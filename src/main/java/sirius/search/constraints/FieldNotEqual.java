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
import sirius.search.Entity;
import sirius.search.EntityRef;
import sirius.search.Index;

/**
 * Represents a constraint which checks if the given field has not the given value.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
public class FieldNotEqual implements Constraint {
    private final String field;
    private Object value;
    private boolean isFilter;

    /*
     * Use the #on(String, Object) factory method
     */
    private FieldNotEqual(String field, Object value) {
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
        if (value != null && value instanceof Entity) {
            this.value = ((Entity) value).getId();
        }
        if (value != null && value instanceof EntityRef) {
            this.value = ((EntityRef) value).getId();
        }
    }

    /**
     * Forces this constraint to be applied as filter not as query.
     *
     * @return the constraint itself for fluent method calls
     */
    public FieldNotEqual asFilter() {
        isFilter = true;
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
        if (!isFilter) {
            return Not.on(FieldEqual.on(field, value)).createQuery();
        }
        return null;
    }

    @Override
    public FilterBuilder createFilter() {
        if (isFilter) {
            return Not.on(FieldEqual.on(field, value)).createFilter();
        }
        return null;
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
