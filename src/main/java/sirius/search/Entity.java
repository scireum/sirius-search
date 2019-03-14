/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.BaseEncoding;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.search.annotations.RefField;
import sirius.search.annotations.RefType;
import sirius.search.annotations.Transient;
import sirius.search.annotations.Unique;
import sirius.search.properties.Property;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Base class for all types which are stored in ElasticSearch.
 * <p>
 * Each subclass should wear a {@link sirius.search.annotations.Indexed} annotation to indicate which index should be
 * used.
 */
public abstract class Entity {

    /**
     * Contains the unique ID of this entity. This is normally auto generated by ElasticSearch.
     */
    public static final String ID = "id";
    @Transient
    protected String id;

    /**
     * Contains the version number of the currently loaded entity. Used for optimistic locking e.g. in
     * {@link IndexAccess#tryUpdate(Entity)}
     */
    @Transient
    protected long version;

    /**
     * Determines if this entity is or will be deleted.
     */
    @Transient
    protected boolean deleted;

    /**
     * Original data loaded from the database (ElasticSearch)
     */
    @Transient
    protected Map<String, Object> source;

    /**
     * Should foreign keys be skipped after updating an entity. This can be used to escape cyclic dependencies
     */
    @Transient
    protected boolean skipForeignKeys;

    /**
     * Contains all named queries which matched this entity
     */
    @Transient
    protected String[] matchedNamedQueries;

    @Part
    private static IndexAccess index;

    @Part
    private static IdGenerator sequenceGenerator;

    /**
     * Creates and initializes a new instance.
     * <p>
     * All mapped properties will be initialized by their {@link Property} if necessary.
     */
    protected Entity() {
        if (index != null && index.schema != null) {
            for (Property p : index.getDescriptor(getClass()).getProperties()) {
                try {
                    p.init(this);
                } catch (Exception e) {
                    Exceptions.ignore(e);
                    IndexAccess.LOG.WARN("Cannot initialize %s of %s", p.getName(), getClass().getSimpleName());
                }
            }
        }
    }

    /**
     * Determines if the entity is new.
     *
     * @return determines if the entity is new (<tt>true</tt>) or if it was loaded from the database (<tt>false</tt>).
     */
    public boolean isNew() {
        return id == null || IndexAccess.NEW.equals(id);
    }

    /**
     * Determines if the entity still exists and is not about to be deleted.
     *
     * @return <tt>true</tt> if the entity is neither new, nor marked as deleted. <tt>false</tt> otherwise
     */
    public boolean exists() {
        return !isNew() && !deleted;
    }

    /**
     * Determines if the entity is marked as deleted.
     *
     * @return <tt>true</tt> if the entity is marked as deleted, <tt>false</tt> otherwise
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Returns the unique ID of the entity.
     * <p>
     * Unless the entity is new, this is never <tt>null</tt>.
     *
     * @return the id of this entity
     */
    public String getId() {
        return id;
    }

    /**
     * Returns an ID which is guaranteed to be globally unique.
     * <p>
     * Note that new entities always have default (non-unique) id.
     *
     * @return the globally unique ID of this entity.
     */
    public String getUniqueId() {
        return index.getDescriptor(getClass()).getType() + "-" + id;
    }

    /**
     * Sets the ID of this entity.
     *
     * @param id the ID for this entity
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the version of this entity.
     *
     * @return the version which was loaded from the database
     */
    public long getVersion() {
        return version;
    }

    /**
     * Sets the version of this entity.
     *
     * @param version the version to set
     */
    public void setVersion(long version) {
        this.version = version;
    }

    /**
     * Sets the deleted flag.
     *
     * @param deleted the new value of the deleted flag
     */
    protected void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    /**
     * Gets the list of all named queries which matched this entity.
     *
     * @param queryName the name of the query to check
     * @return the list of named queries which matched this entity.
     */
    public boolean isMatchedNamedQuery(String queryName) {
        return Arrays.stream(matchedNamedQueries).anyMatch(query -> Strings.areEqual(query, queryName));
    }

    /**
     * Sets the name of the queries which matched this entity.
     * <p>
     * ElasticSearch allows to name/alias a sub-query so that we can signal whether a sub-query matched an entity
     * or not to prevent additional queries.
     *
     * @param matchedNamedQueries the list of named queries which matched this entity
     */
    protected void setMatchedNamedQueries(String[] matchedNamedQueries) {
        this.matchedNamedQueries = matchedNamedQueries;
    }

    /**
     * Invoked immediately before {@link #performSaveChecks()} and permits to fill missing values.
     */
    protected void beforeSaveChecks() {
    }

    /**
     * Performs consistency checks before an entity is saved into the database.
     */
    protected void performSaveChecks() {
        HandledException error = null;
        EntityDescriptor descriptor = index.getDescriptor(getClass());
        for (Property p : descriptor.getProperties()) {

            if (p.getField().isAnnotationPresent(RefField.class)) {
                fillRefField(descriptor, p);
            }

            Object value = p.writeToSource(this);
            if (!p.isNullAllowed()) {
                error = checkNullability(error, p, value);
            }

            if (p.getField().isAnnotationPresent(Unique.class) && !Strings.isEmpty(value)) {
                error = checkUniqueness(error, descriptor, p, value);
            }
        }
        if (error != null) {
            throw error;
        }
    }

    protected HandledException checkUniqueness(HandledException previousError,
                                               EntityDescriptor descriptor,
                                               Property p,
                                               Object value) {
        Query<?> qry = index.select(getClass()).eq(p.getName(), value);
        if (!isNew()) {
            qry.notEq(IndexAccess.ID_FIELD, id);
        }
        Unique unique = p.getField().getAnnotation(Unique.class);
        setupRoutingForUniquenessCheck(descriptor, qry, unique);
        if (qry.exists()) {
            UserContext.setFieldError(p.getName(), NLS.toUserString(value));
            if (previousError == null) {
                try {
                    return Exceptions.createHandled()
                                     .withNLSKey("Entity.fieldMustBeUnique")
                                     .set("field", p.getFieldTitle())
                                     .set("value", NLS.toUserString(p.getField().get(this)))
                                     .handle();
                } catch (Exception e) {
                    Exceptions.handle(e);
                }
            }
        }
        return previousError;
    }

    private void setupRoutingForUniquenessCheck(EntityDescriptor descriptor, Query<?> qry, Unique unique) {
        if (Strings.isEmpty(unique.within())) {
            qry.deliberatelyUnrouted();
            return;
        }

        qry.eq(unique.within(), descriptor.getProperty(unique.within()).writeToSource(this));
        try {
            if (descriptor.hasRouting()) {
                Object routingKey = descriptor.getProperty(descriptor.getRouting()).writeToSource(this);
                if (routingKey != null) {
                    qry.routing(routingKey.toString());
                } else {
                    qry.deliberatelyUnrouted();
                    Exceptions.handle()
                              .to(IndexAccess.LOG)
                              .withSystemErrorMessage(
                                      "Performing a unique check on %s without any routing. This will be slow!",
                                      this.getClass().getName())
                              .handle();
                }
            }
        } catch (Exception e) {
            Exceptions.handle()
                      .to(IndexAccess.LOG)
                      .error(e)
                      .withSystemErrorMessage("Cannot determine routing key for '%s' of type %s",
                                              this,
                                              this.getClass().getName())
                      .handle();
            qry.deliberatelyUnrouted();
        }
    }

    /**
     * Can be used to perform a null check for the given field and value.
     * <p>
     * This is internally used to check all properties which must not be null
     * ({@link sirius.search.properties.Property#isNullAllowed()}). If a field accepts a <tt>null</tt> value but
     * still must be field, this method can be called in {@link #beforeSaveChecks()}.
     *
     * @param previousError Can be used to signal that an error was already found. In this case the given exception
     *                      will be returned as result even if the value was <tt>null</tt>. In most cases this
     *                      parameter will be <tt>null</tt>.
     * @param property      the field to check
     * @param value         the value to check. If the value is <tt>null</tt> an error will be generated.
     * @return an error if either the given <tt>previousError</tt> was non null or if the given value was <tt>null</tt>
     */
    @Nullable
    protected HandledException checkNullability(@Nullable HandledException previousError,
                                                @Nonnull Property property,
                                                @Nullable Object value) {
        if (Strings.isEmpty(value)) {
            UserContext.setFieldError(property.getName(), null);
            if (previousError == null) {
                return Exceptions.createHandled()
                                 .withNLSKey("Entity.fieldMustBeFilled")
                                 .set("field", property.getFieldTitle())
                                 .handle();
            }
        }
        return previousError;
    }

    @SuppressWarnings("unchecked")
    protected void fillRefField(EntityDescriptor localEntityDescriptor, Property propertyToFill) {
        try {
            RefField ref = propertyToFill.getField().getAnnotation(RefField.class);
            Property sourceProperty = localEntityDescriptor.getProperty(ref.localRef());
            EntityDescriptor remoteDescriptor =
                    index.getDescriptor(sourceProperty.getField().getAnnotation(RefType.class).type());

            EntityRef<?> sourceReference = (EntityRef<?>) sourceProperty.getField().get(this);
            Entity sourceEntity = null;
            if (sourceReference.isValueLoaded() && !sourceReference.isDirty()) {
                // Update using sourceReference if present and not from cache
                if (sourceReference.isFilled()) {
                    sourceEntity = sourceReference.getValue();
                }
            } else if (sourceReference.isDirty()) {
                sourceEntity = fetchSourceEntity(localEntityDescriptor,
                                                 propertyToFill,
                                                 sourceProperty,
                                                 remoteDescriptor,
                                                 sourceReference);
            } else {
                // Nothing has changed -> no need to load and update...
                return;
            }
            if (sourceEntity == null) {
                propertyToFill.getField().set(this, null);
            } else {
                propertyToFill.getField()
                              .set(this, remoteDescriptor.getProperty(ref.remoteField()).getField().get(sourceEntity));
            }
        } catch (Exception e) {
            Exceptions.handle()
                      .to(IndexAccess.LOG)
                      .error(e)
                      .withSystemErrorMessage(
                              "Error updating an RefField for an RefType: Property %s in class %s: %s (%s)",
                              propertyToFill.getName(),
                              this.getClass().getName())
                      .handle();
        }
    }

    private Entity fetchSourceEntity(EntityDescriptor localEntityDescriptor,
                                     Property propertyToFill,
                                     Property sourceProperty,
                                     EntityDescriptor remoteDescriptor,
                                     EntityRef<?> sourceReference) {
        String routingValue = null;
        if (remoteDescriptor.getRouting() != null) {
            routingValue = determineLocalRouting(localEntityDescriptor, sourceProperty);
            if (routingValue == null) {
                // No routing available or sourceReference was null -> fail
                Exceptions.handle()
                          .to(IndexAccess.LOG)
                          .withSystemErrorMessage("Error updating an RefField for an RefType: Property %s in class %s: "
                                                  + "No routing information was available to load the referenced entity!",
                                                  propertyToFill.getName(),
                                                  this.getClass().getName())
                          .handle();
            }
        }

        return sourceReference.getValue(routingValue);
    }

    private String determineLocalRouting(EntityDescriptor descriptor, Property entityRef) {
        String routingField = entityRef.getField().getAnnotation(RefType.class).localRouting();
        if (Strings.isFilled(routingField)) {
            return (String) descriptor.getProperty(routingField).writeToSource(this);
        }
        return null;
    }

    /**
     * Invoked before an entity is deleted from the database.
     * <p>
     * This method is not intended to be overridden. Override {@link #onDelete()} or {@link #internalOnDelete()}.
     */
    protected final void beforeDelete() {
        executeDeleteChecksOnForeignKeys();
        internalOnDelete();
        onDelete();
    }

    /**
     * Executes the {@link sirius.search.ForeignKey#checkDelete(Entity)} handlers on all foreign keys...
     */
    protected void executeDeleteChecksOnForeignKeys() {
        for (ForeignKey fk : index.getDescriptor(getClass()).remoteForeignKeys) {
            fk.checkDelete(this);
        }
    }

    /**
     * Intended for classes providing additional before delete handlers.
     * Will be invoked before the entity will be deleted.
     * <p>
     * This method SHOULD call {@code super.onDelete} to ensure that all save handlers are called. However,
     * frameworks should rely on internalOnDelete, which should not be overridden by application classes.
     */
    protected void onDelete() {
    }

    /**
     * Intended for classes providing additional before delete handlers.
     * Will be invoked before the entity will be deleted.
     * <p>
     * This method MUST call {@code super.internalOnDelete} to ensure that all save handlers are called. This is
     * intended to be overridden by framework classes. Application classes should simply override
     * {@code onDelete()}.
     */
    protected void internalOnDelete() {
    }

    /**
     * Invoked after an entity is deleted from the database.
     * <p>
     * This method is not intended to be overridden. Override {@link #onAfterDelete()} or {@link
     * #internalOnAfterDelete()}.
     */
    protected final void afterDelete() {
        executeDeleteOnForeignKeys();
        internalOnAfterDelete();
        onAfterDelete();
    }

    /**
     * Executes the {@link sirius.search.ForeignKey#onDelete(Entity)} handlers on all foreign keys...
     */
    protected void executeDeleteOnForeignKeys() {
        if (!isNew()) {
            for (ForeignKey fk : index.getDescriptor(getClass()).remoteForeignKeys) {
                fk.onDelete(this);
            }
        }
    }

    /**
     * Intended for classes providing additional after delete handlers.
     * Will be invoked after the entity deleted.
     * <p>
     * This method SHOULD call {@code super.onAfterDelete} to ensure that all save handlers are called. However,
     * frameworks should rely on internalOnAfterDelete, which should not be overridden by application classes.
     */
    protected void onAfterDelete() {
    }

    /**
     * Intended for classes providing additional after save handlers.
     * Will be invoked after the entity will was persisted.
     * <p>
     * This method MUST call {@code super.internalOnAfterDelete} to ensure that all save handlers are called. This
     * is
     * intended to be overridden by framework classes. Application classes should simply override
     * {@code onAfterDelete()}.
     */
    protected void internalOnAfterDelete() {
    }

    /**
     * Returns a verbose representation of the entity containing all fields.
     *
     * @return a string representation of the entity containing all fields
     */
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append(id + " (Version: " + version + ") {");
        boolean first = true;
        for (Property p : index.getDescriptor(getClass()).getProperties()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(p.getName());
            sb.append(": ");
            sb.append("'");
            sb.append(Strings.limit(p.writeToSource(this), 50));
            sb.append("'");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Returns a formatted representation of the entity containing all fields.
     *
     * @return a string representation of the entity containing all fields
     */
    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n\tid: \"").append(id).append("\",\n\tversion: ").append(version).append(",\n\tsource: {\n");
        Iterator<Property> iterator = index.getDescriptor(getClass()).getProperties().iterator();
        while (iterator.hasNext()) {
            Property property = iterator.next();
            sb.append("\t\t");
            sb.append(property.getName());
            sb.append(": ");
            sb.append("\"");
            sb.append(property.writeToSource(this));
            sb.append("\"");
            if (iterator.hasNext()) {
                sb.append(", ");
            }
            sb.append("\n");
        }
        sb.append("\t}\n}");
        return sb.toString();
    }

    @Override
    public String toString() {
        return toDebugString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (isNew()) {
            return false;
        }
        if (!(obj instanceof Entity)) {
            return false;
        }
        return getId().equals(((Entity) obj).getId());
    }

    @Override
    public int hashCode() {
        if (isNew()) {
            return super.hashCode();
        }
        return getId().hashCode();
    }

    /**
     * Invoked after an entity is saved into the database.
     * <p>
     * This method is not intended to be overridden. Override {@link #onAfterSave()} or {@link #internalOnAfterSave()}.
     */
    protected final void afterSave() {
        executeSaveOnForeignKeys();
        internalOnAfterSave();
        onAfterSave();
    }

    /**
     * Skips the foreign key updates once this entity is updated.
     * <p>
     * This can be used to break cyclic dependencies
     */
    public void skipForeignKeys() {
        skipForeignKeys = true;
    }

    /**
     * Executes the {@link sirius.search.ForeignKey#onSave(Entity)} handlers on all foreign keys...
     */
    protected void executeSaveOnForeignKeys() {
        if (skipForeignKeys) {
            return;
        }
        if (!isNew()) {
            for (ForeignKey fk : index.getDescriptor(getClass()).remoteForeignKeys) {
                fk.onSave(this);
            }
        }
    }

    /**
     * Intended for classes providing additional after save handlers.
     * Will be invoked after the entity was persisted.
     * <p>
     * This method MUST call {@code super.internalOnAfterSave} to ensure that all save handlers are called. This
     * is
     * intended to be overridden by framework classes. Application classes should simply override
     * {@code onAfterSave()}.
     */
    protected void internalOnAfterSave() {
    }

    /**
     * Intended for classes providing additional after save handlers.
     * Will be invoked after the entity was persisted.
     * <p>
     * This method SHOULD call {@code super.onAfterSave} to ensure that all save handlers are called. However,
     * frameworks should rely on internalOnAfterSave, which should not be overridden by application classes.
     */
    protected void onAfterSave() {
    }

    /**
     * Loads the given list of values from a form submit in the given {@link WebContext}.
     *
     * @param ctx              the context which contains the data of the submitted form.
     * @param propertiesToRead the list of properties to read. This is used to have fine control over which values
     *                         are actually loaded from the form and which aren't.
     * @return a map of changed properties, containing the old and new value for each given property
     */
    public Map<String, Tuple<Object, Object>> load(WebContext ctx, String... propertiesToRead) {
        Map<String, Tuple<Object, Object>> changeList = Maps.newTreeMap();
        Set<String> allowedProperties = Sets.newTreeSet(Arrays.asList(propertiesToRead));
        for (Property p : index.getDescriptor(getClass()).getProperties()) {
            if (allowedProperties.contains(p.getName())) {
                Object oldValue = obtainModificationProtectedValue(p);
                p.readFromRequest(this, ctx);
                Object newValue = p.writeToSource(this);
                if (!Objects.equal(newValue, oldValue)) {
                    changeList.put(p.getName(), Tuple.create(oldValue, newValue));
                }
            }
        }

        return changeList;
    }

    private Object obtainModificationProtectedValue(Property p) {
        Object oldValue = p.writeToSource(this);
        return createModificationProtectedValue(oldValue);
    }

    private Object createModificationProtectedValue(Object value) {
        Object protectedValue = value;
        if (value instanceof List<?>) {
            protectedValue = new ArrayList<>((List<?>) value);
        }
        if (value instanceof Map<?, ?>) {
            protectedValue = new HashMap<>((Map<?, ?>) value);
        }
        return protectedValue;
    }

    /**
     * Runs all checks to determine if the entity is consistent and can be saved into the database.
     */
    public void check() {
        beforeSaveChecks();
        performSaveChecks();
    }

    /**
     * Invoked before an entity is saved into the database.
     * <p>
     * This method is not intended to be overridden. Override {@link #onSave()} or {@link #internalOnSave()}.
     */
    protected final void beforeSave() {
        check();
        internalOnSave();
        onSave();
    }

    /**
     * Intended for classes providing additional on save handlers. Will be invoked before the entity will be saved,
     * but after it has been validated.
     * <p>
     * This method MUST call {@code super.internalOnSave} to ensure that all save handlers are called. This is
     * intended to be overridden by framework classes. Application classes should simply override
     * {@code onSave()}.
     */
    protected void internalOnSave() {
    }

    /**
     * Intended for classes providing on save handlers. Will be invoked before the entity will be saved,
     * but after it has been validated.
     * <p>
     * This method SHOULD call {@code super.onSave} to ensure that all save handlers are called. However,
     * frameworks should rely on internalOnSave, which should not be overridden by application classes.
     */
    protected void onSave() {
    }

    /**
     * Enables tracking of source field (which contain the original state of the database before the entity was
     * changed.
     * <p>
     * This will be set by @{@link IndexAccess#find(Class, String)}.
     */
    protected void initSourceTracing() {
        source = Maps.newTreeMap();
    }

    /**
     * Sets a source field when reading an entity from elasticsearch.
     * <p>
     * This is used by {@link Property#readFromSource(Entity, Object)}.
     *
     * @param name name of the field
     * @param val  persisted value of the field.
     */
    public void setSource(String name, Object val) {
        if (source != null) {
            source.put(name, createModificationProtectedValue(val));
        }
    }

    /**
     * Checks if the given field has changed (since the entity was loaded from the database).
     *
     * @param field the field to check
     * @param value the current value which is to be compared
     * @return <tt>true</tt> if the value loaded from the database is not equal to the given value, <tt>false</tt>
     * otherwise.
     */
    public boolean isChanged(String field, Object value) {
        return source != null && !Objects.equal(value, source.get(field));
    }

    /**
     * Returns the name of the index which is used to store the entities.
     *
     * @return the name of the ElasticSearch index used to store the entities. Returns <tt>null</tt> to indicate that
     * the default index (given by the {@link sirius.search.annotations.Indexed} annotation should be used).
     */
    public String getIndex() {
        return null;
    }

    /**
     * Generates an unique ID used to store new objects of this type.
     * <p>
     * By default three types of IDs are supported:
     * <ul>
     * <li><b>ELASTICSEARCH</b>: Let elasticsearch generate the IDs.
     * Works 100% but contains characters like '-' or '_'</li>
     * <li><b>SEQUENCE</b>: Use a sequential generator to compute a new number.
     * Note that this implies a certain overhead to increment a cluster wide sequence.</li>
     * <li><b>BASE32HEX</b>: Use the internal generator (16 byte random data) represented as BASE32HEX
     * encoded string. This is the default setting.</li>
     * </ul>
     * <p>
     * Note that the type of generation can be controlled by overriding {@link #getIdGeneratorType()}.
     *
     * @return a unique ID used for new objects or <tt>null</tt> to let elasticsearch create one.
     */
    public String computePossibleId() {
        switch (getIdGeneratorType()) {
            case SEQUENCE:
                return String.valueOf(sequenceGenerator.getNextId(getClass().getSimpleName().toLowerCase()));
            case BASE32HEX:
                byte[] rndBytes = new byte[16];
                ThreadLocalRandom.current().nextBytes(rndBytes);
                return BaseEncoding.base32Hex().encode(rndBytes).replace("=", "");
            default:
                return null;
        }
    }

    /**
     * Default types of id generators supported. {@link #computePossibleId()}.
     */
    public enum IdGeneratorType {
        ELASTICSEARCH, SEQUENCE, BASE32HEX
    }

    /**
     * Used by the default implementation of {@link #computePossibleId()} to determine which kind of ID to generate.
     *
     * @return the preferred way of generating IDs.
     */
    protected IdGeneratorType getIdGeneratorType() {
        return IdGeneratorType.BASE32HEX;
    }
}
