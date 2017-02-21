/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.SpanQueryBuilder;

/**
 * Defines a span constraint which can be added to a {@link sirius.search.Query} to determine the result set.
 */
public interface SpanConstraint {
    /**
     * Creates an ElasticSearch span query for more fine grained word distance constraints.
     *
     * @return the ElasticSearch span query representing this constraint
     */
    SpanQueryBuilder createSpanQuery();

    /**
     * Creates a string representation of this constraint.
     *
     * @param skipConstraintValues determines if the filter values are included or replaced by generic placeholders.
     * @return a string representation of this constraint
     */
    String toString(boolean skipConstraintValues);

}
