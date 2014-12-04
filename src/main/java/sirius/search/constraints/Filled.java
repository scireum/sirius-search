/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;

/**
 * Represents a constraint which checks that the given field is not empty
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
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
        return null;
    }

    @Override
    public FilterBuilder createFilter() {
        return FilterBuilders.existsFilter(field);
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
