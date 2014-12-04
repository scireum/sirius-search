/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import org.elasticsearch.index.engine.VersionConflictEngineException;

/**
 * Wrapper for VersionConflictEngineException to make it a checked exception again.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/09
 */
public class OptimisticLockException extends Exception {

    private Entity entity;

    /**
     * Creates a new optimistic lock error for the given entity.
     *
     * @param root   the root exception thrown by ES
     * @param entity the entity which caused the error
     */
    public OptimisticLockException(VersionConflictEngineException root, Entity entity) {
        super(root);
        this.entity = entity;
    }

    /**
     * Returns the entity which caused the optimistic lock error.
     *
     * @return the entity which caused the optimistic lock error
     */
    public Entity getEntity() {
        return entity;
    }

}
