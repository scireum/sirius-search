/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.SpanTermQueryBuilder;
import sirius.search.Entity;
import sirius.search.IndexAccess;

/**
 * Represents a constraint which checks if the given field has not the given value.
 */
public class FieldNotEqual implements Constraint {
    private final String field;
    private Object value;

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
