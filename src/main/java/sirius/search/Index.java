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
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import sirius.kernel.Lifecycle;
import sirius.kernel.Sirius;
import sirius.kernel.async.Async;
import sirius.kernel.async.Barrier;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.Future;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Wait;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.*;
import sirius.kernel.health.metrics.MetricProvider;
import sirius.kernel.health.metrics.MetricState;
import sirius.kernel.health.metrics.MetricsCollector;
import sirius.kernel.timer.EveryTenSeconds;
import sirius.web.templates.Content;
import sirius.web.templates.Resource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Central access class to the persistence layer.
 * <p>
 * Provides CRUD access to the underlying ElasticSearch cluster.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
public class Index {

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

    /**
     * Contains the database schema as expected by the java model
     */
    protected static Schema schema;

    /**
     * Logger used by this framework
     */
    public static Log LOG = Log.get("index");

    /**
     * Contains the ElasticSearch client
     */
    private static Client client;
    /**
     * Contains the ES-Node if started using {@link #generateEmptyInMemoryInstance()}
     */
    private static Node inMemoryNode;

    /**
     * To support multiple installations in parallel, an indexPrefix can be supplied, which is added to each index
     */
    private static String indexPrefix;

    /**
     * Determines if the framework is already completely initialized
     */
    private static volatile boolean ready;

    /**
     * Contains a future which can be waited for
     */
    private static Future readyFuture = new Future();

    /**
     * Internal timer which is used to delay some actions. This is necessary, as ES takes up to one second to make a
     * write visible to the next read
     */
    private static Timer delayLineTimer;

    /**
     * Can be used to cache frequently used entities.
     */
    private static Cache<String, Object> globalCache = CacheManager.createCache("entity-cache");

    /**
     * Used when optimistic lock tracing is enabled to record all changes
     */
    private static Map<String, IndexTrace> traces = Maps.newConcurrentMap();

    /**
     * Determines if optimistic lock errors should be traced
     */
    @ConfigValue("index.traceOptimisticLockErrors")
    private static boolean traceOptimisticLockErrors;

    /*
     * Average query duration for statistical measures
     */
    private static Average queryDuration = new Average();
    /*
     * Counts how many threads used blockThreadForUpdate
     */
    private static Counter blocks = new Counter();
    /*
     * Counts how many delays where used
     */
    private static Counter delays = new Counter();
    /*
     * Counts how many optimistic lock errors occurred
     */
    private static Counter optimisticLockErrors = new Counter();

    /**
     * Can be used as routing value for one of the fetch methods to signal that no routing value is available
     * and a lookup by select is deliberately called.
     */
    public static final String FETCH_DELIBERATELY_UNROUTED = "_DELIBERATELY_UNROUTED";

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
    @Nonnull
    public static <E extends Entity> Tuple<E, Boolean> fetch(@Nullable String routing,
                                                             @Nonnull Class<E> type,
                                                             @Nullable String id,
                                                             @Nonnull com.google.common.cache.Cache<String, Object> cache) {
        if (Strings.isEmpty(id)) {
            return Tuple.create(null, false);
        }
        EntityDescriptor descriptor = Index.getDescriptor(type);

        @SuppressWarnings("unchecked") E value = (E) cache.getIfPresent(descriptor.getType() + "-" + id);
        if (value != null) {
            return Tuple.create(value, true);
        }
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
        if (value != null) {
            cache.put(descriptor.getType() + "-" + id, value);
        }
        return Tuple.create(value, false);
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
    public static <E extends Entity> E fetchFromCache(@Nullable String routing,
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
    @Nonnull
    public static <E extends Entity> Tuple<E, Boolean> fetch(@Nullable String routing,
                                                             @Nonnull Class<E> type,
                                                             @Nullable String id) {
        if (Strings.isEmpty(id)) {
            return Tuple.create(null, false);
        }
        EntityDescriptor descriptor = Index.getDescriptor(type);

        @SuppressWarnings("unchecked") E value = (E) globalCache.get(descriptor.getType() + "-" + id);
        if (value != null) {
            return Tuple.create(value, true);
        }
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
    public static <E extends Entity> E fetchFromCache(@Nullable String routing,
                                                      @Nonnull Class<E> type,
                                                      @Nullable String id) {
        return fetch(routing, type, id).getFirst();
    }

    /**
     * Provides access to the expected schema / mappings.
     *
     * @return the expected schema of the object model
     */
    public static Schema getSchema() {
        return schema;
    }

    /**
     * Used to delay an action for at least one second
     */
    private static class WaitingBlock {
        private long waitline;
        private Runnable cmd;
        private CallContext context;

        public WaitingBlock(Runnable cmd) {
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
            Async.executor("index-delay").fork(cmd).execute();
        }
    }

    /**
     * Queue of actions which need to be delayed one second
     */
    private static List<WaitingBlock> oneSecondDelayLine = Lists.newArrayList();

    /**
     * Adds an action to the delay line, which ensures that it is at least delayed for one second
     *
     * @param cmd to command to be delayed
     */
    public static void callAfterUpdate(final Runnable cmd) {
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

    @Register
    public static class IndexReport implements MetricProvider {

        @Override
        public void gather(MetricsCollector collector) {
            if (!isReady()) {
                return;
            }
            ClusterHealthResponse res = getClient().admin().cluster().prepareHealth().execute().actionGet();
            collector.metric("ES-Nodes", res.getNumberOfNodes(), null, asMetricState(res.getStatus()));
            collector.metric("ES-InitializingShards",
                             res.getInitializingShards(),
                             null,
                             res.getInitializingShards() > 0 ? MetricState.YELLOW : MetricState.GRAY);
            collector.metric("ES-RelocatingShards",
                             res.getRelocatingShards(),
                             null,
                             res.getRelocatingShards() > 0 ? MetricState.YELLOW : MetricState.GRAY);
            collector.metric("ES-UnassignedShards",
                             res.getUnassignedShards(),
                             null,
                             res.getUnassignedShards() > 0 ? MetricState.RED : MetricState.GRAY);
            collector.metric("index-delay-line", "ES-DelayLine", oneSecondDelayLine.size(), null);
            collector.differentialMetric("index-blocks", "index-blocks", "ES-DelayBlocks", blocks.getCount(), "/min");
            collector.differentialMetric("index-delays", "index-delays", "ES-Delays", delays.getCount(), "/min");
            collector.differentialMetric("index-locking-errors",
                                         "index-locking-errors",
                                         "ES-OptimisticLock-Errors",
                                         optimisticLockErrors.getCount(),
                                         "/min");
            collector.metric("index-queryDuration", "ES-QueryDuration", queryDuration.getAndClearAverage(), "ms");
            collector.differentialMetric("index-queries",
                                         "index-queries",
                                         "ES-Queries",
                                         queryDuration.getCount(),
                                         "/min");
        }

        private MetricState asMetricState(ClusterHealthStatus status) {
            if (status == ClusterHealthStatus.GREEN) {
                return MetricState.GRAY;
            } else if (status == ClusterHealthStatus.YELLOW) {
                return MetricState.YELLOW;
            } else {
                return MetricState.RED;
            }
        }
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
            } catch (Throwable e) {
                Exceptions.handle(LOG, e);
            }
        }
    }

    /**
     * Manually blocks the current thread for one second, to make a write visible in ES.
     * <p>
     * Consider using {@link #callAfterUpdate(Runnable)} which does not block system resources. Only use this method
     * is absolutely necessary.
     */
    public static void blockThreadForUpdate() {
        blocks.inc();
        Wait.seconds(1);
    }

    /**
     * Determines if the framework is completely initialized.
     *
     * @return <tt>true</tt> if the framework is completely initialized, <tt>false</tt> otherwise
     */
    public static boolean isReady() {
        return ready;
    }

    /**
     * Returns the readiness state of the index as {@link Future}.
     *
     * @return a future which is fullfilled once the index is ready
     */
    public static Future ready() {
        return readyFuture;
    }

    /**
     * Blocks the calling thread until the index is ready.
     */
    public static void waitForReady() {
        if (!Index.isReady()) {
            try {
                Barrier b = Barrier.create();
                b.add(readyFuture);
                b.await();
            } catch (InterruptedException e) {
                Exceptions.handle(e);
            }
        }
    }

    /**
     * Removes outdated traces used to discover optimistic lock errors
     */
    @Register
    public static class OptimisticLockTracer implements EveryTenSeconds {

        @Override
        public void runTimer() throws Exception {
            if (traceOptimisticLockErrors) {
                long limit = System.currentTimeMillis() - 10_000;
                Iterator<IndexTrace> iter = traces.values().iterator();
                while (iter.hasNext()) {
                    if (iter.next().timestamp < limit) {
                        iter.remove();
                    }
                }
            }
        }
    }

    @Register(classes = Lifecycle.class)
    public static class IndexLifecycle implements Lifecycle {

        @Override
        public int getPriority() {
            return 75;
        }

        @Override
        public void started() {
            if (Strings.isEmpty(Sirius.getConfig().getString("index.type"))) {
                LOG.INFO("ElasticSearch is disabled! (index.type is not set)");
                return;
            }

            boolean updateSchema = Sirius.getConfig().getBoolean("index.updateSchema");

            if ("embedded".equalsIgnoreCase(Sirius.getConfig().getString("index.type"))) {
                LOG.INFO("Starting Embedded Elasticsearch...");
                client = NodeBuilder.nodeBuilder().data(true).local(true).build().client();
            } else if ("in-memory".equalsIgnoreCase(Sirius.getConfig().getString("index.type"))) {
                LOG.INFO("Starting In-Memory Elasticsearch...");
                generateEmptyInMemoryInstance();
                updateSchema = true;
            } else {
                LOG.INFO("Connecting to Elasticsearch cluster '%s' via '%s'...",
                         Sirius.getConfig().getString("index.cluster"),
                         Sirius.getConfig().getString("index.host"));
                Settings settings = ImmutableSettings.settingsBuilder()
                                                     .put("cluster.name", Sirius.getConfig().getString("index.cluster"))
                                                     .build();
                client = new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress(Sirius.getConfig()
                                                                                                                .getString(
                                                                                                                        "index.host"),
                                                                                                          Sirius.getConfig()
                                                                                                                .getInt("index.port")));
            }

            // Setup index
            indexPrefix = Sirius.getConfig().getString("index.prefix");
            if (!indexPrefix.endsWith("-")) {
                indexPrefix = indexPrefix + "-";
            }

            schema = new Schema();
            schema.load();

            // Send mappings to ES
            if (updateSchema) {
                for (String msg : schema.createMappings()) {
                    LOG.INFO(msg);
                }
            }
            ready = true;
            readyFuture.success();

            delayLineTimer = new Timer("index-delay");
            delayLineTimer.schedule(new DelayLineHandler(), 1000, 1000);
        }

        @Override
        public void stopped() {
            if (delayLineTimer != null) {
                delayLineTimer.cancel();
            }
        }

        @Override
        public void awaitTermination() {
            // We wait until this last call before we cut the connection to the database (elasticsearch) to permit
            // other stopping lifecycles access until the very end...
            ready = false;
            client.close();
            if (inMemoryNode != null) {
                inMemoryNode.close();
            }
        }

        @Override
        public String getName() {
            return "index (ElasticSearch)";
        }
    }

    /**
     * Handles the given unit of work while restarting it if an optimistic lock error occurs.
     *
     * @param uow the unit of work to handle.
     * @throws HandledException if either any other exception occurs, or if all three attempts fail with an optimistic lock error.
     */
    public static void retry(UnitOfWork uow) {
        int retries = 3;
        while (retries > 0) {
            retries--;
            try {
                uow.execute();
                return;
            } catch (OptimisticLockException e) {
                LOG.FINE(e);
                if (Sirius.isDev()) {
                    LOG.WARN("Retrying due to optimistic lock: %s", e);
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
            } catch (Throwable e) {
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
     * Ensures that the given manually created index exists.
     *
     * @param name the name of the index. The index prefix of the current system will be added automatically
     */
    public static void ensureIndexExists(String name) {
        try {
            IndicesExistsResponse res = Index.getClient()
                                             .admin()
                                             .indices()
                                             .prepareExists(getIndexName(name))
                                             .execute()
                                             .get(10, TimeUnit.SECONDS);
            if (!res.isExists()) {
                CreateIndexResponse createResponse = Index.getClient()
                                                          .admin()
                                                          .indices()
                                                          .prepareCreate(getIndexName(name))
                                                          .execute()
                                                          .get(10, TimeUnit.SECONDS);
                if (!createResponse.isAcknowledged()) {
                    throw Exceptions.handle()
                                    .to(LOG)
                                    .withSystemErrorMessage("Cannot create index: %s", getIndexName(name))
                                    .handle();
                } else {
                    Index.blockThreadForUpdate();
                }
            }
        } catch (Throwable e) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(e)
                            .withSystemErrorMessage("Cannot create index: %s - %s (%s)", getIndexName(name))
                            .handle();
        }
    }

    /**
     * Completely wipes the given index.
     *
     * @param name the name of the index. The index prefix of the current system will be added automatically
     */
    public static void deleteIndex(String name) {
        try {
            DeleteIndexResponse res = Index.getClient()
                                           .admin()
                                           .indices()
                                           .prepareDelete(getIndexName(name))
                                           .execute()
                                           .get(10, TimeUnit.SECONDS);
            if (!res.isAcknowledged()) {
                throw Exceptions.handle()
                                .to(LOG)
                                .withSystemErrorMessage("Cannot delete index: %s", getIndexName(name))
                                .handle();
            }
        } catch (Throwable e) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(e)
                            .withSystemErrorMessage("Cannot delete index: %s - %s (%s)", getIndexName(name))
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
     * @param force will drop the mapping forcefully if the mapping already exists but cannot be changed as requested
     */
    public static <E extends Entity> void addMapping(String index, Class<E> type, boolean force) {
        try {
            EntityDescriptor desc = schema.getDescriptor(type);
            PutMappingResponse putRes = null;
            try {
                putRes = Index.getClient()
                              .admin()
                              .indices()
                              .preparePutMapping(index)
                              .setType(desc.getType())
                              .setSource(desc.createMapping())
                              .execute()
                              .get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                // If we force the mapping, swallow this exception...
                if (!force) {
                    throw e;
                }
            }
            if (putRes == null || !putRes.isAcknowledged()) {
                if (force) {
                    Index.getClient()
                         .admin()
                         .indices()
                         .prepareDeleteMapping(index)
                         .setType(desc.getType())
                         .execute()
                         .get(10, TimeUnit.SECONDS);
                    putRes = Index.getClient()
                                  .admin()
                                  .indices()
                                  .preparePutMapping(index)
                                  .setType(desc.getType())
                                  .setSource(desc.createMapping())
                                  .execute()
                                  .get(10, TimeUnit.SECONDS);
                }
                if (!putRes.isAcknowledged()) {
                    throw Exceptions.handle()
                                    .to(LOG)
                                    .withSystemErrorMessage("Cannot create mapping %s in index: %s",
                                                            type.getSimpleName(),
                                                            index)
                                    .handle();
                }
            }
        } catch (Throwable ex) {
            while (ex.getCause() != null && ex.getCause() != ex) {
                ex = ex.getCause();
            }
            throw Exceptions.handle()
                            .to(LOG)
                            .error(ex)
                            .withSystemErrorMessage("Cannot create mapping %s in index: %s - %s (%s)",
                                                    type.getSimpleName(),
                                                    index)
                            .handle();
        }
    }

    /**
     * Creates this entity by storing an initial copy in the database.
     *
     * @param entity the entity to be stored in the database
     * @param <E>    the type of the entity to create
     * @return the stored entity (with a filled ID etc.)
     */
    public static <E extends Entity> E create(E entity) {
        try {
            return update(entity, false, true);
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
     * @throws OptimisticLockException if the entity was modified in the database and those changes where not reflected
     *                                 by the entity to be saved
     */
    public static <E extends Entity> E tryUpdate(E entity) throws OptimisticLockException {
        return update(entity, true, false);
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
    public static <E extends Entity> E update(E entity) {
        try {
            return update(entity, true, false);
        } catch (OptimisticLockException e) {
            reportClash(entity);
            throw Exceptions.handle()
                            .to(LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to update '%s' (%s): %s (%s)",
                                                    entity.toString(),
                                                    entity.getId())
                            .handle();
        }
    }

    /**
     * Updates the entity in the database.
     * <p>
     * As change tracking is disabled, this operation will override all previous changes which are not reflected
     * by the entity to be saved
     *
     * @param entity the entity to be written into the DB
     * @param <E>    the type of the entity to override
     * @return the updated entity
     */
    public static <E extends Entity> E override(E entity) {
        try {
            return update(entity, false, false);
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
     * @param <E>                 the type of the entity to update
     * @return the saved entity
     * @throws OptimisticLockException if change tracking is enabled and an intermediary change took place
     */
    protected static <E extends Entity> E update(final E entity,
                                                 final boolean performVersionCheck,
                                                 final boolean forceCreate) throws OptimisticLockException {
        try {
            final Map<String, Object> source = Maps.newTreeMap();
            entity.beforeSave();
            EntityDescriptor descriptor = getDescriptor(entity.getClass());
            descriptor.writeTo(entity, source);

            if (LOG.isFINE()) {
                LOG.FINE("SAVE[CREATE: %b, LOCK: %b]: %s.%s: %s",
                         forceCreate,
                         performVersionCheck,
                         getIndex(entity),
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

            IndexRequestBuilder irb = getClient().prepareIndex(getIndex(entity), descriptor.getType(), id)
                                                 .setCreate(forceCreate)
                                                 .setSource(source);
            if (!entity.isNew() && performVersionCheck) {
                irb.setVersion(entity.getVersion());
            }
            if (descriptor.hasRouting()) {
                Object routingKey = descriptor.getProperty(descriptor.getRouting()).writeToSource(entity);
                if (Strings.isEmpty(routingKey)) {
                    LOG.WARN("Updating an entity of type %s (%s) without routing information!",
                             entity.getClass().getName(),
                             entity.getId());
                } else {
                    irb.setRouting(String.valueOf(routingKey));
                }
            }

            Watch w = Watch.start();
            IndexResponse indexResponse = irb.execute().actionGet();
            if (LOG.isFINE()) {
                LOG.FINE("SAVE: %s.%s: %s (%d) SUCCEEDED",
                         getIndex(entity),
                         descriptor.getType(),
                         indexResponse.getId(),
                         indexResponse.getVersion());
            }
            entity.id = indexResponse.getId();
            entity.version = indexResponse.getVersion();
            entity.afterSave();
            queryDuration.addValue(w.elapsedMillis());
            w.submitMicroTiming("ES", "UPDATE " + entity.getClass().getName());
            traceChange(entity);
            return entity;
        } catch (VersionConflictEngineException e) {
            if (LOG.isFINE()) {
                LOG.FINE("Version conflict on updating: %s", entity);
            }
            optimisticLockErrors.inc();
            throw new OptimisticLockException(e, entity);
        } catch (Throwable e) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to update '%s' (%s): %s (%s)",
                                                    entity.toString(),
                                                    entity.getId())
                            .handle();
        }
    }

    /**
     * Returns the descriptor for the given class.
     *
     * @param clazz the class which descriptor is request
     * @return the descriptor for the given class
     */
    public static EntityDescriptor getDescriptor(Class<? extends Entity> clazz) {
        if (!ready) {
            throw Exceptions.handle().to(LOG).withSystemErrorMessage("Index is not ready yet.").handle();
        }
        return schema.getDescriptor(clazz);
    }

    /**
     * Returns the class for the given type name.
     *
     * @param name the name of the type which class is requested
     * @return the class which is associated with the given type
     */
    public static Class<? extends Entity> getType(String name) {
        return schema.getType(name);
    }

    /**
     * Tries to find the entity of the given type with the given id.
     *
     * @param clazz the type of the entity
     * @param id    the id of the entity
     * @param <E>   the type of the entity to find
     * @return the entity of the given class with the given id or <tt>null</tt> if no such entity exists
     */
    public static <E extends Entity> E find(final Class<E> clazz, String id) {
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
    public static <E extends Entity> E find(String routing, final Class<E> clazz, String id) {
        return find(null, routing, clazz, id);
    }

    /**
     * Tries to find the entity of the given type with the given id.
     *
     * @param index   the index to use. The current index prefix will be automatically added. Can be left <tt>null</tt>
     *                to use the default index
     * @param routing the value used to compute the routing hash
     * @param clazz   the type of the entity
     * @param id      the id of the entity
     * @param <E>     the type of the entity to find
     * @return the entity of the given class with the given id or <tt>null</tt> if no such entity exists
     */
    public static <E extends Entity> E find(@Nullable String index,
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
            if (index == null) {
                index = getIndex(clazz);
            } else {
                index = getIndexName(index);
            }
            EntityDescriptor descriptor = getDescriptor(clazz);
            if (LOG.isFINE()) {
                LOG.FINE("FIND: %s.%s: %s", index, descriptor.getType(), id);
            }
            Watch w = Watch.start();
            try {
                if (descriptor.hasRouting() && routing == null) {
                    Exceptions.handle()
                              .to(LOG)
                              .withSystemErrorMessage(
                                      "Trying to FIND an entity of type %s (with id %s) without providing a routing! This will most probably FAIL!",
                                      clazz.getName(),
                                      id)
                              .handle();
                } else if (!descriptor.hasRouting() && routing != null) {
                    Exceptions.handle()
                              .to(LOG)
                              .withSystemErrorMessage(
                                      "Trying to FIND an entity of type %s (with id %s) with a routing - but entity has no routing attribute (in @Indexed)! This will most probably FAIL!",
                                      clazz.getName(),
                                      id)
                              .handle();
                }
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
            } finally {
                queryDuration.addValue(w.elapsedMillis());
                w.submitMicroTiming("ES", "UPDATE " + clazz.getName());
            }
        } catch (Throwable t) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(t)
                            .withSystemErrorMessage("Failed to find '%s' (%s): %s (%s)", id, clazz.getName())
                            .handle();
        }
    }

    /**
     * Tries to load a "fresh" (updated) instance of the given entity from the cluster.
     * <p>
     * Will return <tt>null</tt> if <tt>null</tt> was passed in. If a non persisted entity was given. This
     * entity will be returned. If the entity is no longer available in the cluster,
     * <tt>null</tt> will be returned
     *
     * @param entity the entity to refresh
     * @param <T>    the type of the entity to refresh
     * @return a refreshed instance of the entity or <tt>null</tt> if either the given entity was <tt>null</tt> or if
     * the given entity cannot be found in the cluster.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends Entity> T refreshOrNull(@Nullable T entity) {
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
            return Index.find(routing == null ? null : routing.toString(), type, entity.getId());
        } else {
            return Index.find(type, entity.getId());
        }
    }

    /**
     * Boilerplate method for {@link #refreshOrNull(Entity)}.
     * <p>
     * Returns <tt>null</tt> if the given entity was <tt>null</tt>. Otherwise either a refreshed instance will
     * be returned or an exception will be thrown.
     *
     * @param entity the entity to refresh
     * @param <T>    the type of the entity to refresh
     * @return a fresh instance of the given entity or <tt>null</tt> if <tt>null</tt> was passed in
     * @throws sirius.kernel.health.HandledException if the entity is no longer available in the cluster
     */
    @Nullable
    public static <T extends Entity> T refreshOrFail(@Nullable T entity) {
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
     * Boilerplate method for {@link #refreshOrNull(Entity)}.
     * <p>
     * Returns <tt>null</tt> if the given entity was <tt>null</tt>. Otherwise either a refreshed instance will
     * be returned or the original given instance.
     *
     * @param entity the entity to refresh
     * @param <T>    the type of the entity to refresh
     * @return a fresh instance of the given entity or <tt>null</tt> if <tt>null</tt> was passed in
     */
    @Nullable
    public static <T extends Entity> T refreshIfPossible(@Nullable T entity) {
        T freshEntity = refreshOrNull(entity);
        if (freshEntity == null) {
            return entity;
        } else {
            return freshEntity;
        }
    }

    /**
     * Returns the name of the index associated with the given entity.
     *
     * @param entity the entity which index is requested
     * @param <E>    the type of the entity to get the index for
     * @return the index name associated with the given entity
     */
    protected static <E extends Entity> String getIndex(E entity) {
        if (entity != null) {
            String result = entity.getIndex();
            if (result != null) {
                return getIndexName(result);
            }
        }
        return getIndexName(getDescriptor(entity.getClass()).getIndex());
    }

    /**
     * Returns the name of the index associated with the given class.
     *
     * @param clazz the entity type which index is requested
     * @param <E>   the type of the entity to get the index for
     * @return the index name associated with the given class
     */
    protected static <E extends Entity> String getIndex(Class<E> clazz) {
        return getIndexName(getDescriptor(clazz).getIndex());
    }

    /**
     * Qualifies the given index name with the prefix.
     *
     * @param name the index name to qualify
     * @return the qualified name of the given index name
     */
    protected static String getIndexName(String name) {
        return indexPrefix + name;
    }

    /**
     * Tries to delete the given entity unless it was modified since the last read.
     *
     * @param entity the entity to delete
     * @param <E>    the type of the entity to delete
     * @throws OptimisticLockException if the entity was modified since the last read
     */
    public static <E extends Entity> void tryDelete(E entity) throws OptimisticLockException {
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
    public static <E extends Entity> void delete(E entity) {
        try {
            delete(entity, false);
        } catch (OptimisticLockException e) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to delete '%s' (%s): %s (%s)",
                                                    entity.toString(),
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
    public static <E extends Entity> void forceDelete(E entity) {
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
    protected static <E extends Entity> void delete(final E entity,
                                                    final boolean force) throws OptimisticLockException {
        try {
            if (entity.isNew()) {
                return;
            }
            EntityDescriptor descriptor = getDescriptor(entity.getClass());
            if (LOG.isFINE()) {
                LOG.FINE("DELETE[FORCE: %b]: %s.%s: %s",
                         force,
                         getIndex(entity.getClass()),
                         descriptor.getType(),
                         entity.getId());
            }
            entity.beforeDelete();
            Watch w = Watch.start();
            DeleteRequestBuilder drb = getClient().prepareDelete(getIndex(entity),
                                                                 descriptor.getType(),
                                                                 entity.getId());
            if (!force) {
                drb.setVersion(entity.getVersion());
            }
            if (descriptor.hasRouting()) {
                Object routingKey = descriptor.getProperty(descriptor.getRouting()).writeToSource(entity);
                if (Strings.isEmpty(routingKey)) {
                    LOG.WARN("Deleting an entity of type %s (%s) without routing information!",
                             entity.getClass().getName(),
                             entity.getId());
                } else {
                    drb.setRouting(String.valueOf(routingKey));
                }
            }
            drb.execute().actionGet();
            entity.deleted = true;
            queryDuration.addValue(w.elapsedMillis());
            w.submitMicroTiming("ES", "DELETE " + entity.getClass().getName());
            entity.afterDelete();
            if (LOG.isFINE()) {
                LOG.FINE("DELETE: %s.%s: %s SUCCESS",
                         getIndex(entity.getClass()),
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
        } catch (Throwable e) {
            throw Exceptions.handle()
                            .to(LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to delete '%s' (%s): %s (%s)",
                                                    entity.toString(),
                                                    entity.getId())
                            .handle();
        }
    }

    protected static <E extends Entity> void reportClash(E entity) {
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

    protected static <E extends Entity> void traceChange(E entity) {
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
     * Creates a new query for objects of the given class.
     *
     * @param clazz the class of objects to query
     * @param <E>   the type of the entity to query
     * @return a new query against the database
     */
    public static <E extends Entity> Query<E> select(Class<E> clazz) {
        return new Query<>(clazz);
    }

    /**
     * Installs the given ElasticSearch client
     *
     * @param client the client to use
     */
    public static void setClient(Client client) {
        Index.client = client;
    }

    /**
     * Returns the underlying ElasticSearch client
     *
     * @return the client used by this class
     */
    public static Client getClient() {
        return client;
    }

    /**
     * Returns the selected index prefix
     *
     * @return the index prefix added to each index name
     */
    public static String getIndexPrefix() {
        return indexPrefix;
    }

    /**
     * Sets the index prefix to use
     *
     * @param indexPrefix the index prefix to use
     */
    public static void setIndexPrefix(String indexPrefix) {
        Index.indexPrefix = indexPrefix;
    }

    public static void generateEmptyInMemoryInstance() {
        if (inMemoryNode != null) {
            ready = false;
            client.close();
            inMemoryNode.close();
        }

        File tmpDir = new File(System.getProperty("java.io.tmpdir"),
                               CallContext.getCurrent().getNodeName() + "_in_memory_es");
        tmpDir.mkdirs();

        Settings settings = ImmutableSettings.settingsBuilder()
                                             .put("node.http.enabled", false)
                                             .put("path.data", tmpDir.getAbsolutePath())
                                             .put("index.gateway.type", "none")
                                             .put("gateway.type", "none")
                                             .put("index.store.type", "memory")
                                             .put("index.number_of_shards", 1)
                                             .put("index.number_of_replicas", 0)
                                             .build();
        inMemoryNode = NodeBuilder.nodeBuilder().data(true).settings(settings).local(true).node();
        client = inMemoryNode.client();
        if (schema != null) {
            for (String msg : schema.createMappings()) {
                LOG.FINE(msg);
            }
        }
        ready = true;
    }

    @Part
    private static Content content;

    @SuppressWarnings("unchecked")
    public static void loadDataset(String dataset) {
        try {
            if (inMemoryNode == null) {
                throw Exceptions.createHandled()
                                .withSystemErrorMessage("Cannot load datasets when not running as 'in-memory'")
                                .handle();
            }
            LOG.INFO("Loading dataset: %s", dataset);
            Resource res = content.resolve(dataset)
                                  .orElseThrow(() -> new IllegalArgumentException("Unknown dataset: " + dataset));
            String contents = CharStreams.toString(new InputStreamReader(res.getUrl().openStream(), Charsets.UTF_8));
            JSONArray json = JSON.parseArray(contents);
            for (JSONObject obj : (List<JSONObject>) (Object) json) {
                try {
                    String type = obj.getString("_type");
                    Class<? extends Entity> entityClass = Index.getType(type);
                    EntityDescriptor descriptor = getDescriptor(entityClass);
                    Entity entity = entityClass.newInstance();
                    entity.setId(obj.getString("_id"));
                    descriptor.readSource(entity, obj);
                    update(entity);
                } catch (Throwable e) {
                    throw new IllegalArgumentException("Cannot load: " + obj, e);
                }
            }
            blockThreadForUpdate();
        } catch (IOException e) {
            throw Exceptions.handle(e);
        }
    }

}
