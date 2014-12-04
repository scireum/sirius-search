/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.locks;

import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.search.Index;
import sirius.search.IndexAccess;
import sirius.search.OptimisticLockException;

import javax.annotation.Nonnull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Provides a simple distributed lock utilizing ElasticSearch and its optimistic locking capabilities.
 * <p>
 * Note that the locks created here are not reentrant. Therefore if a lock is held by a thread on a node,
 * it must not call lock again for the same lock as this would immediately lead to a deadlock!
 * </p>
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2014/11
 */
@Register(framework = "search.locks", classes = LockManager.class)
public class LockManager {

    private static final Log LOG = Log.get("locks");

    /*
     * Max period in millis which tryLock can wait to acquire a lock
     */
    private static final int MAX_LOCK_WAIT_MILLIS = 60000;

    /*
     * Max period in millis between two tries to acquire a lock
     */
    private static final int MAX_PAUSE_MILLIS = 1500;

    /*
     * Increment in millis which is added to the pause time if a lock
     * was not successfully acquired
     */
    private static final int PAUSE_INCREMENT_MILLIS = 500;

    @Part
    private IndexAccess index;

    /**
     * Tries to acquire the given lock within the given timeout.
     * <p>
     * If the lock cannot be acquired within the given period, an exception is thrown.
     * </p>
     *
     * @param section the critical section describing the lock to acquire
     * @param timeout the max time span to wait for the lock (cannot be longer than {@link #MAX_LOCK_WAIT_MILLIS}
     * @param unit    the unit of <tt>timeout</tt>
     * @throws sirius.kernel.health.HandledException if the lock cannot be acquired with the given time
     */
    public void lock(@Nonnull CriticalSection section, long timeout, @Nonnull TimeUnit unit) {
        if (!tryLock(section, timeout, unit)) {
            throw Exceptions.handle()
                            .to(LOG)
                            .withSystemErrorMessage("Cannot acquire lock '%s' for '%s' after timeout.",
                                                    section.getLock(),
                                                    section.getSection())
                            .handle();
        }
    }

    /**
     * Tries to acquire the given lock within the given timeout.
     * <p>
     * If the lock cannot be acquired within the given period, <tt>false</tt> is returned. Note that if <tt>true</tt>
     * is returned, {@link #unlock(sirius.search.locks.CriticalSection)} MUST be called once the critical section is left.
     * </p>
     *
     * @param section the critical section describing the lock to acquire
     * @param timeout the max time span to wait for the lock (cannot be longer than {@link #MAX_LOCK_WAIT_MILLIS}
     * @param unit    the unit of <tt>timeout</tt>
     * @return <tt>true</tt> if the lock was acquired, <tt>false</tt> otherwise.
     */
    public boolean tryLock(@Nonnull CriticalSection section, long timeout, @Nonnull TimeUnit unit) {
        long timeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, unit);
        if (timeoutMillis > MAX_LOCK_WAIT_MILLIS) {
            throw new IllegalArgumentException(Strings.apply(
                    "Cannot wait longer than '%s' for a lock '%s' in section '%s",
                    NLS.convertDuration(MAX_LOCK_WAIT_MILLIS),
                    section.getLock(),
                    section.getSection()));
        }
        long limit = System.currentTimeMillis() + timeoutMillis;
        int pauseMillis = 0;
        int maxPauseMillis = MAX_PAUSE_MILLIS;
        do {
            if (tryAcquire(section)) {
                return true;
            }
            // Wait 500ms, 1s and then always 1.5s to check...
            pauseMillis = Math.min(pauseMillis + PAUSE_INCREMENT_MILLIS, maxPauseMillis);
            Wait.millis(pauseMillis);
            Wait.randomMillis(-200, 200);
        } while (System.currentTimeMillis() < limit);
        return false;
    }

    /*
     * Tries to update the LockInfo entity for the given section. This will create a mutual exclusive lock
     * by utilizing optimistic locking
     */
    protected boolean tryAcquire(CriticalSection section) {
        try {
            LockInfo li = index.find(LockInfo.class, section.getLock().getName());
            if (li == null) {
                li = new LockInfo();
                li.setId(section.getLock().getName());
                li = index.tryUpdate(li);
            }
            // Lock is locked -> abort...
            if (Strings.isFilled(li.getCurrentOwnerSection()) || li.getLockedSince() != null) {
                return false;
            }

            // Lock was unlocked, try to acquire....
            li.setCurrentOwnerSection(section.getSection());
            li.setCurrentOwnerNode(CallContext.getNodeName());
            li.setLockedSince(LocalDateTime.now());
            index.tryUpdate(li);
            return true;
        } catch (OptimisticLockException e) {
            return false;
        }
    }

    /**
     * Kills (removes) the lock without further checks.
     * <p>
     * This method must not be used in "normal" user code and is only intended for administrative tasks.
     * </p>
     *
     * @param lock the lock to kill (forcefully unlock)
     */
    public void killLock(String lock) {
        LockInfo li = index.find(LockInfo.class, lock);
        if (li != null) {
            index.delete(li);
            Index.blockThreadForUpdate();
        }
    }

    /**
     * Returns all currently active locks.
     * <p>
     * Intended for administrative tasks.
     * </p>
     *
     * @return a list of all currently held locks
     */
    public List<LockInfo> getLocks() {
        return index.select(LockInfo.class).queryList();
    }

    /**
     * Unlocks the given lock which is held for the given section.
     * <p>
     * Throws an exception if the lock is not held by the current node and section.
     * </p>
     *
     * @param section the critical section describing the lock to unlock
     */
    public void unlock(CriticalSection section) {
        LockInfo li = index.find(LockInfo.class, section.getLock().getName());
        if (li != null) {
            if (!Strings.areEqual(CallContext.getNodeName(),
                                  li.getCurrentOwnerNode()) || !Strings.areEqual(section.getSection(),
                                                                                 li.getCurrentOwnerSection())) {
                throw Exceptions.handle()
                                .to(LOG)
                                .withSystemErrorMessage(
                                        "Cannot unlock '%s' by section '%s' on '%s' as lock is held by section '%s' on '%s'",
                                        section.getLock(),
                                        section.getSection(),
                                        CallContext.getNodeName(),
                                        li.getCurrentOwnerSection(),
                                        li.getCurrentOwnerNode())
                                .handle();
            }
            if (section.getLock().isPersistent()) {
                try {
                    li.setLockedSince(null);
                    li.setCurrentOwnerSection(null);
                    li.setCurrentOwnerNode(null);
                    index.tryUpdate(li);
                } catch (OptimisticLockException e) {
                    throw Exceptions.handle()
                                    .to(LOG)
                                    .withSystemErrorMessage(
                                            "Cannot unlock '%s' by section '%s' on '%s' as lock was updated while unlocking!",
                                            section.getLock(),
                                            section.getSection(),
                                            CallContext.getNodeName())
                                    .handle();
                }
            } else {
                index.delete(li);
            }
        } else {
            throw Exceptions.handle()
                            .to(LOG)
                            .withSystemErrorMessage(
                                    "Cannot unlock '%s' by section '%s' on '%s' as this lock does not exists",
                                    section.getLock(),
                                    section.getSection(),
                                    CallContext.getNodeName())
                            .handle();
        }
    }

    /**
     * Tries to obtain the lock and call the given runnable.
     * <p>
     * Automatically releases the lock once the criticalSection is completed. If the lock cannot be obtained,
     * nothing happens.
     * </p>
     *
     * @param section         the critical section describing the lock to acquire
     * @param timeout         the max period to wait for  the lock
     * @param unit            the unit for <tt>timeout</tt>
     * @param criticalSection the criticalSection to execute while holding the lock
     * @return <tt>true</tt> if the lock was acquired and the given section was executed,
     * <tt>false</tt> otherwise
     */
    public boolean tryInLock(CriticalSection section, long timeout, TimeUnit unit, Runnable criticalSection) {
        if (tryLock(section, timeout, unit)) {
            try {
                criticalSection.run();
            } finally {
                unlock(section);
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Performs the given runnable while obtaining the given lock.
     * <p>
     * Automatically releases the lock once the criticalSection is completed.
     * </p>
     *
     * @param section         the critical section describing the lock to acquire
     * @param timeout         the max period to wait for  the lock
     * @param unit            the unit for <tt>timeout</tt>
     * @param criticalSection the criticalSection to execute while holding the lock
     * @throws sirius.kernel.health.HandledException if the lock cannot be obtained
     */
    public void inLock(CriticalSection section, long timeout, TimeUnit unit, Runnable criticalSection) {
        lock(section, timeout, unit);
        try {
            criticalSection.run();
        } finally {
            unlock(section);
        }
    }

}
