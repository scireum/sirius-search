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
 * Represents a constraint which wraps a given {@link org.elasticsearch.index.query.QueryBuilder}
 */
public class Wrapper implements Constraint {

    private QueryBuilder wrapped;

    /*
     * Use the #on(QueryBuilder) factory method
     */
    private Wrapper(QueryBuilder wrapped) {
        this.wrapped = wrapped;
    }

    /**
     * Creates a new constraint for the given QueryBuilder.
     *
     * @param wrapped the query to wrap
     * @return a new constraint representing the given filter setting
     */
    public static Wrapper on(QueryBuilder wrapped) {
        return new Wrapper(wrapped);
    }

    @Override
    public QueryBuilder createQuery() {
        return wrapped;
    }

    @Override
    public FilterBuilder createFilter() {
        return null;
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return wrapped.toString();
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
