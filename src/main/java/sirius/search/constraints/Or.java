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
import org.elasticsearch.index.query.SpanOrQueryBuilder;
import org.elasticsearch.index.query.SpanQueryBuilder;
import sirius.kernel.commons.Monoflop;

/**
 * Represents a set of constraints of which at least once must be fulfilled.
 */
public class Or implements Constraint, SpanConstraint {

    private Constraint[] constraints;

    /*
     * Use the #on(Constraint[]) factory method
     */
    private Or(Constraint... constraints) {
        this.constraints = constraints;
    }

    /**
     * Creates a new constraint where at least on of the given constraints must be fulfilled.
     *
     * @param constraints the constraints to group together
     * @return the newly created constraint
     */
    public static Or on(Constraint... constraints) {
        return new Or(constraints);
    }

    @Override
    public QueryBuilder createQuery() {
        BoolQueryBuilder result = QueryBuilders.boolQuery();
        for (Constraint constraint : constraints) {
            QueryBuilder qb = constraint.createQuery();
            if (qb != null) {
                result.should(qb);
            }
        }
        if (!result.hasClauses()) {
            return null;
        }

        return result;
    }

    @Override
    public SpanQueryBuilder createSpanQuery() {
        Monoflop mflop = Monoflop.create();
        SpanOrQueryBuilder builder = null;

        for (Constraint constraint : constraints) {
            if (constraint instanceof SpanConstraint) {
                SpanConstraint spanConstraint = (SpanConstraint) constraint;
                if (mflop.firstCall()) {
                    builder = QueryBuilders.spanOrQuery(spanConstraint.createSpanQuery());
                } else {
                    builder.addClause(spanConstraint.createSpanQuery());
                }
            } else {
                throw new UnsupportedOperationException(
                        "Or-Constraint contains a non span constraint, which is not allowed!");
            }
        }

        return builder;
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        StringBuilder sb = new StringBuilder("(");
        for (Constraint child : constraints) {
            if (sb.length() > 1) {
                sb.append(") OR (");
            }
            sb.append(child.toString(skipConstraintValues));
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
