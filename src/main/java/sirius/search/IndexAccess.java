/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import sirius.kernel.Sirius;
import sirius.kernel.async.Barrier;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.ExecutionPoint;
import sirius.kernel.async.Future;
import sirius.kernel.async.Operation;
import sirius.kernel.async.Promise;
import sirius.kernel.async.Tasks;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Wait;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Average;
import sirius.kernel.health.Counter;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.search.suggestion.Complete;
import sirius.search.suggestion.Suggest;
import sirius.web.resources.Resource;
import sirius.web.resources.Resources;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Central access class to the persistence layer.
 * <p>
 * Provides CRUD access to the underlying ElasticSearch cluster.
 */
@Register(classes = IndexAccess.class)
public class IndexAccess {

    /**
     * Contains the default ID used by new entities
     */
    public static final String NEW = "new";

    /**
     * Contains the name of the ID field
     */
    public static final String ID_FIELD = "_id";

    /**
     * Async executor category for integrity check tasks
     */
    public static final String ASYNC_CATEGORY_INDEX_INTEGRITY = "index-ref-integrity";
    private static final String CONFIG_KEY_INDEX_TYPE = "index.type";
    private static final String ASYNC_UPDATER = "async-updater";

    /**
     * Contains the database schema as expected by the java model
     */
    protected Schema schema;

    /**
     * Logger used by this framework
     */
    public static final Log LOG = Log.get("index");

    /**
     * Contains the ElasticSearch client
     */
    protected Client client;

    /**
     * Determines if the framework is already completely initialized
     */
    protected volatile boolean ready;

    /**
     * Contains a future which can be waited for
     */
    private Future readyFuture = new Future();

    /**
     * Internal timer which is used to delay some actions. This is necessary, as ES takes up to one second to make a
     * write visible to the next read
     */
    protected Timer delayLineTimer;

    /**
     * Can be used to cache frequently used entities.
     */
    private Cache<String, Object> globalCache = CacheManager.createLocalCache("entity-cache");

    /**
     * Used when optimistic lock tracing is enabled to record all changes
     */
    protected Map<String, IndexTrace> traces = Maps.newConcurrentMap();

    /**
     * Determines if optimistic lock errors should be traced
     */
    @ConfigValue("index.traceOptimisticLockErrors")
    protected boolean traceOptimisticLockErrors;

    /*
     * Average query duration for statistical measures
     */
    protected Average queryDuration = new Average();
    /*
     * Counts how many threads used blockThreadForUpdate
     */
    protected Counter blocks = new Counter();
    /*
     * Counts how many delays where used
     */
    protected Counter delays = new Counter();
    /*
     * Counts how many optimistic lock errors occurred
     */
    protected Counter optimisticLockErrors = new Counter();

    /**
     * Can be used as routing value for one of the fetch methods to signal that no routing value is available
     * and a lookup by select is deliberately called.
     */
    public static final String FETCH_DELIBERATELY_UNROUTED = "_DELIBERATELY_UNROUTED";

    @Part
    protected Tasks tasks;

    @Part
    private Resources resources;

    @ConfigValue("index.host")
    private String hostAddress;

    @ConfigValue("index.cluster")
    private String clusterName;

    @ConfigValue("index.port")
    private int port;

    @ConfigValue("index.updateSchema")
    private boolean updateSchema;

    /**
     * Queue of actions which need to be delayed one second
     */
    protected static final List<WaitingBlock> oneSecondDelayLine = Lists.newArrayList();

    /**
     * Returns the underlying ElasticSearch client
     *
     * @return the client used by this class
     */
    public Client getClient() {
        return client;
    }

    /**
     * Loads a test dataset from the classpath. The given file should contains an array of JSON objects.
     * <p>
     * Each object must contain a property named <tt>_type</tt> which determines the target entity as well as
     * <tt>_id</tt> which determiens its ID.
     *
     * @param dataset the resource to load into the in memory elasticsearch.
     */
    @SuppressWarnings("unchecked")
    public void loadDataset(String dataset) {
        try {
            LOG.INFO("Loading dataset: %s", dataset);
            Resource res = resources.resolve(dataset)
                                    .orElseThrow(() -> new IllegalArgumentException("Unknown dataset: " + dataset));
            String contents = CharStreams.toString(new InputStreamReader(res.getUrl().openStream(), Charsets.UTF_8));
            JSONArray json = JSON.parseArray(contents);
            for (JSONObject obj : (List<JSONObject>) (Object) json) {
                loadObject(obj);
            }
            blockThreadForUpdate();
        } catch (IOException e) {
            throw Exceptions.handle(e);
        }
    }

    private void loadObject(JSONObject obj) {
        try {
            String type = obj.getString("_type");
            Class<? extends Entity> entityClass = getType(type);
            if (entityClass == null) {
                throw new IllegalArgumentException("No Entity found with type \"" + type + "\"");
            }
            EntityDescriptor descriptor = getDescriptor(entityClass);
            Entity entity = entityClass.newInstance();
            entity.setId(obj.getString("_id"));
            descriptor.readSource(entity, obj);
            create(entity);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot load: " + obj, e);
        }
    }

    /**
     * Provides access to the expected schema / mappings.
     *
     * @return the expected schema of the object model
     */
    public Schema getSchema() {
        return schema;
    }

    /**
     * Checks if the given index exists.
     *
     * @param name the name of the index. The index prefix of the current system will be added automatically
     * @return <tt>true</tt> if the given index exists, <tt>false</tt> otherwise
     */
    public boolean existsIndex(String name) {
        String index = schema.getIndexName(name);
        try {
            IndicesExistsResponse res =
                    getClient().admin().indices().prepareExists(index).execute().get(10, TimeUnit.SECONDS);
            return res.isExists();
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to check existence of index: %s - %s (%s)", index)
                            .handle();
        }
    }

    /**
     * Ensures that the given manually created index exists.
     *
     * @param name the name of the index. The index prefix of the current system will be added automatically
     */
    public void ensureIndexExists(String name) {
        try {
            IndicesExistsResponse res = getClient().admin()
                                                   .indices()
                                                   .prepareExists(schema.getIndexName(name))
                                                   .execute()
                                                   .get(10, TimeUnit.SECONDS);
            if (!res.isExists()) {
                CreateIndexResponse createResponse = getClient().admin()
                                                                .indices()
                                                                .prepareCreate(schema.getIndexName(name))
                                                                .execute()
                                                                .get(10, TimeUnit.SECONDS);
                if (!createResponse.isAcknowledged()) {
                    throw Exceptions.handle()
                                    .to(LOG)
                                    .withSystemErrorMessage("Cannot create index: %s", schema.getIndexName(name))
                                    .handle();
                } else {
                    blockThreadForUpdate();
                }
            }
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(e)
                            .withSystemErrorMessage("Cannot create index: %s - %s (%s)", schema.getIndexName(name))
                            .handle();
        }
    }

    /**
     * Completely wipes the given index.
     *
     * @param name the name of the index. The index prefix of the current system will be added automatically
     */
    public void deleteIndex(String name) {
        try {
            DeleteIndexResponse res = getClient().admin()
                                                 .indices()
                                                 .prepareDelete(schema.getIndexName(name))
                                                 .execute()
                                                 .get(10, TimeUnit.SECONDS);
            if (!res.isAcknowledged()) {
                throw Exceptions.handle()
                                .to(LOG)
                                .withSystemErrorMessage("Cannot delete index: %s", schema.getIndexName(name))
                                .handle();
            }
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(e)
                            .withSystemErrorMessage("Cannot delete index: %s - %s (%s)", schema.getIndexName(name))
                            .handle();
        }
    }

    /**
     * Writes the given mapping to the given index.
     *
     * @param index the full name of the index.
     *              Note: The index prefix of the current system will NOT be added automatically
     * @param type  the entity class which mapping should be written to the given index.
     * @param <E>   the generic of <tt>type</tt>
     */
    public <E extends Entity> void addMapping(String index, Class<E> type) {
        try {
            EntityDescriptor desc = schema.getDescriptor(type);
            getClient().admin()
                       .indices()
                       .preparePutMapping(index)
                       .setType(desc.getType())
                       .setSource(desc.createMapping())
                       .execute()
                       .get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            Throwable rootCause = ex;
            while (rootCause.getCause() != null && !rootCause.getCause().equals(rootCause)) {
                rootCause = rootCause.getCause();
            }
            throw Exceptions.handle()
                            .to(LOG)
                            .error(rootCause)
                            .withSystemErrorMessage("Cannot create mapping %s in index: %s - %s (%s)",
                                                    type.getSimpleName(),
                                                    index)
                            .handle();
        }
    }

    /**
     * Returns the descriptor for the given class.
     *
     * @param clazz the class which descriptor is request
     * @return the descriptor for the given class
     */
    public EntityDescriptor getDescriptor(Class<? extends Entity> clazz) {
        ensureReady();
        return schema.getDescriptor(clazz);
    }

    private void ensureReady() {
        if (!ready) {
            throw Exceptions.handle().to(LOG).withSystemErrorMessage("Index is not ready yet.").handle();
        }
    }

    /**
     * Returns the class for the given type name.
     *
     * @param name the name of the type which class is requested
     * @return the class which is associated with the given type
     */
    public Class<? extends Entity> getType(String name) {
        ensureReady();
        return schema.getType(name);
    }

    /**
     * Qualifies the given index name with the prefix.
     *
     * @param index the index name to qualify
     * @return the qualified name of the given index name
     */
    public String getIndexName(String index) {
        ensureReady();
        return schema.getIndexName(index);
    }

    /**
     * Returns the name of the index associated with the given class.
     *
     * @param clazz the entity type which index is requested
     * @param <E>   the type of the entity to get the index for
     * @return the index name associated with the given class
     */
    public <E extends Entity> String getIndex(Class<E> clazz) {
        ensureReady();
        return schema.getIndex(clazz);
    }

    protected void startup() {
        try (Operation op = new Operation(() -> "IndexLifecycle.startClient", Duration.ofSeconds(15))) {
            startClient();
        }

        schema = new Schema(this);
        schema.load();

        try (Operation op = new Operation(() -> "IndexLifecycle.updateMappings", Duration.ofSeconds(30))) {
            updateMappings();
        }

        ready = true;
        readyFuture.success();

        delayLineTimer = new Timer("index-delay");
        delayLineTimer.schedule(new DelayLineHandler(), 1000, 1000);
    }

    /**
     * Implementation of the timer which handles delayed actions.
     */
    protected static class DelayLineHandler extends TimerTask {

        @Override
        public void run() {
            try {
                synchronized (oneSecondDelayLine) {
                    Iterator<WaitingBlock> iter = oneSecondDelayLine.iterator();
                    while (iter.hasNext()) {
                        WaitingBlock next = iter.next();
                        if (next.isRunnable()) {
                            next.execute();
                            iter.remove();
                        } else {
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                Exceptions.handle(LOG, e);
            }
        }
    }

    private void updateMappings() {
        if (updateSchema) {
            for (String msg : schema.createMappings()) {
                IndexAccess.LOG.INFO(msg);
            }
        }
    }

    @SuppressWarnings("squid:S2095")
    @Explain("We don't want to immediatelly close the transport client but rather return it to be used.")
    private void startClient() {
        if (Sirius.getSettings().getConfig().hasPath(CONFIG_KEY_INDEX_TYPE)
            && !"server".equalsIgnoreCase(Sirius.getSettings().getConfig().getString(CONFIG_KEY_INDEX_TYPE))) {
            LOG.WARN("Unsupported index.type='%s'. Use 'index.type=server' instead or remove this option.",
                     Sirius.getSettings().getConfig().getString(CONFIG_KEY_INDEX_TYPE));
        }

        LOG.INFO("Connecting to Elasticsearch cluster '%s' via '%s'...", clusterName, hostAddress);
        Settings settings = Settings.builder().put("cluster.name", clusterName).build();
        TransportClient transportClient = new PreBuiltTransportClient(settings);
        try {
            transportClient.addTransportAddress(new TransportAddress(InetAddress.getByName(hostAddress), port));
        } catch (UnknownHostException e) {
            Exceptions.handle(LOG, e);
        }
        client = transportClient;
    }

    /**
     * Fetches the entity of given type with the given id.
     * <p>
     * May use a given cache to load the entity.
     *
     * @param routing the routing info used to lookup the entity (might be <tt>null</tt> if no routing is required).
     * @param type    the type of the desired entity
     * @param id      the id of the desired entity
     * @param cache   the cache to resolve the entity.
     * @param <E>     the type of entities to fetch
     * @return a tuple containing the resolved entity (or <tt>null</tt> if not found) and a flag which indicates if the
     * value was loaded from cache (<tt>true</tt>) or not.
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    public <E extends Entity> Tuple<E, Boolean> fetch(@Nullable String routing,
                                                      @Nonnull Class<E> type,
                                                      @Nullable String id,
                                                      @Nonnull com.google.common.cache.Cache<String, Object> cache) {
        if (Strings.isEmpty(id)) {
            return Tuple.create(null, false);
        }
        EntityDescriptor descriptor = getDescriptor(type);

        E value = (E) cache.getIfPresent(descriptor.getType() + "-" + id);
        if (value != null) {
            return Tuple.create(value, true);
        }
        value = fetchFromIndex(routing, type, id, descriptor);
        if (value != null) {
            cache.put(descriptor.getType() + "-" + id, value);
        }
        return Tuple.create(value, false);
    }

    private <E extends Entity> E fetchFromIndex(@Nullable String routing,
                                                @Nonnull Class<E> type,
                                                @Nullable String id,
                                                EntityDescriptor descriptor) {
        E value;
        if (descriptor.hasRouting()) {
            if (Strings.isFilled(routing) && !FETCH_DELIBERATELY_UNROUTED.equals(routing)) {
                value = find(routing, type, id);
            } else {
                if (FETCH_DELIBERATELY_UNROUTED.equals(routing)) {
                    value = select(type).deliberatelyUnrouted().eq(ID_FIELD, id).queryFirst();
                } else {
                    value = select(type).eq(ID_FIELD, id).queryFirst();
                }
            }
        } else {
            value = find(type, id);
        }
        return value;
    }

    /**
     * Fetches the entity of given type with the given id.
     * <p>
     * May use a given cache to load the entity.
     *
     * @param routing the routing info used to lookup the entity (might be <tt>null</tt> if no routing is required).
     * @param type    the type of the desired entity
     * @param id      the id of the desired entity
     * @param cache   the cache to resolve the entity.
     * @param <E>     the type of entities to fetch
     * @return the resolved entity (or <tt>null</tt> if not found)
     */
    @Nullable
    public <E extends Entity> E fetchFromCache(@Nullable String routing,
                                               @Nonnull Class<E> type,
                                               @Nullable String id,
                                               @Nonnull com.google.common.cache.Cache<String, Object> cache) {
        return fetch(routing, type, id, cache).getFirst();
    }

    /**
     * Fetches the entity of given type with the given id.
     * <p>
     * May use a global cache to load the entity.
     *
     * @param routing the routing info used to lookup the entity (might be <tt>null</tt> if no routing is required).
     * @param type    the type of the desired entity
     * @param id      the id of the desired entity
     * @param <E>     the type of entities to fetch
     * @return a tuple containing the resolved entity (or <tt>null</tt> if not found) and a flag which indicates if the
     * value was loaded from cache (<tt>true</tt>) or not.
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    public <E extends Entity> Tuple<E, Boolean> fetch(@Nullable String routing,
                                                      @Nonnull Class<E> type,
                                                      @Nullable String id) {
        if (Strings.isEmpty(id)) {
            return Tuple.create(null, false);
        }
        EntityDescriptor descriptor = getDescriptor(type);

        E value = (E) globalCache.get(descriptor.getType() + "-" + id);
        if (value != null) {
            return Tuple.create(value, true);
        }
        value = fetchFromIndex(routing, type, id, descriptor);

        globalCache.put(descriptor.getType() + "-" + id, value);
        return Tuple.create(value, false);
    }

    /**
     * Fetches the entity of given type with the given id.
     * <p>
     * May use a global cache to load the entity.
     *
     * @param routing the routing info used to lookup the entity (might be <tt>null</tt> if no routing is required).
     * @param type    the type of the desired entity
     * @param id      the id of the desired entity
     * @param <E>     the type of entities to fetch
     * @return the resolved entity (or <tt>null</tt> if not found)
     */
    @Nullable
    public <E extends Entity> E fetchFromCache(@Nullable String routing, @Nonnull Class<E> type, @Nullable String id) {
        return fetch(routing, type, id).getFirst();
    }

    /**
     * Fetches the entity wrapped in an {@link java.util.Optional} of given type with the given id.
     * <p>
     * May use a given cache to load the entity.
     *
     * @param routing the routing info used to lookup the entity (might be <tt>null</tt> if no routing is required).
     * @param type    the type of the desired entity
     * @param id      the id of the desired entity
     * @param cache   the cache to resolve the entity.
     * @param <E>     the type of entities to fetch
     * @return the resolved entity wrapped in an {@link java.util.Optional} (or {@link Optional#EMPTY} if not found)
     */
    public <E extends Entity> Optional<E> fetchOptionalFromCache(@Nullable String routing,
                                                                 @Nonnull Class<E> type,
                                                                 @Nullable String id,
                                                                 @Nonnull com.google.common.cache.Cache<String, Object> cache) {
        return Optional.ofNullable(fetch(routing, type, id, cache).getFirst());
    }

    /**
     * Fetches the entity wrapped in an {@link java.util.Optional} of given type with the given id.
     * <p>
     * May use a global cache to load the entity.
     *
     * @param routing the routing info used to lookup the entity (might be <tt>null</tt> if no routing is required).
     * @param type    the type of the desired entity
     * @param id      the id of the desired entity
     * @param <E>     the type of entities to fetch
     * @return the resolved entity wrapped in an {@link java.util.Optional} (or {@link Optional#EMPTY} if not found)
     */
    public <E extends Entity> Optional<E> fetchOptionalFromCache(@Nullable String routing,
                                                                 @Nonnull Class<E> type,
                                                                 @Nullable String id) {
        return Optional.ofNullable(fetch(routing, type, id).getFirst());
    }

    /**
     * Used to delay an action for at least one second
     */
    private class WaitingBlock {
        private long waitline;
        private Runnable cmd;
        private CallContext context;

        WaitingBlock(Runnable cmd) {
            this.cmd = cmd;
            this.waitline = System.currentTimeMillis() + 1000;
            this.context = CallContext.getCurrent();
        }

        /**
         * Determines if the action was delayed long enough
         *
         * @return <tt>true</tt> if the action was delayed long enough, <tt>false</tt> otherwise
         */
        public boolean isRunnable() {
            return System.currentTimeMillis() > waitline;
        }

        /**
         * Executes the action in its own executor
         */
        public void execute() {
            CallContext.setCurrent(context);
            tasks.executor("index-delay").fork(cmd);
        }
    }

    /**
     * Adds an action to the delay line, which ensures that it is at least delayed for one second
     *
     * @param cmd to command to be delayed
     */
    public void callAfterUpdate(final Runnable cmd) {
        synchronized (oneSecondDelayLine) {
            if (oneSecondDelayLine.size() < 100) {
                delays.inc();
                oneSecondDelayLine.add(new WaitingBlock(cmd));
                return;
            }
        }
        blockThreadForUpdate();
        cmd.run();
    }

    /**
     * Manually blocks the current thread for one second, to make a write visible in ES.
     * <p>
     * Consider using {@link #callAfterUpdate(Runnable)} which does not block system resources. Only use this method
     * if absolutely necessary.
     */
    public void blockThreadForUpdate() {
        blockThreadForUpdate(1);
    }

    /**
     * Manually blocks the current thread for n seconds, to make e.g. a bulk write visible in ES.
     * <p>
     * Consider using {@link #callAfterUpdate(Runnable)} which does not block system resources. Only use this method
     * if absolutely necessary.
     *
     * @param seconds the number of seconds to block
     */
    public void blockThreadForUpdate(int seconds) {
        blocks.inc();
        Wait.seconds(seconds);
    }

    /**
     * Handles the given unit of work while restarting it if an optimistic lock error occurs.
     *
     * @param uow the unit of work to handle.
     * @throws HandledException if either any other exception occurs, or if all three attempts
     *                          fail with an optimistic lock error.
     */
    public void retry(UnitOfWork uow) {
        int retries = 3;
        while (retries > 0) {
            retries--;
            try {
                uow.execute();
                return;
            } catch (OptimisticLockException e) {
                LOG.FINE(e);
                if (Sirius.isDev()) {
                    LOG.INFO("Retrying due to optimistic lock: %s", e);
                }
                if (retries <= 0) {
                    throw Exceptions.handle()
                                    .withSystemErrorMessage(
                                            "Failed to update an entity after re-trying a unit of work several times: %s (%s)")
                                    .error(e)
                                    .to(LOG)
                                    .handle();
                }
                // Wait 0, 500ms, 1000ms
                Wait.millis((2 - retries) * 500);
                // Wait 0..500ms in 50% of all calls...
                Wait.randomMillis(-500, 500);
            } catch (HandledException e) {
                throw e;
            } catch (Exception e) {
                throw Exceptions.handle()
                                .withSystemErrorMessage(
                                        "An unexpected exception occurred while executing a unit of work: %s (%s)")
                                .error(e)
                                .to(LOG)
                                .handle();
            }
        }
    }

    /**
     * Tries to apply the given changes and to save the resulting entity.
     * <p>
     * Tries to perform the given modifications and then to update the entity. If an optimistic lock error occurs,
     * the entity is refreshed and the modifications are re-executed along with another update.
     *
     * @param entity          the entity to update
     * @param preSaveModifier the changes to perform on the entity
     * @param <E>             the type of the entity to update
     * @throws HandledException if either any other exception occurs, or if all three attempts fail with an optimistic
     *                          lock error.
     */
    public <E extends Entity> void retryUpdate(E entity, Callback<E> preSaveModifier) {
        Monoflop mf = Monoflop.create();
        retry(() -> {
            E entityToUpdate = entity;
            if (mf.successiveCall()) {
                entityToUpdate = refreshIfPossible(entity);
            }

            preSaveModifier.invoke(entityToUpdate);
            tryUpdate(entityToUpdate);
        });
    }

    /**
     * Tries to apply the given changes and to save the resulting entity without running the entities save checks and handlers.
     * <p>
     * Tries to perform the given modifications and then to update the entity. If an optimistic lock error occurs,
     * the entity is refreshed and the modifications are re-executed along with another update.
     *
     * @param entity          the entity to update
     * @param preSaveModifier the changes to perform on the entity
     * @param <E>             the type of the entity to update
     * @throws HandledException if either any other exception occurs, or if all three attempts fail with an optimistic
     *                          lock error.
     */
    public <E extends Entity> void retryUpdateUnchecked(E entity, Callback<E> preSaveModifier) {
        Monoflop mf = Monoflop.create();
        retry(() -> {
            E entityToUpdate = entity;
            if (mf.successiveCall()) {
                entityToUpdate = refreshIfPossible(entity);
            }

            preSaveModifier.invoke(entityToUpdate);
            tryUpdateUnchecked(entityToUpdate);
        });
    }

    /**
     * Creates this entity by storing an initial copy in the database.
     *
     * @param entity the entity to be stored in the database
     * @param <E>    the type of the entity to create
     * @return the stored entity (with a filled ID etc.)
     */
    public <E extends Entity> E create(E entity) {
        try {
            return update(entity, false, true, true);
        } catch (OptimisticLockException e) {
            // Should never happen - but who knows...
            throw Exceptions.handle(LOG, e);
        }
    }

    /**
     * Tries to update the given entity in the database.
     * <p>
     * If the same entity was modified in the database already, an
     * <tt>OptimisticLockException</tt> will be thrown
     *
     * @param entity the entity to save
     * @param <E>    the type of the entity to update
     * @return the saved entity
     * @throws OptimisticLockException if the entity was modified in the database and those changes where
     *                                 not reflected
     *                                 by the entity to be saved
     */
    public <E extends Entity> E tryUpdate(E entity) throws OptimisticLockException {
        return update(entity, true, false, true);
    }

    /**
     * Tries to update the given entity in the database without running the entities save checks and handlers.
     * <p>
     * If the same entity was modified in the database already, an
     * <tt>OptimisticLockException</tt> will be thrown
     *
     * @param entity the entity to save
     * @param <E>    the type of the entity to update
     * @return the saved entity
     * @throws OptimisticLockException if the entity was modified in the database and those changes where
     *                                 not reflected
     *                                 by the entity to be saved
     */
    public <E extends Entity> E tryUpdateUnchecked(E entity) throws OptimisticLockException {
        return update(entity, true, false, false);
    }

    /**
     * Updates the entity in the database.
     * <p>
     * If the entity was modified in the database and those changes where not reflected
     * by the entity to be saved, this operation will fail.
     *
     * @param entity the entity to be written into the DB
     * @param <E>    the type of the entity to update
     * @return the updated entity
     */
    public <E extends Entity> E update(E entity) {
        try {
            return update(entity, true, false, true);
        } catch (OptimisticLockException e) {
            reportClash(entity);
            throw Exceptions.handle()
                            .to(LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to update '%s' (%s): %s (%s)",
                                                    entity.toDebugString(),
                                                    entity.getId())
                            .handle();
        }
    }

    /**
     * Updates the entity in the database asynchronous using a dedicated thread pool.
     *
     * @param entity the entity to be written into the DB
     * @param <E>    the type of the entity to update
     * @return a {@link Promise} handling the update process
     */
    public <E extends Entity> Promise<E> updateAsync(E entity) {
        Promise<E> promise = new Promise<>();
        tasks.executor(ASYNC_UPDATER).start(() -> {
            try {
                update(entity);
                promise.success(entity);
            } catch (Exception e) {
                promise.fail(e);
            }
        });

        return promise;
    }

    /**
     * Updates the entities in the database.
     * <p>
     * If one of the entities was modified in the database and those changes where not reflected
     * by the entity to be saved, this operation will fail (the version of the according entity will be set to -1L).
     *
     * @param entities the entites to be written into the DB
     * @param <E>      the type of the entities to update
     * @return the updated entities
     */
    public <E extends Entity> List<E> updateBulk(List<E> entities) {
        return updateBulk(entities, true, false);
    }

    protected <E extends Entity> void reportClash(E entity) {
        if (!traceOptimisticLockErrors) {
            return;
        }
        IndexTrace offendingTrace = traces.get(entity.getClass().getName() + "-" + entity.getId());
        if (offendingTrace != null) {
            StringBuilder msg = new StringBuilder("Detected optimistic locking error:\n");
            msg.append("Current Thread: ");
            msg.append(Thread.currentThread().getName());
            msg.append("\n");
            for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                msg.append(e.getClassName())
                   .append(".")
                   .append(e.getMethodName())
                   .append(" (")
                   .append(e.getFileName())
                   .append(":")
                   .append(e.getLineNumber())
                   .append(")\n");
            }
            msg.append("\n");
            for (Tuple<String, String> t : CallContext.getCurrent().getMDC()) {
                msg.append(t.getFirst()).append(": ").append(t.getSecond()).append("\n");
            }
            msg.append("\n");
            msg.append("Offending Thread: ");
            msg.append(offendingTrace.threadName);
            msg.append("\n");
            for (StackTraceElement e : offendingTrace.stackTrace) {
                msg.append(e.getClassName())
                   .append(".")
                   .append(e.getMethodName())
                   .append(" (")
                   .append(e.getFileName())
                   .append(":")
                   .append(e.getLineNumber())
                   .append(")\n");
            }
            msg.append("\n");
            for (Tuple<String, String> t : offendingTrace.mdc) {
                msg.append(t.getFirst()).append(": ").append(t.getSecond()).append("\n");
            }
            msg.append("\n");
            LOG.SEVERE(msg.toString());
        }
    }

    protected <E extends Entity> void traceChange(E entity) {
        if (!traceOptimisticLockErrors) {
            return;
        }
        IndexTrace trace = new IndexTrace();
        trace.threadName = Thread.currentThread().getName();
        trace.id = entity.getId();
        trace.type = entity.getClass().getName();
        trace.timestamp = System.currentTimeMillis();
        trace.mdc = CallContext.getCurrent().getMDC();
        trace.stackTrace = Thread.currentThread().getStackTrace();
        traces.put(trace.type + "-" + trace.id, trace);
    }

    /**
     * Updates the entity in the database.
     * <p>
     * As change tracking is disabled, this operation will override all previous changes which are not reflected
     * by the entity to be saved.
     *
     * @param entity the entity to be written into the DB
     * @param <E>    the type of the entity to override
     * @return the updated entity
     */
    public <E extends Entity> E override(E entity) {
        try {
            return update(entity, false, false, true);
        } catch (OptimisticLockException e) {
            // Should never happen as version checking is disabled....
            throw Exceptions.handle(LOG, e);
        }
    }

    /**
     * Updates the entity in the database without running the entities save checks and handlers.
     * <p>
     * As change tracking is disabled, this operation will override all previous changes which are not reflected
     * by the entity to be saved.
     *
     * @param entity the entity to be written into the DB
     * @param <E>    the type of the entity to override
     * @return the updated entity
     */
    public <E extends Entity> E overrideUnchecked(E entity) {
        try {
            return update(entity, false, false, false);
        } catch (OptimisticLockException e) {
            // Should never happen as version checking is disabled....
            throw Exceptions.handle(LOG, e);
        }
    }

    /**
     * Internal save method used by {@link #create(Entity)}, {@link #tryUpdate(Entity)}, {@link #update(Entity)}
     * and {@link #override(Entity)}
     *
     * @param entity              the entity to save
     * @param performVersionCheck determines if change tracking will be enabled
     * @param forceCreate         determines if a new entity should be created
     * @param runSaveChecks       determines if the entities save checks and handlers should be executed
     * @param <E>                 the type of the entity to update
     * @return the saved entity
     * @throws OptimisticLockException if change tracking is enabled and an intermediary change took place
     */
    protected <E extends Entity> E update(final E entity,
                                          final boolean performVersionCheck,
                                          final boolean forceCreate,
                                          final boolean runSaveChecks) throws OptimisticLockException {
        try {
            final Map<String, Object> source = Maps.newTreeMap();
            if (runSaveChecks) {
                entity.beforeSave();
            }
            EntityDescriptor descriptor = getDescriptor(entity.getClass());
            descriptor.writeTo(entity, source);

            if (LOG.isFINE()) {
                LOG.FINE("SAVE[CREATE: %b, LOCK: %b]: %s.%s: %s",
                         forceCreate,
                         performVersionCheck,
                         schema.getIndex(entity),
                         descriptor.getType(),
                         Strings.join(source));
            }

            String id = entity.getId();
            if (NEW.equals(id)) {
                id = null;
            }
            if (Strings.isEmpty(id)) {
                id = entity.computePossibleId();
            }

            IndexRequestBuilder irb = getClient().prepareIndex(schema.getIndex(entity), descriptor.getType(), id)
                                                 .setCreate(forceCreate)
                                                 .setSource(source);
            if (!entity.isNew() && performVersionCheck) {
                irb.setVersion(entity.getVersion());
            }

            applyRouting("Updating", entity, descriptor, irb::setRouting);

            return executeUpdate(entity, descriptor, irb, runSaveChecks);
        } catch (VersionConflictEngineException e) {
            if (LOG.isFINE()) {
                LOG.FINE("Version conflict on updating: %s", entity);
            }
            optimisticLockErrors.inc();
            throw new OptimisticLockException(e, entity);
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to update '%s' (%s): %s (%s)",
                                                    entity.toDebugString(),
                                                    entity.getId())
                            .handle();
        }
    }

    /**
     * Internal save method used by {@link #updateBulk(List)}.
     *
     * @param entities            the entities to save
     * @param performVersionCheck determines if change tracking will be enabled
     * @param forceCreate         determines if a new entity should be created
     * @param <E>                 the type of the entity to update
     * @return the saved entity
     */
    protected <E extends Entity> List<E> updateBulk(final List<E> entities,
                                                    final boolean performVersionCheck,
                                                    final boolean forceCreate) {
        try {
            BulkRequestBuilder bulkRequest = getClient().prepareBulk();
            EntityDescriptor descriptor;

            for (E entity : entities) {
                final Map<String, Object> source = Maps.newTreeMap();
                entity.beforeSave();
                descriptor = getDescriptor(entity.getClass());
                descriptor.writeTo(entity, source);

                if (LOG.isFINE()) {
                    LOG.FINE("BULK-SAVE[CREATE: %b, LOCK: %b]: %s.%s: %s",
                             forceCreate,
                             performVersionCheck,
                             schema.getIndex(entity),
                             descriptor.getType(),
                             Strings.join(source));
                }

                String id = entity.getId();
                if (NEW.equals(id)) {
                    id = null;
                }
                if (Strings.isEmpty(id)) {
                    id = entity.computePossibleId();
                }

                IndexRequestBuilder irb = getClient().prepareIndex(schema.getIndex(entity), descriptor.getType(), id)
                                                     .setCreate(forceCreate)
                                                     .setSource(source);
                if (!entity.isNew() && performVersionCheck) {
                    irb.setVersion(entity.getVersion());
                }

                applyRouting("Updating", entity, descriptor, irb::setRouting);
                bulkRequest.add(irb);
            }

            return executeBulkUpdate(entities, bulkRequest);
        } catch (Exception e) {
            throw Exceptions.handle().to(LOG).error(e).withSystemErrorMessage("Failed bulk-update").handle();
        }
    }

    private <E extends Entity> void applyRouting(String action,
                                                 E entity,
                                                 EntityDescriptor descriptor,
                                                 Consumer<String> routingTarget) {
        if (descriptor.hasRouting()) {
            Object routingKey = descriptor.getProperty(descriptor.getRouting()).writeToSource(entity);
            if (Strings.isEmpty(routingKey)) {
                LOG.WARN("%s an entity of type %s (%s) without routing information! Location: %s",
                         action,
                         entity.getClass().getName(),
                         entity.getId(),
                         ExecutionPoint.snapshot());
            } else {
                routingTarget.accept(String.valueOf(routingKey));
            }
        }
    }

    private <E extends Entity> E executeUpdate(E entity,
                                               EntityDescriptor descriptor,
                                               IndexRequestBuilder irb,
                                               final boolean runSaveChecks) {
        Watch w = Watch.start();
        IndexResponse indexResponse = irb.execute().actionGet();
        if (LOG.isFINE()) {
            LOG.FINE("SAVE: %s.%s: %s (%d) SUCCEEDED",
                     schema.getIndex(entity),
                     descriptor.getType(),
                     indexResponse.getId(),
                     indexResponse.getVersion());
        }
        entity.id = indexResponse.getId();
        entity.version = indexResponse.getVersion();
        if (runSaveChecks) {
            entity.afterSave();
        }
        queryDuration.addValue(w.elapsedMillis());
        w.submitMicroTiming("ES", "UPDATE " + entity.getClass().getName());
        traceChange(entity);
        return entity;
    }

    private <E extends Entity> List<E> executeBulkUpdate(List<E> entities, BulkRequestBuilder brb) {
        Watch w = Watch.start();
        BulkResponse indexResponse = brb.execute().actionGet();

        if (!indexResponse.hasFailures() && LOG.isFINE()) {
            LOG.FINE("BULK-SAVE SUCCEEDED");
        } else if (indexResponse.hasFailures()) {
            Exceptions.handle().withSystemErrorMessage(indexResponse.buildFailureMessage()).handle();
        }

        for (int i = 0; i < indexResponse.getItems().length; i++) {
            E entity = entities.get(i);
            entity.id = indexResponse.getItems()[i].getId();
            entity.version = indexResponse.getItems()[i].getVersion();

            if (!indexResponse.getItems()[i].isFailed()) {
                entity.afterSave();
            }

            traceChange(entity);
        }

        queryDuration.addValue(w.elapsedMillis());
        w.submitMicroTiming("ES", "BULK-UPDATE");

        return entities;
    }

    /**
     * Tries to find the entity of the given type with the given id.
     *
     * @param clazz the type of the entity
     * @param id    the id of the entity
     * @param <E>   the type of the entity to find
     * @return the entity of the given class with the given id or <tt>null</tt> if no such entity exists
     */
    public <E extends Entity> E find(final Class<E> clazz, String id) {
        return find(null, null, clazz, id);
    }

    /**
     * Tries to find the entity of the given type with the given id and routing.
     *
     * @param routing the value used to compute the routing hash
     * @param clazz   the type of the entity
     * @param id      the id of the entity
     * @param <E>     the type of the entity to find
     * @return the entity of the given class with the given id or <tt>null</tt> if no such entity exists
     */
    public <E extends Entity> E find(String routing, final Class<E> clazz, String id) {
        return find(null, routing, clazz, id);
    }

    /**
     * Tries to find the entity of the given type with the given id and routing.
     *
     * @param routing the value used to compute the routing hash
     * @param clazz   the type of the entity
     * @param id      the id of the entity
     * @param <E>     the type of the entity to find
     * @return the entity wrapped in an {@link Optional} of the given class with the given id or
     * {@link Optional#EMPTY} if no such entity exists
     */
    public <E extends Entity> Optional<E> findOptional(String routing, final Class<E> clazz, String id) {
        return Optional.ofNullable(find(null, routing, clazz, id));
    }

    /**
     * Tries to find the entity of the given type with the given id.
     *
     * @param index   the index to use. The current index prefix will be automatically added. Can be left <tt>null</tt>
     * @param routing the value used to compute the routing hash
     *                to use the default index
     * @param clazz   the type of the entity
     * @param id      the id of the entity
     * @param <E>     the type of the entity to find
     * @return the entity of the given class with the given id or <tt>null</tt> if no such entity exists
     */
    public <E extends Entity> E find(@Nullable String index,
                                     @Nullable String routing,
                                     @Nonnull final Class<E> clazz,
                                     String id) {
        try {
            if (Strings.isEmpty(id)) {
                return null;
            }
            if (NEW.equals(id)) {
                E e = clazz.newInstance();
                e.setId(NEW);
                return e;
            }

            String indexName = index;
            if (indexName == null) {
                indexName = schema.getIndex(clazz);
            } else {
                indexName = schema.getIndexName(index);
            }
            EntityDescriptor descriptor = getDescriptor(clazz);
            if (LOG.isFINE()) {
                LOG.FINE("FIND: %s.%s: %s", indexName, descriptor.getType(), id);
            }
            Watch w = Watch.start();
            try {
                verifyRoutingForFind(routing, clazz, id, descriptor);
                return executeFind(indexName, routing, clazz, id, descriptor);
            } finally {
                queryDuration.addValue(w.elapsedMillis());
                w.submitMicroTiming("ES", "UPDATE " + clazz.getName());
            }
        } catch (Exception t) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(t)
                            .withSystemErrorMessage("Failed to find '%s' (%s): %s (%s)", id, clazz.getName())
                            .handle();
        }
    }

    private <E extends Entity> E executeFind(@Nullable String index,
                                             @Nullable String routing,
                                             @Nonnull Class<E> clazz,
                                             String id,
                                             EntityDescriptor descriptor) throws Exception {
        GetResponse res = getClient().prepareGet(index, descriptor.getType(), id)
                                     .setPreference("_primary")
                                     .setRouting(routing)
                                     .execute()
                                     .actionGet();
        if (!res.isExists()) {
            if (LOG.isFINE()) {
                LOG.FINE("FIND: %s.%s: NOT FOUND", index, descriptor.getType());
            }
            return null;
        } else {
            E entity = clazz.newInstance();
            entity.initSourceTracing();
            entity.setId(res.getId());
            entity.setVersion(res.getVersion());
            descriptor.readSource(entity, res.getSource());
            if (LOG.isFINE()) {
                LOG.FINE("FIND: %s.%s: FOUND: %s", index, descriptor.getType(), Strings.join(res.getSource()));
            }
            return entity;
        }
    }

    private <E extends Entity> void verifyRoutingForFind(@Nullable String routing,
                                                         @Nonnull Class<E> clazz,
                                                         String id,
                                                         EntityDescriptor descriptor) {
        if (descriptor.hasRouting() && routing == null) {
            Exceptions.handle()
                      .to(LOG)
                      .withSystemErrorMessage(
                              "Trying to FIND an entity of type %s (with id %s) without providing a routing! "
                              + "This will most probably FAIL!",
                              clazz.getName(),
                              id)
                      .handle();
        } else if (!descriptor.hasRouting() && routing != null) {
            Exceptions.handle()
                      .to(LOG)
                      .withSystemErrorMessage("Trying to FIND an entity of type %s (with id %s) with a routing "
                                              + "- but entity has no routing attribute (in @Indexed)! "
                                              + "This will most probably FAIL!", clazz.getName(), id)
                      .handle();
        }
    }

    /**
     * Tries to load a "fresh" (updated) instance of the given entity from the cluster.
     * <p>
     * Will return <tt>null</tt> if <tt>null</tt> was passed in. If a non persisted entity was given. This
     * entity will be returned. If the entity is no longer available in the cluster,
     * <tt>null</tt> will be returned.
     *
     * @param entity the entity to refresh
     * @param <T>    the type of the entity to refresh
     * @return a refreshed instance of the entity or <tt>null</tt> if either the given entity was <tt>null</tt> or if
     * the given entity cannot be found in the cluster.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends Entity> T refreshOrNull(@Nullable T entity) {
        if (entity == null) {
            return null;
        }
        if (entity.isNew()) {
            return entity;
        }
        Class<T> type = (Class<T>) entity.getClass();
        EntityDescriptor descriptor = getDescriptor(type);
        if (descriptor.hasRouting()) {
            Object routing = descriptor.getProperty(descriptor.getRouting()).writeToSource(entity);
            return find(routing == null ? null : routing.toString(), type, entity.getId());
        } else {
            return find(type, entity.getId());
        }
    }

    /**
     * Boilerplate method for {@link #refreshOrNull(sirius.search.Entity)}.
     * <p>
     * Returns <tt>null</tt> if the given entity was <tt>null</tt>. Otherweise either a refreshed instance will
     * be returned or an exception will be thrown.
     *
     * @param entity the entity to refresh
     * @param <T>    the type of the entity to refresh
     * @return a fresh instance of the given entity or <tt>null</tt> if <tt>null</tt> was passed in
     * @throws sirius.kernel.health.HandledException if the entity is no longer available in the cluster
     */
    @Nullable
    public <T extends Entity> T refreshOrFail(@Nullable T entity) {
        T freshEntity = refreshOrNull(entity);
        if (entity != null && freshEntity == null) {
            throw Exceptions.handle()
                            .to(LOG)
                            .withSystemErrorMessage("Failed to refresh the entity '%s' of type %s with id '%s'",
                                                    entity,
                                                    entity.getClass().getSimpleName(),
                                                    entity.getId())
                            .handle();
        } else {
            return freshEntity;
        }
    }

    /**
     * Boilerplate method for {@link #refreshOrNull(sirius.search.Entity)}.
     * <p>
     * Returns <tt>null</tt> if the given entity was <tt>null</tt>. Otherwise either a refreshed instance will
     * be returned or the original given instance.
     *
     * @param entity the entity to refresh
     * @param <T>    the type of the entity to refresh
     * @return a fresh instance of the given entity or <tt>null</tt> if <tt>null</tt> was passed in
     */
    @Nullable
    public <T extends Entity> T refreshIfPossible(@Nullable T entity) {
        T freshEntity = refreshOrNull(entity);
        if (freshEntity == null) {
            return entity;
        } else {
            return freshEntity;
        }
    }

    /**
     * Tries to delete the given entity unless it was modified since the last read.
     *
     * @param entity the entity to delete
     * @param <E>    the type of the entity to delete
     * @throws OptimisticLockException if the entity was modified since the last read
     */
    public <E extends Entity> void tryDelete(E entity) throws OptimisticLockException {
        delete(entity, false);
    }

    /**
     * Deletes the given entity
     * <p>
     * If the entity was modified since the last read, this operation will fail.
     *
     * @param entity the entity to delete
     * @param <E>    the type of the entity to delete
     */
    public <E extends Entity> void delete(E entity) {
        try {
            delete(entity, false);
        } catch (OptimisticLockException e) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to delete '%s' (%s): %s (%s)",
                                                    entity.toDebugString(),
                                                    entity.getId())
                            .handle();
        }
    }

    /**
     * Deletes the given entity without any change tracking. Therefore the entity will also be deleted, if it was
     * modified since the last read.
     *
     * @param entity the entity to delete
     * @param <E>    the type of the entity to delete
     */
    public <E extends Entity> void forceDelete(E entity) {
        try {
            delete(entity, true);
        } catch (OptimisticLockException e) {
            // Should never happen as version checking is disabled....
            throw Exceptions.handle(LOG, e);
        }
    }

    /**
     * Handles all kinds of deletes
     *
     * @param entity the entity to delete
     * @param force  determines whether optimistic locking is suppressed (<tt>true</tt>) or not
     * @param <E>    the type of the entity to delete
     * @throws OptimisticLockException if the entity was changed since the last read
     */
    protected <E extends Entity> void delete(final E entity, final boolean force) throws OptimisticLockException {
        try {
            if (entity.isNew()) {
                return;
            }
            EntityDescriptor descriptor = getDescriptor(entity.getClass());
            if (LOG.isFINE()) {
                LOG.FINE("DELETE[FORCE: %b]: %s.%s: %s",
                         force,
                         schema.getIndex(entity.getClass()),
                         descriptor.getType(),
                         entity.getId());
            }
            entity.beforeDelete();
            Watch w = Watch.start();
            DeleteRequestBuilder drb =
                    getClient().prepareDelete(schema.getIndex(entity), descriptor.getType(), entity.getId());
            if (!force) {
                drb.setVersion(entity.getVersion());
            }

            applyRouting("Deleting", entity, descriptor, drb::setRouting);

            drb.execute().actionGet();
            entity.deleted = true;
            queryDuration.addValue(w.elapsedMillis());
            w.submitMicroTiming("ES", "DELETE " + entity.getClass().getName());
            entity.afterDelete();
            if (LOG.isFINE()) {
                LOG.FINE("DELETE: %s.%s: %s SUCCESS",
                         schema.getIndex(entity.getClass()),
                         descriptor.getType(),
                         entity.getId());
            }
            traceChange(entity);
        } catch (VersionConflictEngineException e) {
            if (LOG.isFINE()) {
                LOG.FINE("Version conflict on updating: %s", entity);
            }
            reportClash(entity);
            optimisticLockErrors.inc();
            throw new OptimisticLockException(e, entity);
        } catch (Exception e) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to delete '%s' (%s): %s (%s)",
                                                    entity.toDebugString(),
                                                    entity.getId())
                            .handle();
        }
    }

    /**
     * Creates a new query for objects of the given class.
     *
     * @param clazz the class of objects to query
     * @param <E>   the type of the entity to query
     * @return a new query against the database
     */
    public <E extends Entity> Query<E> select(Class<E> clazz) {
        return new Query<>(clazz);
    }

    /**
     * Creates a new suggester for objects of the given class.
     *
     * @param clazz the class of objects to suggest for/from
     * @param <E>   the type of the entity
     * @return a new suggester against the database
     */
    public <E extends Entity> Suggest<E> suggest(Class<E> clazz) {
        return new Suggest<>(this, clazz);
    }

    /**
     * Creates a new completer for objects of the given class.
     *
     * @param clazz the class of objects to generate completions
     * @param <E>   the type of the entity
     * @return a new completer against the database
     */
    public <E extends Entity> Complete<E> complete(Class<E> clazz) {
        return new Complete<>(this, clazz);
    }

    /**
     * Determines if the framework is completely initialized.
     *
     * @return <tt>true</tt> if the framework is completely initialized, <tt>false</tt> otherwise
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Returns the readiness state of the index as {@link Future}.
     *
     * @return a future which is fullfilled once the index is ready
     */
    public Future ready() {
        return readyFuture;
    }

    /**
     * Blocks the calling thread until the index is ready.
     */
    public void waitForReady() {
        if (!isReady()) {
            try {
                Barrier b = Barrier.create();
                b.add(readyFuture);
                b.await();
            } catch (InterruptedException e) {
                Exceptions.ignore(e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
