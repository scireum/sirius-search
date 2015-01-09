/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import com.google.common.cache.Cache;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.health.Exceptions;

/**
 * Used as field type which references other entities.
 * <p>
 * This permits elegant lazy loading, as only the ID is eagerly loaded and stored into the database. The object
 * itself is only loaded on demand.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
public class EntityRef<E extends Entity> {
    private E value;
    private boolean valueFromCache;
    private Class<E> clazz;
    private String id;
    private boolean dirty = false;

    /**
     * Creates a new reference.
     * <p>
     * Fields of this type don't need to be initialized, as this is done by the
     * {@link sirius.search.properties.EntityProperty}.
     *
     * @param ref the type of the referenced entity
     */
    public EntityRef(Class<E> ref) {
        this.clazz = ref;
    }

    /**
     * Determines if the entity value is present.
     *
     * @return <tt>true</tt> if the value was already loaded (or set). <tt>false</tt> otherwise.
     */
    public boolean isValueLoaded() {
        return value != null || id == null;
    }

    /**
     * Returns the entity value represented by this reference.
     *
     * @return the value represented by this reference
     */
    public E getValue() {
        return getValue(null);
    }

    /**
     * Returns the entity value represented by this reference.
     *
     * @param routing the routing info used to lookup the entity (might be <tt>null</tt> if no routing is required).
     * @return the value represented by this reference
     */
    public E getValue(String routing) {
        if (!isValueLoaded() || valueFromCache) {
            EntityDescriptor descriptor = Index.getDescriptor(clazz);
            if (descriptor.hasRouting()) {
                if (Strings.isFilled(routing)) {
                    value = Index.find(routing, clazz, id);
                } else {
                    Exceptions.handle()
                              .to(Index.LOG)
                              .withSystemErrorMessage(
                                      "Fetching an entity of type %s (%s) without routing! Using SELECT which might be slower!",
                                      clazz.getName(),
                                      id)
                              .handle();
                    value = Index.select(clazz).eq(Index.ID_FIELD, id).queryFirst();
                }
            } else {
                if (Strings.isFilled(routing)) {
                    Exceptions.handle()
                              .to(Index.LOG)
                              .withSystemErrorMessage(
                                      "Fetching an entity of type %s (%s) with routing (which is not required for this type)!",
                                      clazz.getName(),
                                      id)
                              .handle();
                }
                value = Index.find(clazz, id);
            }
            valueFromCache = false;
            clearDirty();
        }
        return value;
    }

    /**
     * Returns the entity value represented by this reference.
     * <p>
     * The framework is permitted to load the value from the given local cache.
     *
     * @param localCache the cache to used when looking up values
     * @return the value represented by this reference
     */
    public E getCachedValue(Cache<String, Object> localCache) {
        return getCachedValueWithRouting(null, localCache);
    }

    /**
     * Returns the entity value represented by this reference.
     * <p>
     * The framework is permitted to load the value from the given local cache.
     *
     * @param routing    the routing info used to lookup the entity (might be <tt>null</tt> if no routing is required).
     * @param localCache the cache to used when looking up values
     * @return the value represented by this reference
     */
    public E getCachedValueWithRouting(String routing, Cache<String, Object> localCache) {
        if (isValueLoaded()) {
            return value;
        }

        Tuple<E, Boolean> tuple = Index.fetch(routing, clazz, id, localCache);
        value = tuple.getFirst();
        valueFromCache = tuple.getSecond();
        markDirty();

        return value;
    }

    /**
     * Returns the entity value represented by this reference.
     * <p>
     * The framework is permitted to load the value from the global cache.
     *
     * @return the value represented by this reference
     */
    public E getCachedValue() {
        return getCachedValueWithRouting(null);
    }

    /**
     * Returns the entity value represented by this reference.
     * <p>
     * The framework is permitted to load the value from the global cache.
     *
     * @param routing the routing info used to lookup the entity (might be <tt>null</tt> if no routing is required).
     * @return the value represented by this reference
     */
    public E getCachedValueWithRouting(String routing) {
        if (isValueLoaded()) {
            return value;
        }

        Tuple<E, Boolean> tuple = Index.fetch(routing, clazz, id);
        value = tuple.getFirst();
        valueFromCache = tuple.getSecond();
        markDirty();

        return value;
    }

    /**
     * Sets the value to be represented by this reference.
     *
     * @param value the value to be stored
     */
    public void setValue(E value) {
        this.value = value;
        this.valueFromCache = false;
        this.id = value == null ? null : value.id;
        clearDirty();
    }

    /**
     * Clears the dirty flag for this reference.
     */
    public void clearDirty() {
        this.dirty = false;
    }

    /**
     * Sets the dirty flag for this reference.
     */
    protected void markDirty() {
        this.dirty = true;
    }

    /**
     * Determines whether the id stored by this entity differs from state stored in the database.
     * <p>
     * Used to determine if RefField referencing this entity must be updated. This is also set to <tt>true</tt>
     * if the current value was fetched from a cache instead from the database.
     *
     * @return <tt>true</tt> if the id has changed or <tt>false</tt> otherwise
     */
    protected boolean isDirty() {
        return dirty;
    }

    /**
     * Returns the ID of the represented value.
     * <p>
     * This can always be fetched without a DB lookup.
     *
     * @return the ID of the represented value
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the ID of the represented value.
     * <p>
     * If the object is available, consider using {@link #setValue(Entity)} as it also stores the value in a
     * temporary buffer which improves calls to {@link #getValue()} (which might happen in onSave handlers).
     *
     * @param id the id of the represented value
     */
    public void setId(String id) {
        this.id = id;
        this.value = null;
        this.valueFromCache = false;
        markDirty();
    }

    /**
     * Determines if an entity is referenced by this field or not.
     * <p>
     * This is not be confused with {@link #isValueLoaded()} which indicates if the value has already been
     * loaded from the database.
     *
     * @return <tt>true</tt> if an entity is referenced, <tt>false</tt> otherwise
     */
    public boolean isFilled() {
        return Strings.isFilled(id);
    }

    /**
     * Determines if the id of the referenced entity equals the given id.
     *
     * @param id the id to check
     * @return <tt>true</tt> if the id of the referenced entity equals the given id, <tt>false</tt> otherwise
     */
    public boolean containsId(String id) {
        return Strings.areEqual(this.id, id);
    }

    /**
     * Determines if the id of the referenced entity is not equals to the given id.
     *
     * @param id the id to check
     * @return <tt>true</tt> if the id of the referenced entity is not equal the given id, <tt>false</tt> otherwise
     */
    public boolean isNotThisId(String id) {
        return !containsId(id);
    }

    @Override
    public String toString() {
        if (isFilled()) {
            return clazz.getSimpleName() + ": " + id;
        } else {
            return clazz.getSimpleName() + ": <empty>";
        }
    }
}
