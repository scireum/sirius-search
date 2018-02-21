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
import sirius.kernel.commons.Strings;
import sirius.search.Entity;
import sirius.search.IndexAccess;

/**
 * Represents a constraint which checks if the given field has not the given value.
 */
public class FieldNotEqual implements Constraint {
    private final String field;
    private Object value;
    private boolean ignoreNull = false;

    /*
     * Use the #on(String, Object) factory method
     */
    private FieldNotEqual(String field, Object value) {
        // In search queries the id field must be referenced via "_id" not "id..
        if (Entity.ID.equalsIgnoreCase(field)) {
            this.field = IndexAccess.ID_FIELD;
        } else {
            this.field = field;
        }
        this.value = FieldEqual.transformFilterValue(value);
    }

    /**
     * Makes the filter ignore <tt>null</tt> values (no constraint will be created).
     *
     * @return the constraint itself for fluent method calls
     */
    public FieldNotEqual ignoreNull() {
        this.ignoreNull = true;
        return this;
    }

    /**
     * Creates a new constraint for the given field and value.
     *
     * @param field the field to check
     * @param value the value to filter for
     * @return a new constraint representing the given filter setting
     */
    public static FieldNotEqual on(String field, Object value) {
        return new FieldNotEqual(field, value);
    }

    @Override
    public QueryBuilder createQuery() {
        if (Strings.isEmpty(value)) {
            if (ignoreNull || field.equals(IndexAccess.ID_FIELD)) {
                return null;
            } else {
                return QueryBuilders.existsQuery(field);
            }
        }
        return Not.on(FieldEqual.on(field, value)).createQuery();
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return field + " != '" + (skipConstraintValues ? "?" : value) + "'";
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
