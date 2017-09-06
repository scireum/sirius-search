/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.SpanNearQueryBuilder;
import org.elasticsearch.index.query.SpanQueryBuilder;
import sirius.kernel.commons.Monoflop;

/**
 * Represents a constraint, which allows to specify the maximal distance (slop) between sub constraints.
 * This can e.g. be used to define a query, which searches for the words "word1" and "word2" and between those values
 * mustn't be more than 3 (the defined slop) other words.
 */
public class NearSpan implements Constraint, SpanConstraint {

    private SpanConstraint[] constraints;
    private int slop = 3;
    private boolean inOrder = false;
    private float boost = 1f;

    /*
     * Use the #on(Constraint...) factory method
     */
    private NearSpan(SpanConstraint[] constraints) {
        this.constraints = constraints;
    }

    /**
     * Creates a new near span query for the given sub constraints.
     * <p>
     * Use {@link #slop(int)} to define the slop for this constraint. Otherwise a default of <tt>3</tt> is used.
     * Use {@link #inOrder()} if the terms should be found in the given order. Otherwise <tt>false</tt> is used.
     *
     * @param constraints the given sub constraints that should be wrapped in a near span constraint
     * @return the newly created near span constraint
     */
    public static NearSpan of(SpanConstraint... constraints) {
        return new NearSpan(constraints);
    }

    /**
     * Sets the slop for this constraint.
     *
     * @param slop the slop that should be used
     * @return the contraint itself for fluent method calls
     */
    public NearSpan slop(int slop) {
        this.slop = slop;
        return this;
    }

    /**
     * Sets whether the sub constraints should be found in the given order.
     *
     * @return the contraint itself for fluent method calls
     */
    public NearSpan inOrder() {
        this.inOrder = true;
        return this;
    }

    public NearSpan boost(float boost) {
        this.boost = boost;
        return this;
    }

    @Override
    public SpanNearQueryBuilder createQuery() {
        Monoflop mflop = Monoflop.create();
        SpanNearQueryBuilder builder = null;

        for (SpanConstraint constraint : constraints) {
            if (mflop.firstCall()) {
                builder = QueryBuilders.spanNearQuery(constraint.createSpanQuery(), slop).inOrder(inOrder).boost(boost);
            } else {
                builder.addClause(constraint.createSpanQuery()).inOrder(inOrder).boost(boost);
            }
        }
        return builder;
    }

    @Override
    public SpanQueryBuilder createSpanQuery() {
        return createQuery();
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        StringBuilder sb = new StringBuilder("(");
        for (SpanConstraint child : constraints) {
            if (sb.length() > 1) {
                sb.append(") NEAR [slop=").append(slop).append("] (");
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
