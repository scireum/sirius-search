/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.Collection;

/**
 * Represents a constraint which verifies that the given field contains none of the given values.
 */
public class NoneInField implements Constraint {

    private final Collection<?> values;
    private final String field;
    private boolean isFilter;

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

    /**
     * Forces this constraint to be applied as filter not as query.
     *
     * @return the constraint itself for fluent method calls
     */
    public NoneInField asFilter() {
        isFilter = true;
        return this;
    }

    @Override
    public QueryBuilder createQuery() {
        if (!isFilter) {
            if (values == null || values.isEmpty()) {
                return null;
            }
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            for (Object value : values) {
                boolQueryBuilder.mustNot(QueryBuilders.termQuery(field, FieldEqual.transformFilterValue(value)));
            }
            return boolQueryBuilder;
        }
        return null;
    }

    @Override
    public FilterBuilder createFilter() {
        if (isFilter) {
            if (values == null || values.isEmpty()) {
                return null;
            }
            BoolFilterBuilder boolFilterBuilder = FilterBuilders.boolFilter();
            for (Object value : values) {
                boolFilterBuilder.mustNot(FilterBuilders.termFilter(field, FieldEqual.transformFilterValue(value)));
            }

            return boolFilterBuilder;
        }
        return null;
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
