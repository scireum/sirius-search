/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.Injector;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Parts;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.search.annotations.Indexed;
import sirius.search.properties.PropertyFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Modifier;
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
     *
     * @param entityType class to add
     * @param <E>        type of the class to add
     */
    private <E extends Entity> void addKnownClass(Class<E> entityType) {
        EntityDescriptor result = descriptorTable.get(entityType);
        if (result == null) {
            result = new EntityDescriptor(entityType);
            descriptorTable.put(entityType, result);
            nameTable.put(result.getIndex() + "-" + result.getType(), entityType);

            if (Strings.isFilled(result.getSubClassCode()) && !findAbstractParentClass(entityType, result)) {
                IndexAccess.LOG.WARN(
                        "LOAD: Class %s has subClassCode but there is no abstract parent class with the same index name, type name and routing!",
                        entityType.getName());
            }
        }
    }

    /**
     * Searches for the first abstract parent class in the class hierarchy with the same {@link Indexed} annotation and
     * creates a {@link EntityDescriptor} for it.
     *
     * @param subClass           the subclass
     * @param subClassDescriptor descriptor of the subclass
     * @param <E>                type of the subclass
     * @return whether a matching parent class could be found
     */
    @SuppressWarnings("unchecked")
    private <E extends Entity> boolean findAbstractParentClass(Class<E> subClass, EntityDescriptor subClassDescriptor) {
        // iterate recursively over all parent classes until the Entity class
        for (Class<? extends Entity> parentEntityType = (Class<? extends Entity>) subClass.getSuperclass();
             parentEntityType != null && Entity.class.isAssignableFrom(parentEntityType);
             parentEntityType = (Class<? extends Entity>) parentEntityType.getSuperclass()) {
            if (Modifier.isAbstract(parentEntityType.getModifiers())
                && parentEntityType.isAnnotationPresent(Indexed.class)) {
                EntityDescriptor parentDescriptor =
                        descriptorTable.getOrDefault(parentEntityType, new EntityDescriptor(parentEntityType));

                // index name, type name and routing must be equal
                if (Strings.areEqual(parentDescriptor.getAnnotatedIndex(), subClassDescriptor.getAnnotatedIndex()) && Strings.areEqual(
                        parentDescriptor.getRouting(),
                        subClassDescriptor.getRouting())) {
                    addAbstractParentClass(subClass, subClassDescriptor, parentEntityType, parentDescriptor);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Links the <tt>subClassDescriptor</tt> to the <tt>parentClassDescriptor</tt> and stores the latter for later use.
     *
     * @param subClass              type of the subclass
     * @param subClassDescriptor    descriptor of the subclass
     * @param parentClass           type of the parent class
     * @param parentClassDescriptor descriptor of the parent class
     */
    private void addAbstractParentClass(Class<? extends Entity> subClass,
                                        EntityDescriptor subClassDescriptor,
                                        Class<? extends Entity> parentClass,
                                        EntityDescriptor parentClassDescriptor) {
        if (parentClassDescriptor.getSubClassDescriptors().containsKey(subClassDescriptor.getSubClassCode())) {
            IndexAccess.LOG.WARN(
                    "LOAD: Classes %s and %s have the same parent class %s and the same subclass-code \"%s\"!",
                    subClass.getName(),
                    parentClassDescriptor.getSubClassDescriptors()
                                         .get(subClassDescriptor.getSubClassCode())
                                         .getEntityType()
                                         .getName(),
                    parentClass,
                    subClassDescriptor.getSubClassCode());
        } else {
            parentClassDescriptor.getSubClassDescriptors()
                                 .put(subClassDescriptor.getSubClassCode(), subClassDescriptor);
            subClassDescriptor.setParentDescriptor(parentClassDescriptor);
            descriptorTable.put(parentClass, parentClassDescriptor);
            nameTable.put(parentClassDescriptor.getIndex() + "-" + parentClassDescriptor.getType(), parentClass);
        }
    }

    /**
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

    protected List<String> createMappings(String indexPrefix) {
        List<String> changes = new ArrayList<>();
        for (EntityDescriptor ed : descriptorTable.values()) {
            createIndex(indexPrefix, ed, changes);
        }

        for (Map.Entry<Class<? extends Entity>, EntityDescriptor> e : descriptorTable.entrySet()) {
            try {
                if (e.getValue().isSubClassDescriptor()) {
                    continue;
                }

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
            buildCustomFilters(builder);
            builder.endObject();

            builder.endObject().endObject();
            return builder;
        }
    }

    private void buildCustomAnalyzers(XContentBuilder builder) throws IOException {
        builder.startObject("analyzer");

        builder.startObject("trigram");
        builder.field("type", "custom");
        builder.field("tokenizer", "standard");
        builder.array("filter", "standard", "shingle");
        builder.endObject();

        builder.endObject();
    }

    private void buildCustomFilters(XContentBuilder builder) throws IOException {
        builder.startObject("filter");

        builder.startObject("shingle");
        builder.field("type", "shingle");
        builder.field("min_shingle_size", "2");
        builder.field("max_shingle_size", "3");
        builder.endObject();

        builder.endObject();
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
