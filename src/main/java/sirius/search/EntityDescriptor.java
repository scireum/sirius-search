/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import sirius.kernel.commons.Reflection;
import sirius.kernel.commons.Strings;
import sirius.search.annotations.Indexed;
import sirius.search.annotations.RefField;
import sirius.search.annotations.RefType;
import sirius.search.annotations.Transient;
import sirius.search.properties.Property;
import sirius.search.properties.PropertyFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contains metadata collected by inspecting an entity class (subclass of {@link Entity}).
 * <p>
 * For each entity class an <tt>EntityDescriptor</tt> is automatically created and filled by the framework. Most of
 * this data is used by the framework to define the mapping (schema) in ElasticSearch, etc.
 * </p>
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
public class EntityDescriptor {

    private final String indexName;
    private String typeName;
    private String routing;
    private final Class<?> clazz;
    protected List<Property> properties;
    protected List<ForeignKey> foreignKeys;
    public List<ForeignKey> remoteForeignKeys = Lists.newArrayList();

    /**
     * Creates a new EntityDescriptor for the given entity class.
     *
     * @param clazz the entity class to be inspected
     */
    public EntityDescriptor(Class<?> clazz) {
        this.clazz = clazz;
        if (!clazz.isAnnotationPresent(Indexed.class)) {
            throw new IllegalArgumentException("Missing @Indexed-Annotation: " + clazz.getName());
        }
        this.indexName = clazz.getAnnotation(Indexed.class).index();
        this.typeName = clazz.getAnnotation(Indexed.class).type();
        this.routing = clazz.getAnnotation(Indexed.class).routing();
        if (Strings.isEmpty(routing)) {
            routing = null;
        }
        if ("".equals(typeName)) {
            typeName = clazz.getSimpleName();
        }
    }

    /**
     * Returns a list of properties which will be stored in the database
     *
     * @return the list of persisted properties
     */
    public List<Property> getProperties() {
        if (properties == null) {
            List<Property> props = new ArrayList<Property>();
            List<ForeignKey> keys = new ArrayList<ForeignKey>();
            addProperties(clazz, clazz, props, keys);
            properties = props;
            foreignKeys = keys;
        }

        return properties;
    }

    /*
     * Adds all properties of the given class (and its superclasses)
     */
    @SuppressWarnings("unchecked")
    private void addProperties(Class<?> rootClass, Class<?> clazz, List<Property> props, List<ForeignKey> keys) {
        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Transient.class) && !Modifier.isStatic(field.getModifiers())) {
                Property p = null;
                for (PropertyFactory f : Schema.factories.getParts()) {
                    if (f.accepts(field)) {
                        p = f.create(field);
                        if (propertyAlreadyExists(props, p)) {
                            Index.LOG.SEVERE(Strings.apply(
                                    "A property named '%s' already exists for the type '%s'. Cannot transform field %s",
                                    p.getName(),
                                    rootClass.getSimpleName(),
                                    field));
                            p = null;
                            break;
                        }
                        props.add(p);
                        if (!p.acceptsSetter() && hasSetter(field)) {
                            Index.LOG.WARN("Property %s in type %s does not accept a setter method to be present",
                                           field.getName(),
                                           rootClass.getSimpleName());
                        }
                        break;
                    }
                }
                if (p == null) {
                    Index.LOG.WARN("Cannot create property %s in type %s", field.getName(), clazz.getSimpleName());
                } else {
                    if (field.isAnnotationPresent(RefType.class)) {
                        keys.add(new ForeignKey(field, (Class<? extends Entity>) rootClass));
                    }
                    if (field.isAnnotationPresent(RefField.class)) {
                        ForeignKey key = findForeignKey(keys, field.getAnnotation(RefField.class).localRef());
                        if (key == null) {
                            Index.LOG.WARN("No foreign key %s found for field reference %s    ",
                                           field.getAnnotation(RefField.class).localRef(),
                                           field.getName());
                        } else {
                            key.addReference(p, field.getAnnotation(RefField.class).remoteField());
                        }
                    }
                }
            }
        }
        if (clazz.getSuperclass() != null && !Object.class.equals(clazz.getSuperclass())) {
            addProperties(rootClass, clazz.getSuperclass(), props, keys);
        }
    }

    private boolean propertyAlreadyExists(List<Property> props, Property check) {
        for (Property prop : props) {
            if (Strings.areEqual(prop.getName(), check.getName())) {
                return true;
            }
        }

        return false;
    }

    /*
     * Determines if there is a setter method (setXXX) for the given field.
     */
    private boolean hasSetter(Field field) {
        try {
            field.getDeclaringClass().getMethod("set" + Reflection.toFirstUpper(field.getName()), field.getType());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /*
     * Searches for the foreign key with the given name
     */
    private ForeignKey findForeignKey(List<ForeignKey> keys, String key) {
        for (ForeignKey k : keys) {
            if (Objects.equal(k.getName(), key)) {
                return k;
            }
        }
        return null;
    }

    /**
     * Converts the data of the given entity into the given source map.
     *
     * @param entity the entity to load the data form
     * @param source the target map to store the converted values into
     */
    protected void writeTo(Entity entity, Map<String, Object> source) {
        for (Property p : getProperties()) {
            source.put(p.getName(), p.writeToSource(entity));
        }
    }

    /**
     * Converts the data in the given source map and stores it in the given entity.
     *
     * @param entity the entity to store the loaded data
     * @param source the source map to read the data from
     */
    public void readSource(Entity entity, Map<String, Object> source) {
        for (Property p : getProperties()) {
            p.readFromSource(entity, source.get(p.getName()));
        }
    }

    /**
     * Returns the name of the index which is used to store the data.
     * <p>
     * Note that this is an ElasticSearch index and not to be confused with a database index. This would be more or
     * less something like a schema in a SQL db
     * </p>
     *
     * @return the name of the ES index used to store entities
     */
    public String getIndex() {
        return indexName;
    }

    /**
     * Returns the type name used to store entities related to this descriptor.
     * <p>
     * A type in ElasticSearch can be compared to a table in a SQL db.
     * </p>
     *
     * @return the type name used to store entities related to this descriptor
     */
    public String getType() {
        return typeName;
    }

    /**
     * Creates the mapping required by ElasticSearch to store entities related to this descriptor.
     *
     * @return a JSON structure defining the mapping of this descriptor
     * @throws IOException in case of an IO error during the JSON encoding
     */
    public XContentBuilder createMapping() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject().startObject(getType());
        builder.startObject("properties");
        for (Property p : getProperties()) {
            p.createMapping(builder);
        }
        builder.endObject();
        if (Strings.isFilled(routing)) {
            builder.startObject("_routing");
            builder.field("path", routing);
            builder.endObject();
        }

        return builder.endObject().endObject();
    }

    /**
     * Returns all foreign keys defined by this descriptor.
     *
     * @return a list of all foreign keys defined by this descriptor.
     */
    public List<ForeignKey> getForeignKeys() {
        if (foreignKeys == null) {
            getProperties();
        }
        return foreignKeys;
    }

    /**
     * Returns the property with the given name
     *
     * @param name the name of the requested property
     * @return the property with the requested name or <tt>null</tt> if no property with the given name was found
     */
    public Property getProperty(String name) {
        for (Property p : properties) {
            if (p.getName().equals(name)) {
                return p;
            }
        }

        return null;
    }

    /**
     * Determines if a routing should be specified for this type of entities.
     *
     * @return <tt>true</tt> if a routing is required, <tt>false</tt> otherwise
     */
    public boolean hasRouting() {
        return routing != null;
    }

    /**
     * Returns the field used for routing.
     *
     * @return the name of the field used for routing (shard selection) or <tt>null</tt> to indicate that there is
     * no special routing.
     */
    public String getRouting() {
        return routing;
    }

    /**
     * Determines if entities have foreign keys.
     *
     * @return <tt>true</tt> if there are foreign keys, <tt>false</tt> otherwise
     */
    public boolean hasForeignKeys() {
        return !foreignKeys.isEmpty();
    }
}
