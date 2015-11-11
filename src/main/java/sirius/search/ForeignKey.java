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
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.elasticsearch.script.ScriptService;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.search.annotations.RefType;
import sirius.search.properties.Property;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Represents a soft foreign key.
 * <p>
 * In contrast to "real" foreign keys, these only try to achieve eventual consistency. Sometimes it is even preferable
 * to accept inconsistent datasets (like child objects without parents) for performance reasons.
 * <p>
 * Foreign keys are automatically created by {@link sirius.search.Schema#linkSchema()} based on {@link RefType}
 * annotations.
 * <p>
 * Using {@link sirius.search.annotations.RefField} along a <tt>RefType</tt> annotation permits to have copies of
 * fields,
 * like the name of a parent object, which is automatically updated once it changes.
 */
public class ForeignKey {
    private final RefType refType;
    private String otherType;
    private String localType;
    private Field field;
    private List<Reference> references = Lists.newArrayList();
    private Class<? extends Entity> localClass;

    /**
     * Contains all metadata to take care of a {@link sirius.search.annotations.RefField}
     */
    class Reference {
        private final Property localProperty;
        private final String remoteField;
        Property remoteProperty;

        /**
         * Creates a new reference
         *
         * @param field       the field which contains the local copy
         * @param remoteField the remote field which defines the source of the value
         */
        Reference(Property field, String remoteField) {
            this.localProperty = field;
            this.remoteField = remoteField;
        }

        /**
         * Returns the local property which contains the copy of the value.
         *
         * @return the local property which contains the copy of the value
         */
        public Property getLocalProperty() {
            return localProperty;
        }

        /**
         * Returns the remote field which determines the source of the value.
         *
         * @return the remote field determining the source of the value
         */
        public String getRemoteField() {
            return remoteField;
        }

        /**
         * Returns the remote property which is the origin of the value.
         *
         * @return the remote property which is the origin of the value
         */
        public Property getRemoteProperty() {
            if (remoteProperty == null) {
                for (Property p : Index.getDescriptor(getReferencedClass()).getProperties()) {
                    if (Objects.equal(remoteField, p.getName())) {
                        remoteProperty = p;
                        break;
                    }
                }
                if (remoteProperty == null) {
                    Index.LOG.WARN("Unknown foreign key reference %s from %s in type %s",
                                   remoteField,
                                   localProperty.getName(),
                                   field.getDeclaringClass().getSimpleName());
                }
            }
            return remoteProperty;
        }
    }

    /**
     * Creates a new foreign key for the underlying java Field
     *
     * @param field      the field which originates the foreign key
     * @param localClass contains the entity class to which this foreign key belongs. This is not necessarily
     *                   the declaring class of <tt>field</tt> as this might be an abstract super class
     */
    public ForeignKey(Field field, Class<? extends Entity> localClass) {
        this.localClass = localClass;
        this.refType = field.getAnnotation(RefType.class);
        this.field = field;
    }

    /**
     * Returns the name of the underlying field.
     *
     * @return the name of the underlying field
     */
    public String getName() {
        return field.getName();
    }

    /**
     * Adds a field reference to go along the foreign key.
     *
     * @param field       the local field which contains the copy
     * @param remoteField the name of the remote field, which contains the original value
     */
    public void addReference(Property field, String remoteField) {
        references.add(new Reference(field, remoteField));
    }

    /**
     * Returns the class of the referenced entity.
     *
     * @return the class of the referenced entity
     */
    public Class<? extends Entity> getReferencedClass() {
        return refType.type();
    }

    /**
     * Returns the class of the entity containing the foreign key (referencing field)
     *
     * @return the class of the entity where the foreign key was declared
     */
    public Class<? extends Entity> getLocalClass() {
        return localClass;
    }

    /**
     * Checks if the given entity can be consistently deleted.
     *
     * @param entity the entity (which must be of type {@link #getReferencedClass()}) which is the be deleted.
     */
    public void checkDelete(final Entity entity) {
        if (refType.cascade() == Cascade.REJECT) {
            if (Index.select(getLocalClass())
                     .eq(field.getName(), entity.getId())
                     .autoRoute(field.getName(), entity.getId())
                     .exists()) {
                throw Exceptions.createHandled()
                                .withNLSKey(Strings.isFilled(refType.onDeleteErrorMsg()) ?
                                            refType.onDeleteErrorMsg() :
                                            "ForeignKey.restricted")
                                .handle();
            }
        }
    }

    @Part
    private static Tasks tasks;

    /**
     * Handles a delete of the given entity.
     *
     * @param entity the entity (which must be of type {@link #getReferencedClass()}) which is going to be deleted
     */
    public void onDelete(final Entity entity) {
        if (refType.cascade() == Cascade.REJECT) {
            rejectDeleteIfNecessary(entity);
        } else if (refType.cascade() == Cascade.SET_NULL) {
            tasks.executor(Index.ASYNC_CATEGORY_INDEX_INTEGRITY).fork(() -> setNull(entity));
        } else if (refType.cascade() == Cascade.CASCADE) {
            tasks.executor(Index.ASYNC_CATEGORY_INDEX_INTEGRITY).fork(() -> cascadeDelete(entity));
        }
    }

    @SuppressWarnings("unchecked")
    private void cascadeDelete(Entity entity) {
        try {
            Index.select((Class<Entity>) getLocalClass())
                 .eq(getName(), entity.getId())
                 .autoRoute(field.getName(), entity.getId())
                 .iterateAll(row -> {
                     try {
                         Index.delete(row, true);
                     } catch (Throwable e) {
                         Exceptions.handle(Index.LOG, e);
                     }
                 });
        } catch (Throwable e) {
            Exceptions.handle(Index.LOG, e);
        }
    }

    private void rejectDeleteIfNecessary(Entity entity) {
        if (Index.select(getLocalClass())
                 .eq(getName(), entity.getId())
                 .autoRoute(field.getName(), entity.getId())
                 .exists()) {
            throw Exceptions.createHandled()
                            .withNLSKey(Strings.isFilled(refType.onDeleteErrorMsg()) ?
                                        refType.onDeleteErrorMsg() :
                                        "ForeignKey.restricted")
                            .handle();
        }
    }

    @SuppressWarnings("unchecked")
    private void setNull(Entity entity) {
        try {
            Index.select((Class<Entity>) getLocalClass())
                 .eq(getName(), entity.getId())
                 .autoRoute(field.getName(), entity.getId())
                 .iterate(row -> {
                     try {
                         field.set(row, null);
                         updateReferencedFields(entity, row);
                     } catch (Throwable e) {
                         Exceptions.handle(Index.LOG, e);
                     }
                     return true;
                 });
        } catch (Throwable e) {
            Exceptions.handle(Index.LOG, e);
        }
    }

    /**
     * Handles a save of the given entity.
     *
     * @param entity the entity (which must be of type {@link #getReferencedClass()}) which is going to be saved
     */
    @SuppressWarnings("unchecked")
    public void onSave(final Entity entity) {
        if (references.isEmpty()) {
            return;
        }
        boolean referenceChanged = false;
        for (Reference ref : references) {
            try {
                if (entity.isChanged(ref.getRemoteProperty().getName(),
                                     ref.getRemoteProperty().writeToSource(entity))) {
                    referenceChanged = true;
                    break;
                }
            } catch (Exception e) {
                Exceptions.handle(e);
                // Just to be sure...
                referenceChanged = true;
            }
        }
        if (referenceChanged) {
            tasks.executor(Index.ASYNC_CATEGORY_INDEX_INTEGRITY).fork(() -> updateReferencedFields(entity));
        }
    }

    @SuppressWarnings("unchecked")
    private void updateReferencedFields(Entity entity) {
        try {
            Index.select((Class<Entity>) getLocalClass())
                 .eq(getName(), entity.getId())
                 .autoRoute(field.getName(), entity.getId())
                 .iterate(row -> {
                     updateReferencedFields(entity, row);
                     return true;
                 });
        } catch (Throwable e) {
            Exceptions.handle(Index.LOG, e);
        }
    }

    /*
     * Tries to update all referenced fields. If we cannot update the entity with
     * three retries, we give up and report a warning...
     */
    private void updateReferencedFields(Entity parent, Entity child) {
        try {
            UpdateRequestBuilder urb = Index.getClient()
                                            .prepareUpdate()
                                            .setIndex(Index.getIndex(getLocalClass()))
                                            .setType(getLocalType())
                                            .setRetryOnConflict(3)
                                            .setId(child.getId());
            EntityDescriptor descriptor = Index.getDescriptor(getLocalClass());
            if (descriptor.hasRouting()) {
                Object routingKey = descriptor.getProperty(descriptor.getRouting()).writeToSource(child);
                if (Strings.isEmpty(routingKey)) {
                    Index.LOG.WARN("Updating an entity of type %s (%s) without routing information!",
                                   child.getClass().getName(),
                                   child.getId());
                } else {
                    urb.setRouting(String.valueOf(routingKey));
                }
            }
            StringBuilder sb = new StringBuilder();
            for (Reference ref : references) {
                sb.append("ctx._source.");
                sb.append(ref.getLocalProperty().getName());
                sb.append("=");
                sb.append(ref.getLocalProperty().getName());
                sb.append(";");
                urb.addScriptParam(ref.getLocalProperty().getName(), ref.getRemoteProperty().writeToSource(parent));
            }
            urb.setScript(sb.toString(), ScriptService.ScriptType.INLINE);
            if (Index.LOG.isFINE()) {
                Index.LOG.FINE("UPDATE: %s.%s: %s", Index.getIndex(getLocalClass()), getLocalType(), sb.toString());
            }
            urb.execute().actionGet();
            if (Index.LOG.isFINE()) {
                Index.LOG.FINE("UPDATE: %s.%s: SUCCEEDED", Index.getIndex(getLocalClass()), getLocalType());
            }
            Index.traceChange(child);
        } catch (VersionConflictEngineException t) {
            // Ran out of retries -> report as warning
            Index.LOG.WARN("UPDATE: %s.%s: FAILED DUE TO CONCURRENT UPDATE: %s",
                           Index.getIndex(getLocalClass()),
                           getLocalType(),
                           t.getMessage());
            Index.reportClash(child);
        } catch (DocumentMissingException t) {
            Exceptions.ignore(t);
        } catch (Throwable t) {
            if (Index.LOG.isFINE()) {
                Index.LOG.FINE("UPDATE: %s.%s: FAILED: %s",
                               Index.getIndex(getLocalClass()),
                               getLocalType(),
                               t.getMessage());
            }
            throw Exceptions.handle(Index.LOG, t);
        }
    }

    /**
     * Returns the type name of the locally referenced entity.
     *
     * @return the type name of the entity class which declared this foreign key
     */
    public String getLocalType() {
        if (localType == null) {
            localType = Index.getDescriptor(getLocalClass()).getType();
        }
        return localType;
    }

    /**
     * Returns the type name of the remote referenced entity.
     *
     * @return the type name of the entity class which is referenced by this foreign key
     */
    public String getOtherType() {
        if (otherType == null) {
            otherType = Index.getDescriptor(getReferencedClass()).getType();
        }
        return otherType;
    }
}
