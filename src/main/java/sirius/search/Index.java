/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.kernel.async.Future;
import sirius.kernel.commons.Callback;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Central access class to the persistence layer.
 * <p>
 * Provides CRUD access to the underlying ElasticSearch cluster.
 *
 * @deprecated use {@link IndexAccess}
 */
@Deprecated
public class Index {

    @Part
    private static IndexAccess access;

    private Index() {
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
     * @return a tuple containing the resolved entity (or <tt>null</tt> if not found) and a flag which indicates if the
     * value was loaded from cache (<tt>true</tt>) or not.
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    public static <E extends Entity> Tuple<E, Boolean> fetch(@Nullable String routing,
                                                             @Nonnull Class<E> type,
                                                             @Nullable String id,
                                                             @Nonnull
                                                                     com.google.common.cache.Cache<String, Object> cache) {
        Exceptions.logDeprecatedMethodUse();
        return access.fetch(routing, type, id, cache);
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
    public static <E extends Entity> E fetchFromCache(@Nullable String routing,
                                                      @Nonnull Class<E> type,
                                                      @Nullable String id,
                                                      @Nonnull com.google.common.cache.Cache<String, Object> cache) {
        Exceptions.logDeprecatedMethodUse();
        return access.fetchFromCache(routing, type, id, cache);
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
    @SuppressWarnings("unchecked")
    public static <E extends Entity> Tuple<E, Boolean> fetch(@Nullable String routing,
                                                             @Nonnull Class<E> type,
                                                             @Nullable String id) {
        Exceptions.logDeprecatedMethodUse();
        return access.fetch(routing, type, id);
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
    public static <E extends Entity> E fetchFromCache(@Nullable String routing,
                                                      @Nonnull Class<E> type,
                                                      @Nullable String id) {
        Exceptions.logDeprecatedMethodUse();
        return access.fetchFromCache(routing, type, id);
    }

    /**
     * Provides access to the expected schema / mappings.
     *
     * @return the expected schema of the object model
     */
    public static Schema getSchema() {
        Exceptions.logDeprecatedMethodUse();
        return access.getSchema();
    }

    /**
     * Adds an action to the delay line, which ensures that it is at least delayed for one second
     *
     * @param cmd to command to be delayed
     */
    public static void callAfterUpdate(final Runnable cmd) {
        Exceptions.logDeprecatedMethodUse();
        access.callAfterUpdate(cmd);
    }

    /**
     * Manually blocks the current thread for one second, to make a write visible in ES.
     * <p>
     * Consider using {@link #callAfterUpdate(Runnable)} which does not block system resources. Only use this method
     * is absolutely necessary.
     */
    public static void blockThreadForUpdate() {
        Exceptions.logDeprecatedMethodUse();
        access.blockThreadForUpdate();
    }

    /**
     * Determines if the framework is completely initialized.
     *
     * @return <tt>true</tt> if the framework is completely initialized, <tt>false</tt> otherwise
     */
    public static boolean isReady() {
        Exceptions.logDeprecatedMethodUse();
        return access.isReady();
    }

    /**
     * Returns the readiness state of the index as {@link Future}.
     *
     * @return a future which is fullfilled once the index is ready
     */
    public static Future ready() {
        Exceptions.logDeprecatedMethodUse();
        return access.ready();
    }

    /**
     * Blocks the calling thread until the index is ready.
     */
    public static void waitForReady() {
        Exceptions.logDeprecatedMethodUse();
        access.waitForReady();
    }

    /**
     * Handles the given unit of work while restarting it if an optimistic lock error occurs.
     *
     * @param uow the unit of work to handle.
     * @throws HandledException if either any other exception occurs, or if all three attempts fail with an optimistic
     *                          lock error.
     */
    public static void retry(UnitOfWork uow) {
        Exceptions.logDeprecatedMethodUse();
        access.retry(uow);
    }

    /**
     * Tries to apply the given changes and to save the resulting entity.
     * <p>
     * Tries to perform the given modifications and then to update the entity. If an optimistic lock error occurs,
     * the entity is refreshed and the modifications are re-executed along with another update.
     *
     * @param entity          the entity to update
     * @param preSaveModifier the changes to perform on the entity
     * @param <E>             the type of the entity to update
     * @throws HandledException if either any other exception occurs, or if all three attempts fail with an optimistic
     *                          lock error.
     */
    public static <E extends Entity> void retryUpdate(E entity, Callback<E> preSaveModifier) {
        Exceptions.logDeprecatedMethodUse();
        access.retryUpdate(entity, preSaveModifier);
    }

    /**
     * Checks if the given index exists.
     *
     * @param name the name of the index. The index prefix of the current system will be added automatically
     * @return <tt>true</tt> if the given index exists, <tt>false</tt> otherwise
     */
    public static boolean existsIndex(String name) {
        Exceptions.logDeprecatedMethodUse();
        return access.existsIndex(name);
    }

    /**
     * Ensures that the given manually created index exists.
     *
     * @param name the name of the index. The index prefix of the current system will be added automatically
     */
    public static void ensureIndexExists(String name) {
        Exceptions.logDeprecatedMethodUse();
        access.ensureIndexExists(name);
    }

    /**
     * Completely wipes the given index.
     *
     * @param name the name of the index. The index prefix of the current system will be added automatically
     */
    public static void deleteIndex(String name) {
        Exceptions.logDeprecatedMethodUse();
        access.deleteIndex(name);
    }

    /**
     * Writes the given mapping to the given index.
     *
     * @param index the full name of the index.
     *              Note: The index prefix of the current system will NOT be added automatically
     * @param type  the entity class which mapping should be written to the given index.
     * @param <E>   the generic of <tt>type</tt>
     */
    public static <E extends Entity> void addMapping(String index, Class<E> type) {
        Exceptions.logDeprecatedMethodUse();
        access.addMapping(index, type);
    }

    /**
     * Creates this entity by storing an initial copy in the database.
     *
     * @param entity the entity to be stored in the database
     * @param <E>    the type of the entity to create
     * @return the stored entity (with a filled ID etc.)
     */
    public static <E extends Entity> E create(E entity) {
        Exceptions.logDeprecatedMethodUse();
        return access.create(entity);
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
     * @throws OptimisticLockException if the entity was modified in the database and those changes where not reflected
     *                                 by the entity to be saved
     */
    public static <E extends Entity> E tryUpdate(E entity) throws OptimisticLockException {
        Exceptions.logDeprecatedMethodUse();
        return access.tryUpdate(entity);
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
    public static <E extends Entity> E update(E entity) {
        Exceptions.logDeprecatedMethodUse();
        return access.update(entity);
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
    public static <E extends Entity> E override(E entity) {
        Exceptions.logDeprecatedMethodUse();
        return access.override(entity);
    }

    /**
     * Tries to find the entity of the given type with the given id.
     *
     * @param clazz the type of the entity
     * @param id    the id of the entity
     * @param <E>   the type of the entity to find
     * @return the entity of the given class with the given id or <tt>null</tt> if no such entity exists
     */
    public static <E extends Entity> E find(final Class<E> clazz, String id) {
        Exceptions.logDeprecatedMethodUse();
        return access.find(null, null, clazz, id);
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
    public static <E extends Entity> E find(String routing, final Class<E> clazz, String id) {
        Exceptions.logDeprecatedMethodUse();
        return access.find(null, routing, clazz, id);
    }

    /**
     * Tries to find the entity of the given type with the given id.
     *
     * @param index   the index to use. The current index prefix will be automatically added. Can be left <tt>null</tt>
     *                to use the default index
     * @param routing the value used to compute the routing hash
     * @param clazz   the type of the entity
     * @param id      the id of the entity
     * @param <E>     the type of the entity to find
     * @return the entity of the given class with the given id or <tt>null</tt> if no such entity exists
     */
    public static <E extends Entity> E find(@Nullable String index,
                                            @Nullable String routing,
                                            @Nonnull final Class<E> clazz,
                                            String id) {
        Exceptions.logDeprecatedMethodUse();
        return access.find(index, routing, clazz, id);
    }

    /**
     * Tries to load a "fresh" (updated) instance of the given entity from the cluster.
     * <p>
     * Will return <tt>null</tt> if <tt>null</tt> was passed in. If a non persisted entity was given. This
     * entity will be returned. If the entity is no longer available in the cluster,
     * <tt>null</tt> will be returned
     *
     * @param entity the entity to refresh
     * @param <T>    the type of the entity to refresh
     * @return a refreshed instance of the entity or <tt>null</tt> if either the given entity was <tt>null</tt> or if
     * the given entity cannot be found in the cluster.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends Entity> T refreshOrNull(@Nullable T entity) {
        Exceptions.logDeprecatedMethodUse();
        return access.refreshOrNull(entity);
    }

    /**
     * Boilerplate method for {@link #refreshOrNull(Entity)}.
     * <p>
     * Returns <tt>null</tt> if the given entity was <tt>null</tt>. Otherwise either a refreshed instance will
     * be returned or an exception will be thrown.
     *
     * @param entity the entity to refresh
     * @param <T>    the type of the entity to refresh
     * @return a fresh instance of the given entity or <tt>null</tt> if <tt>null</tt> was passed in
     * @throws sirius.kernel.health.HandledException if the entity is no longer available in the cluster
     */
    @Nullable
    public static <T extends Entity> T refreshOrFail(@Nullable T entity) {
        Exceptions.logDeprecatedMethodUse();
        return access.refreshOrFail(entity);
    }

    /**
     * Boilerplate method for {@link #refreshOrNull(Entity)}.
     * <p>
     * Returns <tt>null</tt> if the given entity was <tt>null</tt>. Otherwise either a refreshed instance will
     * be returned or the original given instance.
     *
     * @param entity the entity to refresh
     * @param <T>    the type of the entity to refresh
     * @return a fresh instance of the given entity or <tt>null</tt> if <tt>null</tt> was passed in
     */
    @Nullable
    public static <T extends Entity> T refreshIfPossible(@Nullable T entity) {
        Exceptions.logDeprecatedMethodUse();
        return access.refreshIfPossible(entity);
    }

    /**
     * Tries to delete the given entity unless it was modified since the last read.
     *
     * @param entity the entity to delete
     * @param <E>    the type of the entity to delete
     * @throws OptimisticLockException if the entity was modified since the last read
     */
    public static <E extends Entity> void tryDelete(E entity) throws OptimisticLockException {
        Exceptions.logDeprecatedMethodUse();
        access.tryDelete(entity);
    }

    /**
     * Deletes the given entity
     * <p>
     * If the entity was modified since the last read, this operation will fail.
     *
     * @param entity the entity to delete
     * @param <E>    the type of the entity to delete
     */
    public static <E extends Entity> void delete(E entity) {
        Exceptions.logDeprecatedMethodUse();
        access.delete(entity);
    }

    /**
     * Deletes the given entity without any change tracking. Therefore the entity will also be deleted, if it was
     * modified since the last read.
     *
     * @param entity the entity to delete
     * @param <E>    the type of the entity to delete
     */
    public static <E extends Entity> void forceDelete(E entity) {
        Exceptions.logDeprecatedMethodUse();
        access.forceDelete(entity);
    }

    /**
     * Creates a new query for objects of the given class.
     *
     * @param clazz the class of objects to query
     * @param <E>   the type of the entity to query
     * @return a new query against the database
     */
    public static <E extends Entity> Query<E> select(Class<E> clazz) {
        Exceptions.logDeprecatedMethodUse();
        return access.select(clazz);
    }
}
