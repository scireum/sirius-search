/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.*;

/**
 * Represents a relational filter which can be used to filter &lt; or &lt;=, along with &gt; or &gt;=
 */
public class FieldOperator implements Constraint {

    private enum Bound {
        LT, LT_EQ, GT, GT_EQ
    }

    private String field;
    private Object value;
    private Bound bound;
    private boolean orEmpty = false;

    /*
     * Use one of the factory methods
     */
    private FieldOperator(String field) {
        this.field = field;
    }

    /**
     * Creates a new constraint representing <tt>field &lt; value</tt>
     *
     * @param field the field to check
     * @param value the value to compare to
     * @return the newly constructed constraint
     */
    public static FieldOperator less(String field, Object value) {
        FieldOperator result = new FieldOperator(field);
        result.bound = Bound.LT;
        result.value = FieldEqual.transformFilterValue(value);

        return result;
    }

    /**
     * Creates a new constraint representing <tt>field &gt; value</tt>
     *
     * @param field the field to check
     * @param value the value to compare to
     * @return the newly constructed constraint
     */
    public static FieldOperator greater(String field, Object value) {
        FieldOperator result = new FieldOperator(field);
        result.bound = Bound.GT;
        result.value = FieldEqual.transformFilterValue(value);
        return result;
    }

    /**
     * Makes the filter include its limit.
     * <p>
     * Essentially this converts &lt; to &lt;= and &gt; to &gt;=
     *
     * @return the constraint itself for fluent method calls
     */
    public FieldOperator including() {
        if (bound == Bound.LT) {
            bound = Bound.LT_EQ;
        } else if (bound == Bound.GT) {
            bound = Bound.GT_EQ;
        }

        return this;
    }

    /**
     * Signals that this constraint is also fulfilled if the target field is empty.
     * <p>
     * This will convert this constraint into a filter.
     *
     * @return the constraint itself for fluent method calls
     */
    public FieldOperator orEmpty() {
        this.orEmpty = true;
        return this;
    }

    @Override
    public QueryBuilder createQuery() {
        if (value != null) {
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(field);
            if (bound == Bound.LT) {
                rangeQueryBuilder.lt(value);
            } else if (bound == Bound.LT_EQ) {
                rangeQueryBuilder.lte(value);
            } else if (bound == Bound.GT_EQ) {
                rangeQueryBuilder.gte(value);
            } else if (bound == Bound.GT) {
                rangeQueryBuilder.gt(value);
            }

            if (orEmpty) {
                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                boolQuery.should(rangeQueryBuilder);
                boolQuery.should(QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(field)));
                return boolQuery;
            }

            return rangeQueryBuilder;
        }
        return null;
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return field + " " + bound + " '" + (skipConstraintValues ? "?" : value) + "'";
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
