/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.SpanQueryBuilder;
import sirius.kernel.nls.NLS;

/**
 * Represents a constraint which verifies that the given string occurs in any of the searchable fields of the entity
 */
public class QueryString implements Constraint {

    private final Object value;

    /*
     * Use the #query(Object) factory method
     */
    private QueryString(Object value) {
        this.value = value;
    }

    /**
     * Creates a new constraint which filters entities having the given value in one of their fields.
     *
     * @param value the value to filter by
     * @return the newly created constraint
     */
    public static QueryString query(Object value) {
        return new QueryString(value);
    }

    @Override
    public QueryBuilder createQuery() {
        return QueryBuilders.queryStringQuery(NLS.toMachineString(value));
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return "_QUERY" + " = '" + (skipConstraintValues ? "?" : value) + "'";
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
