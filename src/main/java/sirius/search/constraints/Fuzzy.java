/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.SpanQueryBuilder;

/**
 * Represents a constraint that allows fuzzy searching on a field for compensating misspellings
 * <p>
 * This constraint allows to match values that differ slightly from the provided value
 */
public class Fuzzy implements Constraint, SpanConstraint {

    private final String field;
    private String value;
    private Fuzziness fuzziness = Fuzziness.AUTO;
    private float boost = 1f;

    private Fuzzy(String field, String value) {
        this.field = field;
        this.value = value;
    }

    /**
     * Creates a new fuzzy query for the given field and value.
     * <p>
     * Use {@link #fuzziness(Fuzziness)} to specify the fuzziness of the value. Otherwise {@link Fuzziness#AUTO} is
     * used.
     *
     * @param field the field to search in
     * @param value the value to filter on.
     * @return the newly created constraint
     */
    public static Fuzzy on(String field, String value) {
        return new Fuzzy(field, value);
    }

    /**
     * Specifies the fuzziness to use.
     *
     * @param value the fuzziness to use
     * @return the contraint itself for fluent method calls
     */
    public Fuzzy fuzziness(Fuzziness value) {
        this.fuzziness = value;
        return this;
    }

    /**
     * Sets the boost value that should be used for matching terms.
     *
     * @param boost the boost value
     * @return the constraint itself for fluent method calls
     */
    public Fuzzy withBoost(float boost) {
        this.boost = boost;
        return this;
    }

    @Override
    public QueryBuilder createQuery() {
        return QueryBuilders.matchQuery(field, value).fuzziness(fuzziness).boost(boost);
    }

    @Override
    public SpanQueryBuilder createSpanQuery() {
        return QueryBuilders.spanMultiTermQueryBuilder(QueryBuilders.fuzzyQuery(field, value)
                                                                    .fuzziness(fuzziness)
                                                                    .boost(boost));
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return field + " LIKE (Fuzzy=" + fuzziness + ") '" + (skipConstraintValues ? "?" : value) + "'";
    }
}
