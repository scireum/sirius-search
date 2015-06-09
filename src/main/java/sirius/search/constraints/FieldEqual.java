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
import sirius.search.Entity;
import sirius.search.EntityRef;
import sirius.search.Index;

/**
 * Represents a constraint which checks if the given field has the given value.
 */
public class FieldEqual implements Constraint {
    private final String field;
    private Object value;
    private boolean isFilter;
    private boolean ignoreNull = false;

    /*
     * Use the #on(String, Object) factory method
     */
    private FieldEqual(String field, Object value) {
        // In search queries the id field must be referenced via "_id" not "id..
        if (Entity.ID.equalsIgnoreCase(field)) {
            this.field = Index.ID_FIELD;
        } else {
            this.field = field;
        }
        this.value = FieldOperator.convertJava8Times(value);
        if (value != null && value.getClass().isEnum()) {
            this.value = ((Enum<?>) value).name();
        }
        if (value instanceof Entity) {
            this.value = ((Entity) value).getId();
        }
        if (value instanceof EntityRef) {
            this.value = ((EntityRef<?>) value).getId();
        }
    }

    /**
     * Creates a new constraint for the given field and value.
     *
     * @param field the field to check
     * @param value the expected value to filter for
     * @return a new constraint representing the given filter setting
     */
    public static FieldEqual on(String field, Object value) {
        return new FieldEqual(field, value);
    }

    /**
     * Makes the filter ignore <tt>null</tt> values (no constraint will be created).
     *
     * @return the constraint itself for fluent method calls
     */
    public FieldEqual ignoreNull() {
        this.ignoreNull = true;
        return this;
    }

    /**
     * Forces this constraint to be applied as filter not as query.
     *
     * @return the constraint itself for fluent method calls
     */
    public FieldEqual asFilter() {
        isFilter = true;
        return this;
    }

    @Override
    public QueryBuilder createQuery() {
        if (Strings.isEmpty(value)) {
            // We need a filter in that case...
            return null;
        }
        if (!isFilter) {
            return QueryBuilders.termQuery(field, value);
        }
        return null;
    }

    @Override
    public FilterBuilder createFilter() {
        if (Strings.isEmpty(value)) {
            if (ignoreNull) {
                return null;
            }
            // We need a filter in that case...
            return FilterBuilders.missingFilter(field);
        }
        if (isFilter) {
            return FilterBuilders.termFilter(field, value);
        }
        return null;
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return field + " = '" + (skipConstraintValues ? "?" : value) + "'";
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
