/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;


/**
 * Represents a relational filter which can be used to filter &lt; or &lt;=, along with &gt; or &gt;=
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2014/02
 */
public class FieldOperator implements Constraint {

    private enum Bound {
        LT, LT_EQ, GT, GT_EQ
    }

    private String field;
    private Object value;
    private Bound bound;
    private boolean isFilter;
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
        result.value = convertJava8Times(value);

        return result;
    }

    /*
     * Converts Java 8 Time API objects into ISO strings accepted by ES (Joda-Time)
     */
    protected static Object convertJava8Times(Object value) {
        if (value != null && value instanceof Instant) {
            value = LocalDateTime.ofInstant((Instant) value, ZoneId.systemDefault());
        }
        if (value != null && value instanceof TemporalAccessor) {
            if (((TemporalAccessor) value).isSupported(ChronoField.HOUR_OF_DAY)) {
                return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format((TemporalAccessor) value);
            } else {
                return DateTimeFormatter.ISO_LOCAL_DATE.format((TemporalAccessor) value);
            }
        }

        return value;
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
        result.value = convertJava8Times(value);
        return result;
    }

    /**
     * Makes the filter include its limit.
     * <p>
     * Essentially this converts &lt; to &lt;= and &gt; to &gt;=
     * </p>
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
     * </p>
     *
     * @return the constraint itself for fluent method calls
     */
    public FieldOperator orEmpty() {
        asFilter();
        this.orEmpty = true;

        return this;
    }

    /**
     * Forces this constraint to be applied as filter not as query.
     *
     * @return the constraint itself for fluent method calls
     */
    public FieldOperator asFilter() {
        isFilter = true;
        return this;
    }

    @Override
    public QueryBuilder createQuery() {
        if (!isFilter && !orEmpty && value != null) {
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

            return rangeQueryBuilder;
        }
        return null;
    }

    @Override
    public FilterBuilder createFilter() {
        if (isFilter && value != null) {
            if (orEmpty) {
                BoolFilterBuilder boolFilterBuilder = FilterBuilders.boolFilter();
                if (bound == Bound.LT) {
                    boolFilterBuilder.should(FilterBuilders.rangeFilter(field).lt(value));
                } else if (bound == Bound.LT_EQ) {
                    boolFilterBuilder.should(FilterBuilders.rangeFilter(field).lte(value));
                } else if (bound == Bound.GT_EQ) {
                    boolFilterBuilder.should(FilterBuilders.rangeFilter(field).gte(value));
                } else if (bound == Bound.GT) {
                    boolFilterBuilder.should(FilterBuilders.rangeFilter(field).gt(value));
                }
                boolFilterBuilder.should(FilterBuilders.missingFilter(field));
                return boolFilterBuilder;
            } else {
                if (bound == Bound.LT) {
                    return FilterBuilders.rangeFilter(field).lt(value);
                } else if (bound == Bound.LT_EQ) {
                    return FilterBuilders.rangeFilter(field).lte(value);
                } else if (bound == Bound.GT_EQ) {
                    return FilterBuilders.rangeFilter(field).gte(value);
                } else if (bound == Bound.GT) {
                    return FilterBuilders.rangeFilter(field).gt(value);
                }
            }
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
