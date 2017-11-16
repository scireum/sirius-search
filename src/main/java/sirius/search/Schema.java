/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import com.typesafe.config.ConfigValue;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.Injector;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Parts;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.search.properties.PropertyFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * Describes the expected database schema based on all known subclasses of {@link Entity}.
 */
public class Schema {

    private static final String CONFIG_PREFIX_INDEX_SETTINGS = "index.settings.";
    /**
     * Contains all known property factories. These are used to transform fields defined by entity classes to
     * properties
     */
    @Parts(PropertyFactory.class)
    protected static PartCollection<PropertyFactory> factories;

    private IndexAccess access;

    /**
     * To support multiple installations in parallel, an indexPrefix can be supplied, which is added to each index
     */
    private String indexPrefix;

    private boolean temporaryIndexPrefix = false;

    /**
     * Contains a map with an entity descriptor for each entity class
     */
    protected Map<Class<? extends Entity>, EntityDescriptor> descriptorTable =
            Collections.synchronizedMap(new HashMap<Class<? extends Entity>, EntityDescriptor>());

    /**
     * Contains a map providing the class for each entity type name
     */
    protected Map<String, Class<? extends Entity>> nameTable =
            Collections.synchronizedMap(new HashMap<String, Class<? extends Entity>>());

    protected Schema(IndexAccess access) {
        this.access = access;
        indexPrefix = Sirius.getSettings().getString("index.prefix");
        if (indexPrefix.contains("${timestamp}")) {
            temporaryIndexPrefix = true;
            indexPrefix = indexPrefix.replace("${timestamp}", String.valueOf(System.currentTimeMillis()));
            IndexAccess.LOG.INFO("Using unique index prefix: %s", indexPrefix);
        }
        if (Strings.isFilled(indexPrefix) && !indexPrefix.endsWith("-")) {
            indexPrefix = indexPrefix + "-";
        }
    }

    /*
     * Adds a known entity class
     */
    private <E extends Entity> void addKnownClass(Class<E> entityType) {
        EntityDescriptor result = descriptorTable.get(entityType);
        if (result == null) {
            result = new EntityDescriptor(entityType);
            descriptorTable.put(entityType, result);
            nameTable.put(result.getIndex() + "-" + result.getType(), entityType);
        }
    }

    /*
     * Links the schema and builds up foreign keys, etc.
     */
    private void linkSchema() {
        for (EntityDescriptor e : descriptorTable.values()) {
            for (ForeignKey fk : e.getForeignKeys()) {
                EntityDescriptor other = getDescriptor(fk.getReferencedClass());
                if (other == null) {
                    IndexAccess.LOG.WARN("Cannot reference non-entity class %s from %s",
                                         fk.getReferencedClass().getSimpleName(),
                                         e.getType());
                } else {
                    other.remoteForeignKeys.add(fk);
                }
            }
        }
    }

    /**
     * Returns a set of all known indices.
     *
     * @return a set of all known ElasticSearch indices
     */
    protected Set<String> getIndices() {
        Set<String> result = new TreeSet<>();
        for (EntityDescriptor e : descriptorTable.values()) {
            result.add(getIndexName(e.getIndex()));
        }
        return result;
    }

    /**
     * Returns the name of the index associated with the given entity.
     *
     * @param entity the entity which index is requested
     * @param <E>    the type of the entity to get the index for
     * @return the index name associated with the given entity
     */
    public <E extends Entity> String getIndex(@Nonnull E entity) {
        String result = entity.getIndex();
        if (result != null) {
            return getIndexName(result);
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
    public <E extends Entity> String getIndex(Class<E> clazz) {
        return getIndexName(getDescriptor(clazz).getIndex());
    }

    /**
     * Qualifies the given index name with the prefix.
     *
     * @param name the index name to qualify
     * @return the qualified name of the given index name
     */
    public String getIndexName(String name) {
        return indexPrefix + name;
    }

    /**
     * Returns a collection of all known type names.
     *
     * @return a collection of all known type names
     */
    public Collection<String> getTypeNames() {
        return nameTable.keySet();
    }

    /**
     * Creates and executes all required mapping changes.
     *
     * @return a list of mapping change actions which were performed
     */
    public List<String> createMappings() {
        return createMappings(indexPrefix);
    }

    @SuppressWarnings("squid:S2095")
    @Explain("The content builder of the mapping doesn't have to be closed here")
    protected List<String> createMappings(String indexPrefix) {
        List<String> changes = new ArrayList<>();
        for (EntityDescriptor ed : descriptorTable.values()) {
            createIndex(indexPrefix, ed, changes);
        }

        for (Map.Entry<Class<? extends Entity>, EntityDescriptor> e : descriptorTable.entrySet()) {
            try {
                if (IndexAccess.LOG.isFINE()) {
                    IndexAccess.LOG.FINE("MAPPING OF %s : %s",
                                         e.getValue().getType(),
                                         e.getValue().createMapping().prettyPrint().string());
                }
                access.addMapping(indexPrefix + e.getValue().getIndex(), e.getKey());
                changes.add("Created mapping for " + e.getValue().getType() + " in " + e.getValue().getIndex());
            } catch (HandledException ex) {
                changes.add(ex.getMessage());
            } catch (Exception ex) {
                changes.add(Exceptions.handle(IndexAccess.LOG, ex).getMessage());
            }
        }

        return changes;
    }

    private void createIndex(String indexPrefix, EntityDescriptor descriptor, List<String> changes) {
        String index = indexPrefix + descriptor.getIndex();
        try {
            IndicesExistsResponse res =
                    access.getClient().admin().indices().prepareExists(index).execute().get(10, TimeUnit.SECONDS);
            if (!res.isExists()) {
                CreateIndexResponse createResponse = access.getClient()
                                                           .admin()
                                                           .indices()
                                                           .prepareCreate(index)
                                                           .setSettings(createIndexSettings(descriptor))
                                                           .execute()
                                                           .get(10, TimeUnit.SECONDS);
                if (createResponse.isAcknowledged()) {
                    changes.add("Created index " + index + " successfully!");
                } else {
                    changes.add("Failed to create index " + index + "!");
                }
            }
        } catch (Exception e) {
            IndexAccess.LOG.WARN(e);
            changes.add("Cannot create index " + index + ": " + e.getMessage());
        }
    }

    private XContentBuilder createIndexSettings(EntityDescriptor ed) throws IOException {
        try (XContentBuilder builder = XContentFactory.jsonBuilder()) {

            builder.startObject().startObject("index");
            String numberOfShardsKey = "index.settings.default.numberOfShards";
            if (!ed.getAnnotatedIndex().isEmpty() && Sirius.getSettings()
                                                           .getConfig()
                                                           .hasPath(CONFIG_PREFIX_INDEX_SETTINGS
                                                                    + ed.getAnnotatedIndex()
                                                                    + ".numberOfShards")) {
                numberOfShardsKey = CONFIG_PREFIX_INDEX_SETTINGS + ed.getAnnotatedIndex() + ".numberOfShards";
            }
            String numberOfReplicasKey = "index.settings.default.numberOfReplicas";
            if (!ed.getAnnotatedIndex().isEmpty() && Sirius.getSettings()
                                                           .getConfig()
                                                           .hasPath(CONFIG_PREFIX_INDEX_SETTINGS
                                                                    + ed.getAnnotatedIndex()
                                                                    + ".numberOfReplicas")) {
                numberOfReplicasKey = CONFIG_PREFIX_INDEX_SETTINGS + ed.getAnnotatedIndex() + ".numberOfReplicas";
            }
            builder.field("number_of_shards", Sirius.getSettings().getConfig().getInt(numberOfShardsKey));
            builder.field("number_of_replicas", Sirius.getSettings().getConfig().getInt(numberOfReplicasKey));

            builder.startObject("analysis");
            buildCustomAnalyzers(builder);
            buildCustomTokenizers(builder);
            buildCustomFilters(builder);
            builder.endObject();

            builder.endObject().endObject();
            return builder;
        }
    }

    private void buildCustomTokenizers(XContentBuilder builder) throws IOException {
        if (Sirius.getSettings().getConfig().hasPath("index.customTokenizers")) {
            builder.startObject("tokenizer");
            readCustomAnalysisSettings("index.customTokenizers", builder);
            builder.endObject();
        }
    }

    private void buildCustomAnalyzers(XContentBuilder builder) throws IOException {
        if (Sirius.getSettings().getConfig().hasPath("index.customAnalyzers")) {
            builder.startObject("analyzer");
            readCustomAnalysisSettings("index.customAnalyzers", builder);
            builder.endObject();
        }
    }

    private void buildCustomFilters(XContentBuilder builder) throws IOException {
        if (Sirius.getSettings().getConfig().hasPath("index.customFilters")) {
            builder.startObject("filter");
            readCustomAnalysisSettings("index.customFilters", builder);
            builder.endObject();
        }
    }

    @SuppressWarnings("unchecked")
    private void readCustomAnalysisSettings(String configPath, XContentBuilder builder) throws IOException {
        for (Map.Entry<String, ConfigValue> entry : Sirius.getSettings().getConfig(configPath).root().entrySet()) {
            builder.startObject(entry.getKey());
            for (Map.Entry<String, Object> inner : ((Map<String, Object>) entry.getValue().unwrapped()).entrySet()) {
                if (inner.getValue() instanceof List) {
                    List<String> list = (List<String>) inner.getValue();
                    builder.array(inner.getKey(), list.toArray(new String[list.size()]));
                } else {
                    builder.field(inner.getKey(), inner.getValue());
                }
            }
            builder.endObject();
        }
    }

    /**
     * Returns the entity descriptor for the given entity class
     *
     * @param entityType the class of the entity which descriptor is being searched
     * @param <E>        the type to get the descriptor for
     * @return the entity descriptor for the given class or <tt>null</tt> if no descriptor is found
     */
    @Nullable
    public <E extends Entity> EntityDescriptor getDescriptor(Class<E> entityType) {
        return descriptorTable.get(entityType);
    }

    /**
     * Returns a collection of all present entity classes
     *
     * @return a collection of entity classes or <tt>null</tt> if no entities are found
     */
    public Set<Class<? extends Entity>> getEntities() {
        return descriptorTable.keySet();
    }

    /**
     * Returns the entity class for the given entity type.
     *
     * @param name the entity type which class is being searched
     * @return the entity class or <tt>null</tt> if no entity with the given type name exists
     */
    public Class<? extends Entity> getType(String name) {
        return nameTable.get(name);
    }

    /**
     * Initializes the schema by loading all entity classes and running the cross link detection
     */
    public void load() {
        // Load all known schemas..
        for (Entity e : Injector.context().getParts(Entity.class)) {
            addKnownClass(e.getClass());
        }

        // Detect cross references
        linkSchema();
    }

    /**
     * Performs a re-index of all indices into new ones starting with the given index prefix instead of the
     * currently active one.
     *
     * @param prefix the new index prefix to use
     */
    public void reIndex(String prefix) {
        final String newPrefix = prefix.endsWith("-") ? prefix : prefix + "-";
        access.tasks.defaultExecutor().fork(new ReIndexTask(this, newPrefix));
    }

    /**
     * Deletes all temporarily created indices (used by UNIT tests).
     */
    protected void dropTemporaryIndices() {
        if (!Sirius.isStartedAsTest()) {
            return;
        }
        if (!temporaryIndexPrefix) {
            return;
        }

        for (String index : getIndices()) {
            try {
                DeleteIndexResponse res =
                        access.getClient().admin().indices().prepareDelete(index).execute().get(10, TimeUnit.SECONDS);
                if (res.isAcknowledged()) {
                    IndexAccess.LOG.INFO("Successfully deleted temporary index: %s", index);
                } else {
                    IndexAccess.LOG.WARN("Failed to delete temporary index: %s", index);
                }
            } catch (Exception e) {
                Exceptions.handle()
                          .error(e)
                          .to(IndexAccess.LOG)
                          .withSystemErrorMessage("Failed to delete temporary index %s: %s (%s)", index)
                          .handle();
            }
        }
    }
}
