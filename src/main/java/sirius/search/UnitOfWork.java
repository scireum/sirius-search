/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

/**
 * Contains a unit of work which can be restarted.
 * <p>
 * This is used by {@link Index#retry(UnitOfWork)} to signal that the given block can be safely re-executed if
 * a recoverable error like an optimistic lock error occurs.
 * </p>
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
public interface UnitOfWork {

    /**
     * Starts or re-starts the block of code.
     *
     * @throws Exception in case of an inner error
     */
    void execute() throws Exception;

}
