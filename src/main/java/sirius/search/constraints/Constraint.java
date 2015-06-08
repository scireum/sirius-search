/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;

/**
 * Defines a constraint which can be added to a {@link sirius.search.Query} to determine the result set.
 */
public interface Constraint {
    /**
     * Creates an ElasticSearch query which reflects this constraint. If this is a filter constraint, <tt>null</tt>
     * can be returned.
     *
     * @return the ElasticSearch query representing this constraint or <tt>null</tt> if it is a filter
     */
    QueryBuilder createQuery();

    /**
     * Creates an ElasticSearch filter which represents this constraint.
     *
     * @return an ElasticSearch filter representing this constraint or <tt>null</tt> if this constraint is realized
     * via a query and not a filter.
     */
    FilterBuilder createFilter();

    /**
     * Creates a string representation of this constraint.
     *
     * @param skipConstraintValues determines if the filter values are included or replaced by generic placeholders.
     * @return a string representation of this constraint
     */
    String toString(boolean skipConstraintValues);
}
