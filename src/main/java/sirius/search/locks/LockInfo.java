/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.locks;

import sirius.search.Entity;
import sirius.search.annotations.Indexed;

import java.time.LocalDateTime;

/**
 * Used by {@link sirius.search.locks.LockManager} to represent a lock in Elasticsearch.
 */
@Indexed(index = "core", framework = "search.locks")
public class LockInfo extends Entity {

    /**
     * Contains the current owner of the lock (its node name).
     */
    public static final String CURRENT_OWNER_NODE = "currentOwnerNode";
    private String currentOwnerNode;

    /**
     * Contains the code section which holds the lock.
     */
    public static final String CURRENT_OWNER_SECTION = "currentOwnerSection";
    private String currentOwnerSection;

    /**
     * Contains the timestamp when the lock was created.
     */
    public static final String LOCKED_SINCE = "lockedSince";
    private LocalDateTime lockedSince;

    public String getCurrentOwnerNode() {
        return currentOwnerNode;
    }

    public void setCurrentOwnerNode(String currentOwnerNode) {
        this.currentOwnerNode = currentOwnerNode;
    }

    public String getCurrentOwnerSection() {
        return currentOwnerSection;
    }

    public void setCurrentOwnerSection(String currentOwnerSection) {
        this.currentOwnerSection = currentOwnerSection;
    }

    public LocalDateTime getLockedSince() {
        return lockedSince;
    }

    public void setLockedSince(LocalDateTime lockedSince) {
        this.lockedSince = lockedSince;
    }
}
