/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.search.SearchHit;
import sirius.kernel.Sirius;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.Injector;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.search.annotations.Indexed;
import sirius.search.properties.PropertyFactory;

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

    /**
     * Logger used by this framework
     */
    public static final Log LOG = Log.get("index");

    /**
     * Contains all known property factories. These are used to transform fields defined by entity classes to
     * properties
     */
    @Parts(PropertyFactory.class)
    protected static PartCollection<PropertyFactory> factories;

    @Part
    private Tasks tasks;

    /**
     * Contains a map with an entity descriptor for each entity class
     */
    private Map<Class<? extends Entity>, EntityDescriptor> descriptorTable =
            Collections.synchronizedMap(new HashMap<Class<? extends Entity>, EntityDescriptor>());

    /**
     * Contains a map providing the class for each entity type name
     */
    private Map<String, Class<? extends Entity>> nameTable =
            Collections.synchronizedMap(new HashMap<String, Class<? extends Entity>>());

    /**
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

            if (Strings.isFilled(result.getSubClassCode()) && !addAbstractParentClass(entityType, result)) {
                LOG.WARN(
                        "LOAD: Class %s has subClassCode but no abstract parent class with the same index name, type name and routing could be found",
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
    private <E extends Entity> boolean addAbstractParentClass(Class<E> subClass, EntityDescriptor subClassDescriptor) {
        // iterate recurseviley over all parent classes until the Entity class
        for (Class<? extends Entity> parentEntityType = (Class<? extends Entity>) subClass.getSuperclass();
             parentEntityType != null && Entity.class.isAssignableFrom(parentEntityType);
             parentEntityType = (Class<? extends Entity>) parentEntityType.getSuperclass()) {
            if (Modifier.isAbstract(parentEntityType.getModifiers())
                && parentEntityType.isAnnotationPresent(Indexed.class)) {
                EntityDescriptor parentDescriptor =
                        descriptorTable.getOrDefault(parentEntityType, new EntityDescriptor(parentEntityType));

                // index name, type name and routing must be equal
                if (parentDescriptor.equals(subClassDescriptor)) {
                    if (parentDescriptor.getSubClassDescriptors().containsKey(subClassDescriptor.getSubClassCode())) {
                        LOG.WARN(
                                "LOAD: Classes %s and %s have the same parent class %s and the same subclass-code \"%s\"!",
                                subClass.getName(),
                                parentDescriptor.getSubClassDescriptors()
                                                .get(subClassDescriptor.getSubClassCode())
                                                .getClazz()
                                                .getName(),
                                parentEntityType,
                                subClassDescriptor.getSubClassCode());
                    } else {
                        parentDescriptor.getSubClassDescriptors()
                                        .put(subClassDescriptor.getSubClassCode(), subClassDescriptor);
                        descriptorTable.put(parentEntityType, parentDescriptor);
                        nameTable.put(parentDescriptor.getIndex() + "-" + parentDescriptor.getType(), subClass);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Links the schema and builds up foreign keys, etc.
     */
    private void linkSchema() {
        for (EntityDescriptor e : descriptorTable.values()) {
            for (ForeignKey fk : e.getForeignKeys()) {
                EntityDescriptor other = getDescriptor(fk.getReferencedClass());
                if (other == null) {
                    Index.LOG.WARN("Cannot reference non-entity class %s from %s",
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
        Set<String> result = new TreeSet<String>();
        for (EntityDescriptor e : descriptorTable.values()) {
            result.add(Index.getIndexName(e.getIndex()));
        }
        return result;
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
        return createMappings(Index.getIndexPrefix());
    }

    protected List<String> createMappings(String indexPrefix) {
        List<String> changes = new ArrayList<>();
        for (EntityDescriptor ed : descriptorTable.values()) {
            String index = indexPrefix + ed.getIndex();
            try {
                IndicesExistsResponse res =
                        Index.getClient().admin().indices().prepareExists(index).execute().get(10, TimeUnit.SECONDS);
                if (!res.isExists()) {
                    CreateIndexResponse createResponse = Index.getClient()
                                                              .admin()
                                                              .indices()
                                                              .prepareCreate(index)
                                                              .setSettings(createIndexSettings(ed))
                                                              .execute()
                                                              .get(10, TimeUnit.SECONDS);
                    if (createResponse.isAcknowledged()) {
                        changes.add("Created index " + index + " successfully!");
                    } else {
                        changes.add("Failed to create index " + index + "!");
                    }
                }
            } catch (Throwable e) {
                Index.LOG.WARN(e);
                changes.add("Cannot create index " + index + ": " + e.getMessage());
            }
        }
        for (Map.Entry<Class<? extends Entity>, EntityDescriptor> e : descriptorTable.entrySet()) {
            try {
                if (Index.LOG.isFINE()) {
                    Index.LOG.FINE("MAPPING OF %s : %s",
                                   e.getValue().getType(),
                                   e.getValue().createMapping().prettyPrint().string());
                }
                Index.addMapping(indexPrefix + e.getValue().getIndex(), e.getKey());
                changes.add("Created mapping for " + e.getValue().getType() + " in " + e.getValue().getIndex());
            } catch (HandledException ex) {
                changes.add(ex.getMessage());
            } catch (Throwable ex) {
                changes.add(Exceptions.handle(Index.LOG, ex).getMessage());
            }
        }

        return changes;
    }

    private XContentBuilder createIndexSettings(EntityDescriptor ed) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject().startObject("index");
        builder.field("number_of_shards",
                      Sirius.getSettings()
                            .getInt(Sirius.getSettings()
                                          .getConfig()
                                          .hasPath("index.settings." + ed.getIndex() + ".numberOfShards") ?
                                    "index.settings." + ed.getIndex() + ".numberOfShards" :
                                    "index.settings.default.numberOfShards"));
        builder.field("number_of_replicas",
                      Sirius.getSettings()
                            .getInt(Sirius.getSettings()
                                          .getConfig()
                                          .hasPath("index.settings." + ed.getIndex() + ".numberOfReplicas") ?
                                    "index.settings." + ed.getIndex() + ".numberOfReplicas" :
                                    "index.settings.default.numberOfReplicas"));
        return builder.endObject().endObject();
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
        if (!prefix.endsWith("-")) {
            prefix = prefix + "-";
        }
        final String newPrefix = prefix;
        tasks.defaultExecutor().fork(new ReIndexTask(newPrefix));
    }

    private class ReIndexTask implements Runnable {
        private final String newPrefix;
        private int counter;
        private BulkRequestBuilder bulk;

        private ReIndexTask(String newPrefix) {
            this.newPrefix = newPrefix;
        }

        @Override
        public void run() {
            Index.LOG.INFO("Creating Mappings: " + newPrefix);
            for (String result : createMappings(newPrefix)) {
                Index.LOG.INFO(result);
            }
            Index.LOG.INFO("Re-Indexing from: " + Index.getIndexPrefix() + " to " + newPrefix);
            executeAndReCreateBulk();
            try {
                for (EntityDescriptor ed : descriptorTable.values()) {
                    Index.LOG.INFO("Re-Indexing: " + newPrefix + ed.getIndex() + "." + ed.getType());
                    reIndexEntitiesOfDescriptor(ed);
                }
            } finally {
                executeAndReCreateBulk();
                Index.LOG.INFO("Re-Index is COMPLETED! You may now start breathing again...");
            }
        }

        private void reIndexEntitiesOfDescriptor(EntityDescriptor ed) {
            try {
                SearchRequestBuilder srb =
                        Index.getClient().prepareSearch(Index.getIndexPrefix() + ed.getIndex()).setTypes(ed.getType());
                srb.setSearchType(SearchType.SCAN);
                // Limit to 10 per shard
                srb.setSize(10);
                srb.setScroll(org.elasticsearch.common.unit.TimeValue.timeValueSeconds(5 * 60));
                SearchResponse searchResponse = srb.execute().actionGet();
                while (true) {
                    Watch w = Watch.start();
                    searchResponse = Index.getClient()
                                          .prepareSearchScroll(searchResponse.getScrollId())
                                          .setScroll(org.elasticsearch.common.unit.TimeValue.timeValueSeconds(5 * 60))
                                          .execute()
                                          .actionGet();
                    for (SearchHit hit : searchResponse.getHits()) {
                        bulk.add(Index.getClient()
                                      .prepareIndex(newPrefix + ed.getIndex(), ed.getType())
                                      .setId(hit.getId())
                                      .setSource(hit.source())
                                      .request());
                        counter++;
                        if (counter > 1000) {
                            executeAndReCreateBulk();
                        }
                    }
                    //Break condition: No hits are returned
                    if (searchResponse.getHits().hits().length == 0) {
                        return;
                    }
                }
            } catch (Throwable t) {
                throw Exceptions.handle(Index.LOG, t);
            }
        }

        private void executeAndReCreateBulk() {
            if (counter > 0) {
                Index.LOG.INFO("Executing bulk...");
                BulkResponse res = bulk.execute().actionGet();
                if (res.hasFailures()) {
                    for (BulkItemResponse itemRes : res.getItems()) {
                        if (itemRes.isFailed()) {
                            Index.LOG.SEVERE("Re-Indexing failed: " + itemRes.getFailureMessage());
                        }
                    }
                }
            }
            bulk = Index.getClient().prepareBulk();
            counter = 0;
        }
    }
}
