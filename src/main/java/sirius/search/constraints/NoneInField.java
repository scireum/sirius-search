/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.SpanQueryBuilder;

import java.util.Collection;

/**
 * Represents a constraint which verifies that the given field contains none of the given values.
 */
public class NoneInField implements Constraint {

    private final Collection<?> values;
    private final String field;

    /*
     * Use the #on(List, String) factory method
     */
    private NoneInField(Collection<?> values, String field) {
        this.values = values;
        this.field = field;
    }

    /**
     * Creates a new constraint which verifies that the given field contains none of the given values.
     *
     * @param values the values to check for
     * @param field  the field to check
     * @return the newly created constraint
     */
    public static NoneInField on(Collection<?> values, String field) {
        return new NoneInField(values, field);
    }

    @Override
    public QueryBuilder createQuery() {
        if (values == null || values.isEmpty()) {
            return null;
        }
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        for (Object value : values) {
            boolQueryBuilder.mustNot(QueryBuilders.termQuery(field, FieldEqual.transformFilterValue(value)));
        }
        return boolQueryBuilder;
    }

    @Override
    public SpanQueryBuilder createSpanQuery() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        if (values == null || values.isEmpty()) {
            return "<skipped>";
        }
        return "'" + (skipConstraintValues ? "?" : values) + "' NOT IN " + field;
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
