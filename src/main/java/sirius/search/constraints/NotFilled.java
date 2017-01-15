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

/**
 * Represents a constraint which verifies that a given field is empty.
 */
public class NotFilled implements Constraint {

    private String field;

    /*
     * Use the #on(String) factory method
     */
    private NotFilled(String field) {
        this.field = field;
    }

    /**
     * Creates a new constraint which verifies that the given field is empty.
     *
     * @param field to field to be checked
     * @return the newly created constraint
     */
    public static Constraint on(String field) {
        return new NotFilled(field);
    }

    @Override
    public QueryBuilder createQuery() {
        return QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(field));
    }

    @Override
    public String toString() {
        return toString(false);
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return field + " IS NULL";
    }
}
