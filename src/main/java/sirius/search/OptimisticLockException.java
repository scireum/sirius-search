/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import org.elasticsearch.index.engine.VersionConflictEngineException;

import java.util.List;

/**
 * Wrapper for VersionConflictEngineException to make it a checked exception again.
 */
public class OptimisticLockException extends Exception {

    private static final long serialVersionUID = 3422074853606377097L;
    private transient Entity entity;
    private transient List<? extends Entity> entities;

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
     * Creates a new optimistic lock error for the given entities. As no {@link VersionConflictEngineException} is
     * thrown for bulk-operations, the failure-message is passed instead of a direct exception.
     *
     * @param message  the message
     * @param entities the entities which caused the error during a bulk-request
     */
    public OptimisticLockException(String message, List<? extends Entity> entities) {
        super(message);
        this.entities = entities;
    }

    /**
     * Returns the entity which caused the optimistic lock error.
     *
     * @return the entity which caused the optimistic lock error
     */
    public Entity getEntity() {
        return entity;
    }

    /**
     * Returns the entities which caused the optimistic lock error.
     *
     * @return the entities which caused the optimistic lock error
     */
    public List<? extends Entity> getEntities() {
        return entities;
    }
}