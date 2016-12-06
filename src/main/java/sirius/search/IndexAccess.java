/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.HandledException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Provides a wrapper for {@link Index} which can be injected using a {@link sirius.kernel.di.std.Part} annotation.
 * <p>
 * Using this wrapper instead of the static methods permits to mock the index access.
 */
@Register(classes = IndexAccess.class)
public class IndexAccess {

    /**
     * Fetches the entity of given type with the given id.
     * <p>
     * May use a given cache to load the entity.
     *
     * @param routing the routing info used to lookup the entity (might be <tt>null</tt> if no routing is required).
     * @param type    the type of the desired entity
     * @param id      the id of the desired entity
     * @param cache   the cache to resolve the entity.
     * @param <E>     the type of entities to fetch
     * @return a tuple containing the resolved entity (or <tt>null</tt> if not found) and a flag which indicates if the
     * value was loaded from cache (<tt>true</tt>) or not.
     */
    @Nonnull
    public <E extends Entity> Tuple<E, Boolean> fetch(@Nullable String routing,
                                                      @Nonnull Class<E> type,
                                                      @Nullable String id,
                                                      @Nonnull com.google.common.cache.Cache<String, Object> cache) {
        return Index.fetch(routing, type, id, cache);
    }

    /**
     * Fetches the entity of given type with the given id.
     * <p>
     * May use a given cache to load the entity.
     *
     * @param routing the routing info used to lookup the entity (might be <tt>null</tt> if no routing is required).
     * @param type    the type of the desired entity
     * @param id      the id of the desired entity
     * @param cache   the cache to resolve the entity.
     * @param <E>     the type of entities to fetch
     * @return the resolved entity (or <tt>null</tt> if not found)
     */
    @Nullable
    public <E extends Entity> E fetchFromCache(@Nullable String routing,
                                               @Nonnull Class<E> type,
                                               @Nullable String id,
                                               @Nonnull com.google.common.cache.Cache<String, Object> cache) {
        return Index.fetch(routing, type, id, cache).getFirst();
    }

    /**
     * Fetches the entity of given type with the given id.
     * <p>
     * May use a global cache to load the entity.
     *
     * @param routing the routing info used to lookup the entity (might be <tt>null</tt> if no routing is required).
     * @param type    the type of the desired entity
     * @param id      the id of the desired entity
     * @param <E>     the type of entities to fetch
     * @return a tuple containing the resolved entity (or <tt>null</tt> if not found) and a flag which indicates if the
     * value was loaded from cache (<tt>true</tt>) or not.
     */
    @Nonnull
    public <E extends Entity> Tuple<E, Boolean> fetch(@Nullable String routing,
                                                      @Nonnull Class<E> type,
                                                      @Nullable String id) {
        return Index.fetch(routing, type, id);
    }

    /**
     * Fetches the entity of given type with the given id.
     * <p>
     * May use a global cache to load the entity.
     *
     * @param routing the routing info used to lookup the entity (might be <tt>null</tt> if no routing is required).
     * @param type    the type of the desired entity
     * @param id      the id of the desired entity
     * @param <E>     the type of entities to fetch
     * @return the resolved entity (or <tt>null</tt> if not found)
     */
    @Nullable
    public <E extends Entity> E fetchFromCache(@Nullable String routing, @Nonnull Class<E> type, @Nullable String id) {
        return Index.fetch(routing, type, id).getFirst();
    }

    /**
     * Adds an action to the delay line, which ensures that it is at least delayed for one second
     *
     * @param cmd to command to be delayed
     */
    public void callAfterUpdate(final Runnable cmd) {
        Index.callAfterUpdate(cmd);
    }

    /**
     * Manually blocks the current thread for one second, to make a write visible in ES.
     * <p>
     * Consider using {@link #callAfterUpdate(Runnable)} which does not block system resources. Only use this method
     * is absolutely necessary.
     */
    public void blockThreadForUpdate() {
        Index.blockThreadForUpdate();
    }

    /**
     * Handles the given unit of work while restarting it if an optimistic lock error occurs.
     *
     * @param uow the unit of work to handle.
     * @throws sirius.kernel.health.HandledException if either any other exception occurs, or if all three attempts
     *                                               fail
     *                                               with an optimistic lock error.
     */
    public void retry(UnitOfWork uow) {
        Index.retry(uow);
    }

    /**
     * Creates this entity by storing an initial copy in the database.
     *
     * @param entity the entity to be stored in the database
     * @param <E>    the type of the entity to create
     * @return the stored entity (with a filled ID etc.)
     */
    public <E extends Entity> E create(E entity) {
        return Index.create(entity);
    }

    /**
     * Tries to update the given entity in the database.
     * <p>
     * If the same entity was modified in the database already, an
     * <tt>OptimisticLockException</tt> will be thrown
     *
     * @param entity the entity to save
     * @param <E>    the type of the entity to update
     * @return the saved entity
     * @throws sirius.search.OptimisticLockException if the entity was modified in the database and those changes where
     *                                               not reflected
     *                                               by the entity to be saved
     */
    public <E extends Entity> E tryUpdate(E entity) throws OptimisticLockException {
        return Index.tryUpdate(entity);
    }

    /**
     * Tries to apply the given changes and to save the resulting entity.
     * <p>
     * Tries to perform the given modifications and then to update the entity. If an optimistic lock error occurs,
     * the entity is refreshed and the modifications are re-executed along with another update.
     *
     * @param entity          the entity to update
     * @param preSaveModifier the changes to perform on the entity
     * @throws HandledException if either any other exception occurs, or if all three attempts fail with an optimistic
     *                          lock error.
     */
    public <E extends Entity> void retryUpdate(E entity, Callback<E> preSaveModifier) {
        Index.retryUpdate(entity, preSaveModifier);
    }

    /**
     * Updates the entity in the database.
     * <p>
     * If the entity was modified in the database and those changes where not reflected
     * by the entity to be saved, this operation will fail.
     *
     * @param entity the entity to be written into the DB
     * @param <E>    the type of the entity to update
     * @return the updated entity
     */
    public <E extends Entity> E update(E entity) {
        return Index.update(entity);
    }

    /**
     * Updates the entity in the database.
     * <p>
     * As change tracking is disabled, this operation will override all previous changes which are not reflected
     * by the entity to be saved
     *
     * @param entity the entity to be written into the DB
     * @param <E>    the type of the entity to override
     * @return the updated entity
     */
    public <E extends Entity> E override(E entity) {
        return Index.override(entity);
    }

    /**
     * Tries to find the entity of the given type with the given id.
     *
     * @param clazz the type of the entity
     * @param id    the id of the entity
     * @param <E>   the type of the entity to find
     * @return the entity of the given class with the given id or <tt>null</tt> if no such entity exists
     */
    public <E extends Entity> E find(final Class<E> clazz, String id) {
        return Index.find(null, null, clazz, id);
    }

    /**
     * Tries to find the entity of the given type with the given id and routing.
     *
     * @param routing the value used to compute the routing hash
     * @param clazz   the type of the entity
     * @param id      the id of the entity
     * @param <E>     the type of the entity to find
     * @return the entity of the given class with the given id or <tt>null</tt> if no such entity exists
     */
    public <E extends Entity> E find(String routing, final Class<E> clazz, String id) {
        return Index.find(null, routing, clazz, id);
    }

    /**
     * Tries to find the entity of the given type with the given id.
     *
     * @param index   the index to use. The current index prefix will be automatically added. Can be left <tt>null</tt>
     * @param routing the value used to compute the routing hash
     *                to use the default index
     * @param clazz   the type of the entity
     * @param id      the id of the entity
     * @param <E>     the type of the entity to find
     * @return the entity of the given class with the given id or <tt>null</tt> if no such entity exists
     */
    public <E extends Entity> E find(@Nullable String index,
                                     @Nullable String routing,
                                     @Nonnull final Class<E> clazz,
                                     String id) {
        return Index.find(index, routing, clazz, id);
    }

    /**
     * Tries to load a "fresh" (updated) instance of the given entity from the cluster.
     * <p>
     * Will return <tt>null</tt> if <tt>null</tt> was passed in. If a non persisted entity was given. This
     * entity will be returned. If the entity is no longer available in the cluster,
     * <tt>null</tt> will be returned.
     *
     * @param entity the entity to refresh
     * @param <T>    the type of the entity to refresh
     * @return a refreshed instance of the entity or <tt>null</tt> if either the given entity was <tt>null</tt> or if
     * the given entity cannot be found in the cluster.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends Entity> T refreshOrNull(@Nullable T entity) {
        return Index.refreshOrNull(entity);
    }

    /**
     * Boilerplate method for {@link #refreshOrNull(sirius.search.Entity)}.
     * <p>
     * Returns <tt>null</tt> if the given entity was <tt>null</tt>. Otherweise either a refreshed instance will
     * be returned or an exception will be thrown.
     *
     * @param entity the entity to refresh
     * @param <T>    the type of the entity to refresh
     * @return a fresh instance of the given entity or <tt>null</tt> if <tt>null</tt> was passed in
     * @throws sirius.kernel.health.HandledException if the entity is no longer available in the cluster
     */
    @Nullable
    public <T extends Entity> T refreshOrFail(@Nullable T entity) {
        return Index.refreshOrFail(entity);
    }

    /**
     * Boilerplate method for {@link #refreshOrNull(sirius.search.Entity)}.
     * <p>
     * Returns <tt>null</tt> if the given entity was <tt>null</tt>. Otherwise either a refreshed instance will
     * be returned or the original given instance.
     *
     * @param entity the entity to refresh
     * @param <T>    the type of the entity to refresh
     * @return a fresh instance of the given entity or <tt>null</tt> if <tt>null</tt> was passed in
     */
    @Nullable
    public <T extends Entity> T refreshIfPossible(@Nullable T entity) {
        return Index.refreshIfPossible(entity);
    }

    /**
     * Tries to delete the given entity unless it was modified since the last read.
     *
     * @param entity the entity to delete
     * @param <E>    the type of the entity to delete
     * @throws sirius.search.OptimisticLockException if the entity was modified since the last read
     */
    public <E extends Entity> void tryDelete(E entity) throws OptimisticLockException {
        Index.tryDelete(entity);
    }

    /**
     * Deletes the given entity
     * <p>
     * If the entity was modified since the last read, this operation will fail.
     *
     * @param entity the entity to delete
     * @param <E>    the type of the entity to delete
     */
    public <E extends Entity> void delete(E entity) {
        Index.delete(entity);
    }

    /**
     * Deletes the given entity without any change tracking. Therefore the entity will also be deleted, if it was
     * modified since the last read.
     *
     * @param entity the entity to delete
     * @param <E>    the type of the entity to delete
     */
    public <E extends Entity> void forceDelete(E entity) {
        Index.forceDelete(entity);
    }

    /**
     * Creates a new query for objects of the given class.
     *
     * @param clazz the class of objects to query
     * @param <E>   the type of the entity to query
     * @return a new query against the database
     */
    public <E extends Entity> Query<E> select(Class<E> clazz) {
        return Index.select(clazz);
    }

    /**
     * Creates a new suggestion for objects of the given class
     *
     * @param clazz the class from which suggestions should be generated
     * @param <E> the type of the entity to generated suggestions from
     * @return a new suggestion against the database
     */
    public <E extends Entity> Suggest<E> suggest(Class<E> clazz) {
        return Index.suggest(clazz);
    }

    /**
     * Determines if the framework is completely initialized.
     *
     * @return <tt>true</tt> if the framework is completely initialized, <tt>false</tt> otherwise
     */
    public boolean isReady() {
        return Index.isReady();
    }
}
