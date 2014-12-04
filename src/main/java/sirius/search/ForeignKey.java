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
import sirius.kernel.async.Async;
import sirius.kernel.commons.Strings;
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
 * </p>
 * <p>
 * Foreign keys are automatically created by {@link sirius.search.Schema#linkSchema()} based on {@link RefType} annotations.
 * </p>
 * <p>
 * Using {@link sirius.search.annotations.RefField} along a <tt>RefType</tt> annotation permits to have copies of fields,
 * like the name of a parent object, which is automatically updated once it changes.
 * </p>
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
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
        public Reference(Property field, String remoteField) {
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
                                .withNLSKey(Strings.isFilled(refType.onDeleteErrorMsg()) ? refType.onDeleteErrorMsg() : "ForeignKey.restricted")
                                .handle();
            }
        }
    }

    /**
     * Handles a delete of the given entity.
     *
     * @param entity the entity (which must be of type {@link #getReferencedClass()}) which is going to be deleted
     */
    @SuppressWarnings("unchecked")
    public void onDelete(final Entity entity) {
        if (refType.cascade() == Cascade.IGNORE) {
            return;
        } else if (refType.cascade() == Cascade.REJECT) {
            if (Index.select(getLocalClass())
                     .eq(getName(), entity.getId())
                     .autoRoute(field.getName(), entity.getId())
                     .exists()) {
                throw Exceptions.createHandled()
                                .withNLSKey(Strings.isFilled(refType.onDeleteErrorMsg()) ? refType.onDeleteErrorMsg() : "ForeignKey.restricted")
                                .handle();
            }
        } else if (refType.cascade() == Cascade.SET_NULL) {
            Async.executor(Index.ASYNC_CATEGORY_INDEX_INTEGRITY).fork(() -> {
                try {
                    Index.select((Class<Entity>) getLocalClass())
                         .eq(getName(), entity.getId())
                         .autoRoute(field.getName(), entity.getId())
                         .iterate(row -> {
                             updateReferencedFields(entity, (Entity) row);
                             return true;
                         });
                } catch (Throwable e) {
                    Exceptions.handle(Index.LOG, e);
                }
            }).execute();
        } else if (refType.cascade() == Cascade.CASCADE) {
            Async.executor(Index.ASYNC_CATEGORY_INDEX_INTEGRITY).fork(() -> {
                try {
                    Index.select((Class<Entity>) getLocalClass())
                         .eq(getName(), entity.getId())
                         .autoRoute(field.getName(), entity.getId())
                         .iterate(row -> {
                             try {
                                 Index.delete((Entity) row, true);
                             } catch (Throwable e) {
                                 Exceptions.handle(Index.LOG, e);
                             }
                             return true;
                         });
                } catch (Throwable e) {
                    Exceptions.handle(Index.LOG, e);
                }
            }).execute();
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
            Async.executor(Index.ASYNC_CATEGORY_INDEX_INTEGRITY).fork(() -> {
                try {
                    Index.select((Class<Entity>) getLocalClass())
                         .eq(getName(), entity.getId())
                         .autoRoute(field.getName(), entity.getId())
                         .iterate(row -> {
                             updateReferencedFields(entity, (Entity) row);
                             return true;
                         });
                } catch (Throwable e) {
                    Exceptions.handle(Index.LOG, e);
                }
            }).execute();
        }
    }

    private void updateReferencedFields(Entity parent, Entity child) {
        StringBuilder sb = new StringBuilder();
        UpdateRequestBuilder urb = Index.getClient()
                                        .prepareUpdate()
                                        .setIndex(Index.getIndex(getLocalClass()))
                                        .setType(getLocalType())
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
        for (Reference ref : references) {
            sb.append("ctx._source.");
            sb.append(ref.getLocalProperty().getName());
            sb.append("=");
            sb.append(ref.getLocalProperty().getName());
            sb.append(";");
            urb.addScriptParam(ref.getLocalProperty().getName(), ref.getRemoteProperty().writeToSource(parent));
        }
        if (Index.LOG.isFINE()) {
            Index.LOG.FINE("UPDATE: %s.%s: %s", Index.getIndex(getLocalClass()), getLocalType(), sb.toString());
        }
        try {
            urb.setScript(sb.toString()).execute().actionGet();
            if (Index.LOG.isFINE()) {
                Index.LOG.FINE("UPDATE: %s.%s: SUCCEEDED", Index.getIndex(getLocalClass()), getLocalType());
            }
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
