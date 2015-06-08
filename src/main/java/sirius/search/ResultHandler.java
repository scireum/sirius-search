/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

/**
 * Used to iterate through large result sets to process each entity.
 * <p>
 * Instances of this class can be passed to {@link Query#iterate(ResultHandler)} to process the result entity
 * by entity which permits scrolling to large result sets and therefore minimizing resource utilization.
 */
public interface ResultHandler<T> {
    /**
     * Handles one entity of the result set.
     *
     * @param row the entity to process
     * @return <tt>true</tt> if the processing should continue, <tt>false</tt> if no more results need to be processed
     * @throws Exception in case of an error while processing the entity
     */
    boolean handleRow(T row) throws Exception;
}
