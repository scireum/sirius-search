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
 */
public class OptimisticLockException extends Exception {

    private static final long serialVersionUID = 3422074853606377097L;
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
