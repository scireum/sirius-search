/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.locks;

/**
 * Encapsulates a critical section accessing a lock to permit the usage of typed constants.
 * <p>
 * A critical section is used to access and acquire a {@link Lock}. Its name is mainly used to debugging and
 * troubleshooting purposes.
 */
public class CriticalSection {
    private final Lock lock;
    private final String section;

    /*
     * Use {@link Lock#forSection(String)} to create a new instance.
     */
    protected CriticalSection(Lock lock, String section) {
        this.lock = lock;
        this.section = section;
    }

    /**
     * Returns the lock this section belongs to
     *
     * @return the lock this section belongs to
     */
    public Lock getLock() {
        return lock;
    }

    /**
     * Returns the name of the critical section trying a use the lock
     *
     * @return the name of the critical section acquiring the lock
     */
    public String getSection() {
        return section;
    }

    @Override
    public String toString() {
        return section + " (" + lock + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CriticalSection that = (CriticalSection) o;

        if (!lock.equals(that.lock)) {
            return false;
        }
        if (!section.equals(that.section)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = lock.hashCode();
        result = 31 * result + section.hashCode();
        return result;
    }
}
