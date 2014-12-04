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
import org.elasticsearch.index.query.QueryBuilders;
import sirius.kernel.commons.Strings;

/**
 * Represents a constraint which checks if the given field starts with the given value
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2014/05
 */
public class Prefix implements Constraint {
    private final String field;
    private String value;
    private boolean isFilter;

    /*
     * Use the #on(String, Object) factory method
     */
    private Prefix(String field, String value) {
        this.field = field;
        this.value = value;
    }

    /**
     * Creates a new constraint for the given field and value.
     *
     * @param field the field to check
     * @param value the expected prefix to filter by
     * @return a new constraint representing the given filter setting
     */
    public static Prefix on(String field, String value) {
        return new Prefix(field, value);
    }

    /**
     * Forces this constraint to be applied as filter not as query.
     *
     * @return the constraint itself for fluent method calls
     */
    public Prefix asFilter() {
        isFilter = true;
        return this;
    }

    @Override
    public QueryBuilder createQuery() {
        if (Strings.isFilled(value) && !isFilter) {
            return QueryBuilders.prefixQuery(field, value);
        }
        return null;
    }

    @Override
    public FilterBuilder createFilter() {
        if (Strings.isFilled(value) && isFilter) {
            return FilterBuilders.prefixFilter(field, value);
        }
        return null;
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return field + " STARTS-WITH '" + (skipConstraintValues ? "?" : value) + "'";
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
