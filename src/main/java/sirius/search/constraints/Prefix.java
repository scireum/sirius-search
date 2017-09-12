/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.PrefixQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.SpanQueryBuilder;
import sirius.kernel.commons.Strings;

/**
 * Represents a constraint which checks if the given field starts with the given value.
 * <p>
 * To prevent funny OutOfMemoryErrors the number of tokens being expanded is 256.
 */
public class Prefix implements Constraint, SpanConstraint {
    private final String field;
    private String value;
    private float boost = 1f;

    /*
     * Use the #on(String, Object) factory method
     */
    private Prefix(String field, String value) {
        this.field = field;
        this.value = value;
    }

    /**
     * Creates a new constraint for the given field and value.
     *
     * @param field the field to check
     * @param value the expected prefix to filter by
     * @return a new constraint representing the given filter setting
     */
    public static Prefix on(String field, String value) {
        return new Prefix(field, value);
    }

    /**
     * Sets the boost value that should be used for matching terms.
     *
     * @param boost the boost value
     * @return the constraint itself for fluent method calls
     */
    public Prefix withBoost(float boost) {
        this.boost = boost;
        return this;
    }

    @Override
    public QueryBuilder createQuery() {
        if (Strings.isFilled(value)) {
            return QueryBuilders.prefixQuery(field, value).rewrite("top_terms_256").boost(boost);
        }

        return null;
    }

    @Override
    public SpanQueryBuilder createSpanQuery() {
        if (createQuery() != null) {
            return QueryBuilders.spanMultiTermQueryBuilder((PrefixQueryBuilder) createQuery());
        }
        return null;
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return field + " STARTS-WITH '" + (skipConstraintValues ? "?" : value) + "'";
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
