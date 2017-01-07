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
import sirius.search.Entity;
import sirius.search.IndexAccess;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Represents a constraint which verifies that the given field contains one of the given values.
 */
public class OneInField implements Constraint {

    private final Collection<?> values;
    private final String field;
    private boolean orEmpty = false;
    private boolean forceEmpty = false;

    /*
     * Use the #on(List, String) factory method
     */
    private OneInField(Collection<?> values, String field) {
        if (values != null) {
            //noinspection RedundantCast - Otherwise javac might blow up occasionally :-/
            this.values = values.stream()
                                .filter(Objects::nonNull)
                                .collect((Collector<Object, ?, List<Object>>) Collectors.toList());
        } else {
            this.values = null;
        }
        // In search queries the id field must be referenced via "_id" not "id..
        if (Entity.ID.equalsIgnoreCase(field)) {
            this.field = IndexAccess.ID_FIELD;
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
        orEmpty = true;
        forceEmpty = true;
        return this;
    }

    @Override
    public QueryBuilder createQuery() {
        if (values == null || values.isEmpty()) {
            if (forceEmpty) {
                return QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(field));
            }
            return null;
        }
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        for (Object value : values) {
            boolQueryBuilder.should(QueryBuilders.termQuery(field, FieldEqual.transformFilterValue(value)));
        }
        if (orEmpty) {
            boolQueryBuilder.should(QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(field)));
        }
        return boolQueryBuilder;
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
