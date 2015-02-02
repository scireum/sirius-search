/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.*;
import sirius.search.Entity;
import sirius.search.Index;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a constraint which verifies that the given field contains one of the given values.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
public class OneInField implements Constraint {

    private final Collection<?> values;
    private final String field;
    private boolean orEmpty = false;
    private boolean isFilter;
    private boolean forceEmpty = false;

    /*
     * Use the #on(List, String) factory method
     */
    private OneInField(Collection<?> values, String field) {
        if (values != null) {
            this.values = values.stream().filter(Objects::nonNull).collect(Collectors.toList());
        } else {
            this.values = null;
        }
        // In search queries the id field must be referenced via "_id" not "id..
        if (Entity.ID.equalsIgnoreCase(field)) {
            this.field = Index.ID_FIELD;
        } else {
            this.field = field;
        }
    }

    /**
     * Creates a new constraint which verifies that the given field contains one of the given values.
     *
     * @param values the values to check for
     * @param field  the field to check
     * @return the newly created constraint
     */
    public static OneInField on(Collection<?> values, String field) {
        return new OneInField(values, field);
    }

    /**
     * Signals that this constraint is also fulfilled if the target field is empty.
     * <p>
     * This will convert this constraint into a filter.
     *
     * @return the constraint itself for fluent method calls
     */
    public OneInField orEmpty() {
        asFilter();
        orEmpty = true;
        return this;
    }

    /**
     * Signals that an empty input list is not ignored but enforces the target field to be empty.
     * <p>
     * This will convert this constraint into a filter.
     *
     * @return the constraint itself for fluent method calls
     */
    public OneInField forceEmpty() {
        asFilter();
        orEmpty = true;
        forceEmpty = true;
        return this;
    }

    /**
     * Forces this constraint to be applied as filter not as query.
     *
     * @return the constraint itself for fluent method calls
     */
    public OneInField asFilter() {
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
                boolQueryBuilder.should(QueryBuilders.termQuery(field, FieldOperator.convertJava8Times(value)));
            }
            return boolQueryBuilder;
        }
        return null;
    }

    @Override
    public FilterBuilder createFilter() {
        if (isFilter) {
            if (values == null) {
                return null;
            }
            BoolFilterBuilder boolFilterBuilder = FilterBuilders.boolFilter();
            if (values.isEmpty()) {
                if (forceEmpty) {
                    boolFilterBuilder.should(FilterBuilders.missingFilter(field));
                    return boolFilterBuilder;
                }
                return null;
            }
            for (Object value : values) {
                boolFilterBuilder.should(FilterBuilders.termFilter(field, FieldOperator.convertJava8Times(value)));
            }
            if (orEmpty) {
                boolFilterBuilder.should(FilterBuilders.missingFilter(field));
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
        return "'" + (skipConstraintValues ? "?" : values) + "' IN " + field;
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
