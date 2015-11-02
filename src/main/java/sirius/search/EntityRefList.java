/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import com.google.common.cache.Cache;
import com.google.common.collect.Lists;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Used as field type which references a list of other entities.
 * <p>
 * This permits elegant lazy loading, as only the IDs are eagerly loaded and stored into the database. The objects
 * itself are only loaded on demand.
 *
 * @param <E> the type of the referenced entities
 */
public class EntityRefList<E extends Entity> {

    private List<E> values;
    private boolean valueFromCache;
    private Class<E> clazz;
    private List<String> ids = Lists.newArrayList();

    /**
     * Creates a new reference.
     * <p>
     * Fields of this type don't need to be initialized, as this is done by the
     * {@link sirius.search.properties.EntityProperty}.
     *
     * @param ref the type of the referenced entity
     */
    public EntityRefList(Class<E> ref) {
        this.clazz = ref;
    }

    /**
     * Determines if the entity value is present.
     *
     * @return <tt>true</tt> if the value was already loaded (or set). <tt>false</tt> otherwise.
     */
    public boolean isValueLoaded() {
        return values != null || ids.isEmpty();
    }

    /**
     * Returns the entities represented by this reference as <b>unmodifyable list</b>.
     *
     * @return the values represented by this reference. Note that this list is not modifyable. Use {@link
     * #addValue(Entity)} or {@link #setIds(List)} to modify this list.
     */
    public List<E> getValues() {
        return getValuesWithRouting(null);
    }

    /**
     * Returns the entities represented by this reference as <b>unmodifyable list</b>.
     *
     * @param routing the routing info used to lookup the entities (might be <tt>null</tt> if no routing is required).
     * @return the values represented by this reference. Note that this list is not modifyable. Use {@link
     * #addValue(Entity)} or {@link #setIds(List)} to modify this list.
     */
    public List<E> getValuesWithRouting(String routing) {
        if (!isValueLoaded() || valueFromCache) {
            EntityDescriptor descriptor = Index.getDescriptor(clazz);
            List<E> result = Lists.newArrayList();
            for (String id : ids) {
                if (descriptor.hasRouting()) {
                    if (Strings.isFilled(routing)) {
                        result.add(Index.find(routing, clazz, id));
                    } else {
                        Exceptions.handle()
                                  .to(Index.LOG)
                                  .withSystemErrorMessage("Fetching an entity of type %s (%s) without routing! "
                                                          + "Using SELECT which might be slower!", clazz.getName(), id)
                                  .handle();
                        result.add(Index.select(clazz).eq(Index.ID_FIELD, id).queryFirst());
                    }
                } else {
                    if (Strings.isFilled(routing)) {
                        Exceptions.handle()
                                  .to(Index.LOG)
                                  .withSystemErrorMessage("Fetching an entity of type %s (%s) with routing "
                                                          + "(which is not required for this type)!",
                                                          clazz.getName(),
                                                          id)
                                  .handle();
                    }
                    result.add(Index.find(clazz, id));
                }
            }
            values = result.stream().filter(v -> v != null).collect(Collectors.toList());
            valueFromCache = false;
        }
        if (values == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * Returns the entities represented by this reference as <b>unmodifyable list</b>.
     * <p>
     * The framework is permitted to load the value from a given local cache.
     *
     * @param localCache the cache to used when looking up values
     * @return the values represented by this reference. Note that this list is not modifyable. Use {@link
     * #addValue(Entity)} or {@link #setIds(List)} to modify this list.
     */
    public List<E> getCachedValue(Cache<String, Object> localCache) {
        return getCachedValueWithRouting(null, localCache);
    }

    /**
     * Returns the entities represented by this reference as <b>unmodifyable list</b>.
     * <p>
     * The framework is permitted to load the value from a given local cache.
     *
     * @param routing    the routing info used to lookup the entities (might be <tt>null</tt> if no routing is
     *                   required).
     * @param localCache the cache to used when looking up values
     * @return the values represented by this reference. Note that this list is not modifyable. Use {@link
     * #addValue(Entity)} or {@link #setIds(List)} to modify this list.
     */
    public List<E> getCachedValueWithRouting(String routing, Cache<String, Object> localCache) {
        if (isValueLoaded()) {
            if (values == null) {
                return Collections.emptyList();
            }
            return values;
        }

        List<E> result = Lists.newArrayList();
        valueFromCache = false;
        for (String id : ids) {
            Tuple<E, Boolean> tuple = Index.fetch(routing, clazz, id, localCache);
            if (tuple.getFirst() != null) {
                result.add(tuple.getFirst());
                valueFromCache |= tuple.getSecond();
            }
        }
        values = result;
        return Collections.unmodifiableList(values);
    }

    /**
     * Returns the entities represented by this reference as <b>unmodifyable list</b>.
     * <p>
     * The framework is permitted to load the value from the global cache.
     *
     * @return the values represented by this reference. Note that this list is not modifyable. Use {@link
     * #addValue(Entity)} or {@link #setIds(List)} to modify this list.
     */
    public List<E> getCachedValue() {
        return getCachedValueWithRouting(null);
    }

    /**
     * Returns the entities represented by this reference as <b>unmodifyable list</b>.
     * <p>
     * The framework is permitted to load the value from the global cache.
     *
     * @param routing the routing info used to lookup the entities (might be <tt>null</tt> if no routing is required).
     * @return the values represented by this reference. Note that this list is not modifyable. Use {@link
     * #addValue(Entity)} or {@link #setIds(List)} to modify this list.
     */
    public List<E> getCachedValueWithRouting(String routing) {
        if (isValueLoaded()) {
            if (values == null) {
                return Collections.emptyList();
            }
            return values;
        }

        List<E> result = Lists.newArrayList();
        valueFromCache = false;
        for (String id : ids) {
            Tuple<E, Boolean> tuple = Index.fetch(routing, clazz, id);
            if (tuple.getFirst() != null) {
                result.add(tuple.getFirst());
                valueFromCache |= tuple.getSecond();
            }
        }
        values = result;
        return Collections.unmodifiableList(values);
    }

    /**
     * Adds the value to be represented by this reference.
     *
     * @param value the value to be stored
     */
    public void addValue(E value) {
        if (value != null) {
            if (ids.isEmpty()) {
                values = Lists.newArrayList();
            }
            this.ids.add(value.getId());
            if (values != null) {
                values.add(value);
            }
        }
    }

    /**
     * Determines if the list of referenced entities contains the given entity.
     *
     * @param value the value to check for
     * @return <tt>true</tt> if the value is non null and contained in the list of referenced entities
     */
    public boolean contains(@Nullable E value) {
        if (value == null) {
            return false;
        }
        return ids.contains(value.getId());
    }

    /**
     * Determines if the list of referenced entities contains the given entity (referenced via the given id).
     *
     * @param id the id of the entity to check for
     * @return <tt>true</tt> if the id is non null and the id of an entity contained in the list of referenced entities
     */
    public boolean containsId(String id) {
        if (Strings.isEmpty(id)) {
            return false;
        }
        return ids.contains(id);
    }

    /**
     * Returns the IDs of the represented values.
     * <p>
     * This can always be fetched without a DB lookup.
     *
     * @return the IDs of the represented values
     */
    public List<String> getIds() {
        return ids;
    }

    /**
     * Sets the IDs of the represented values.
     *
     * @param ids the ids of the represented values
     */
    public void setIds(List<String> ids) {
        this.ids.clear();
        if (ids != null) {
            for (String id : ids) {
                if (Strings.isFilled(id)) {
                    this.ids.add(id);
                }
            }
        }
        this.values = null;
        this.valueFromCache = false;
    }

    /**
     * Removes all references from the list and clears the locally cached values
     */
    public void clear() {
        this.ids.clear();

        this.values = null;
        this.valueFromCache = false;
    }

    @Override
    public String toString() {
        if (!getIds().isEmpty()) {
            return clazz.getSimpleName() + ": " + ids;
        } else {
            return clazz.getSimpleName() + ": <empty>";
        }
    }
}
