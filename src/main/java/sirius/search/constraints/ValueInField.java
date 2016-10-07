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
 * Represents a constraint which verifies that the given list field contains at least the given value.
 */
public class ValueInField implements Constraint {

    private final Object value;
    private final String field;
    private boolean isFilter;

    /*
     * Use the #on(Object, String) factory method
     */
    private ValueInField(Object value, String field) {
        this.value = FieldEqual.transformFilterValue(value);
        this.field = field;
    }

    /**
     * Creates a new constraint which verifies that the given field contains at least the given value.
     *
     * @param value the value to check for
     * @param field the field to check
     * @return the newly created constraint
     */
    public static ValueInField on(Object value, String field) {
        return new ValueInField(value, field);
    }

    /**
     * Forces this constraint to be applied as filter not as query.
     *
     * @return the constraint itself for fluent method calls
     */
    public ValueInField asFilter() {
        isFilter = true;
        return this;
    }

    @Override
    public QueryBuilder createQuery() {
        if (!isFilter) {
            return QueryBuilders.termQuery(field, value);
        }
        return null;
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return "'" + (skipConstraintValues ? "?" : value) + "' IN " + field;
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
