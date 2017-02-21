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
 * Represents a constraint which checks that the given field is not empty
 */
public class Filled implements Constraint {

    private String field;

    /*
     * Use the #on(String) factory method
     */
    private Filled(String field) {
        this.field = field;
    }

    /**
     * Creates a new constraint for the given field.
     *
     * @param field the field which must not be empty
     * @return the new constraint which verifies that the given field is not empty
     */
    public static Constraint on(String field) {
        return new Filled(field);
    }

    @Override
    public QueryBuilder createQuery() {
        return QueryBuilders.existsQuery(field);
    }

    @Override
    public String toString() {
        return toString(false);
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return field + " IS NOT NULL";
    }
}
