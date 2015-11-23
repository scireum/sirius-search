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
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import sirius.kernel.Sirius;
import sirius.kernel.async.Tasks;
import sirius.kernel.di.Injector;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.search.properties.PropertyFactory;

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

    /**
     * Contains all known property factories. These are used to transform fields defined by entity classes to
     * properties
     */
    @Parts(PropertyFactory.class)
    protected static PartCollection<PropertyFactory> factories;

    @Part
    private Tasks tasks;

    private IndexAccess access;

    /**
     * To support multiple installations in parallel, an indexPrefix can be supplied, which is added to each index
     */
    private String indexPrefix;

    public Schema() {
        indexPrefix = Sirius.getConfig().getString("index.prefix");
        if (!indexPrefix.endsWith("-")) {
            indexPrefix = indexPrefix + "-";
        }
    }

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
        Set<String> result = new TreeSet<String>();
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
    public <E extends Entity> String getIndex(E entity) {
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
            String index = indexPrefix + ed.getIndex();
            try {
                IndicesExistsResponse res =
                        access.getClient().admin().indices().prepareExists(index).execute().get(10, TimeUnit.SECONDS);
                if (!res.isExists()) {
                    CreateIndexResponse createResponse = access.getClient()
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
                IndexAccess.LOG.WARN(e);
                changes.add("Cannot create index " + index + ": " + e.getMessage());
            }
        }
        for (Map.Entry<Class<? extends Entity>, EntityDescriptor> e : descriptorTable.entrySet()) {
            try {
                if (IndexAccess.LOG.isFINE()) {
                    IndexAccess.LOG.FINE("MAPPING OF %s : %s",
                                         e.getValue().getType(),
                                         e.getValue().createMapping().prettyPrint().string());
                }
                index.addMapping(indexPrefix + e.getValue().getIndex(), e.getKey());
                changes.add("Created mapping for " + e.getValue().getType() + " in " + e.getValue().getIndex());
            } catch (HandledException ex) {
                changes.add(ex.getMessage());
            } catch (Throwable ex) {
                changes.add(Exceptions.handle(IndexAccess.LOG, ex).getMessage());
            }
        }

        return changes;
    }

    private XContentBuilder createIndexSettings(EntityDescriptor ed) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject().startObject("index");
        builder.field("number_of_shards",
                      Sirius.getConfig()
                            .getInt(Sirius.getConfig().hasPath("index.settings." + ed.getIndex() + ".numberOfShards") ?
                                    "index.settings." + ed.getIndex() + ".numberOfShards" :
                                    "index.settings.default.numberOfShards"));
        builder.field("number_of_replicas",
                      Sirius.getConfig()
                            .getInt(Sirius.getConfig()
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
        tasks.defaultExecutor().fork(new ReIndexTask(this, newPrefix));
    }
}
