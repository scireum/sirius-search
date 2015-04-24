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
import sirius.kernel.async.Async;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.Injector;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Parts;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.search.properties.PropertyFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Describes the expected database schema based on all known subclasses of {@link Entity}.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
public class Schema {

    /**
     * Contains all known property factories. These are used to transform fields defined by entity classes to
     * properties
     */
    @Parts(PropertyFactory.class)
    protected static PartCollection<PropertyFactory> factories;

    /**
     * Contains a map with an entity descriptor for each entity class
     */
    private Map<Class<? extends Entity>, EntityDescriptor> descriptorTable = Collections.synchronizedMap(new HashMap<Class<? extends Entity>, EntityDescriptor>());

    /**
     * Contains a map providing the class for each entity type name
     */
    private Map<String, Class<? extends Entity>> nameTable = Collections.synchronizedMap(new HashMap<String, Class<? extends Entity>>());

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
        List<String> changes = new ArrayList<String>();
        for (EntityDescriptor ed : descriptorTable.values()) {
            String index = indexPrefix + ed.getIndex();
            try {
                IndicesExistsResponse res = Index.getClient()
                                                 .admin()
                                                 .indices()
                                                 .prepareExists(index)
                                                 .execute()
                                                 .get(10, TimeUnit.SECONDS);
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
                Index.addMapping(indexPrefix + e.getValue().getIndex(), e.getKey(), false);
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
                      Sirius.getConfig()
                            .getInt(Sirius.getConfig()
                                          .hasPath("index.settings." + ed.getIndex() + ".numberOfShards") ? "index.settings." + ed
                                    .getIndex() + ".numberOfShards" : "index.settings.default.numberOfShards"));
        builder.field("number_of_replicas",
                      Sirius.getConfig()
                            .getInt(Sirius.getConfig()
                                          .hasPath("index.settings." + ed.getIndex() + ".numberOfReplicas") ? "index.settings." + ed
                                    .getIndex() + ".numberOfReplicas" : "index.settings.default.numberOfReplicas"));
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
        Async.defaultExecutor().fork(new Runnable() {
            @Override
            public void run() {
                Index.LOG.INFO("Creating Mappings: " + newPrefix);
                for (String result : createMappings(newPrefix)) {
                    Index.LOG.INFO(result);
                }
                Index.LOG.INFO("Re-Indexing from: " + Index.getIndexPrefix() + " to " + newPrefix);
                BulkRequestBuilder bulk = Index.getClient().prepareBulk();
                int counter = 0;
                try {
                    for (EntityDescriptor ed : descriptorTable.values()) {
                        Index.LOG.INFO("Re-Indexing: " + newPrefix + ed.getIndex() + "." + ed.getType());
                        try {
                            SearchRequestBuilder srb = Index.getClient()
                                                            .prepareSearch(Index.getIndexPrefix() + ed.getIndex())
                                                            .setTypes(ed.getType());
                            srb.setSearchType(SearchType.SCAN);
                            srb.setSize(10); // 10 per shard!
                            srb.setScroll(org.elasticsearch.common.unit.TimeValue.timeValueSeconds(60));
                            SearchResponse searchResponse = srb.execute().actionGet();
                            while (true) {
                                Watch w = Watch.start();
                                searchResponse = Index.getClient()
                                                      .prepareSearchScroll(searchResponse.getScrollId())
                                                      .setScroll(org.elasticsearch.common.unit.TimeValue.timeValueSeconds(
                                                              60))
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
                                        Index.LOG.INFO("Executing bulk: " + ed.getType());
                                        BulkResponse res = bulk.execute().actionGet();
                                        if (res.hasFailures()) {
                                            for (BulkItemResponse itemRes : res.getItems()) {
                                                if (itemRes.isFailed()) {
                                                    Index.LOG.SEVERE("Re-Indexing failed: " + itemRes.getFailureMessage());
                                                }
                                            }
                                        }
                                        bulk = Index.getClient().prepareBulk();
                                        counter = 0;
                                    }
                                }
                                //Break condition: No hits are returned
                                if (searchResponse.getHits().hits().length == 0) {
                                    break;
                                }
                            }
                        } catch (Throwable t) {
                            throw Exceptions.handle(Index.LOG, t);
                        }
                    }
                } finally {
                    if (counter > 0) {
                        Index.LOG.INFO("Executing final bulk...");
                        BulkResponse res = bulk.execute().actionGet();
                        if (res.hasFailures()) {
                            for (BulkItemResponse itemRes : res.getItems()) {
                                if (itemRes.isFailed()) {
                                    Index.LOG.SEVERE("Re-Indexing failed: " + itemRes.getFailureMessage());
                                }
                            }
                        }
                        bulk = Index.getClient().prepareBulk();
                        counter = 0;
                    }
                    Index.LOG.INFO("Re-Index is COMPLETED! You may now start breathing again...");
                }
            }
        }).execute();
    }
}
