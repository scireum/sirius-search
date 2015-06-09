/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.locks;

import javax.annotation.Nonnull;

/**
 * Encapsulates the name of a lock to permit the creation of named constants.
 */
public class Lock {
    private final String name;
    private boolean persistent = true;

    private Lock(String name) {
        this.name = name;
    }

    /**
     * Creates a new lock containing the given name and critical section.
     * <p>
     * The lock name is used to globally acquire a lock. The section is mainly used for debugging or
     * trouble shooting purposes to know why and where a lock is held.
     *
     * @param name the name of the lock (unique key used to identify the lock)
     * @return a new lock instance
     */
    public static Lock named(@Nonnull String name) {
        return new Lock(name);
    }

    /**
     * Returns the name of the lock
     *
     * @return the name of the lock
     */
    public String getName() {
        return name;
    }

    /**
     * Makes the lock transient.
     * <p>
     * A transient lock is deleted once it becomes unlocked. This adds some overhead on locking and unlocking
     * the lock, but must be used when locks are used for mutual exclusive access on data objects (dynamic locks),
     * as otherwise an unlocked lock would never be deleted.
     *
     * @return the lock itself.
     */
    public Lock makeTransient() {
        persistent = false;
        return this;
    }

    /**
     * Determines if the lock is persistent.
     * <p>
     * A persistent lock is not deleted from the lock table once it becomes unlocked. This adds some performance
     * to locking and unlocking it. This should be only used for static locks which are regularly used. For
     * dynamic locks which manage the exclusive access on a single object, {@link #makeTransient()} should be
     * called so that the lock is deleted once it is no longer held - as otherwise it might remain on the lock
     * table forever.
     *
     * @return <tt>true</tt> if the lock is persistent (static), <tt>false</tt> if the lock is transient (dynamic)
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Creates a new critical section which acquired this lock.
     *
     * @param section the name of the section
     * @return a new section for the given name, acquiring the given lock
     */
    public CriticalSection forSection(@Nonnull String section) {
        return new CriticalSection(this, section);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Lock lock = (Lock) o;

        return name.equals(lock.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
