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
public class NearSpan implements Constraint {

    private Constraint[] constraints;
    private int slop = 3;
    private boolean inOrder= false;

    /*
     * Use the #on(Constraint...) factory method
     */
    private NearSpan(Constraint[] constraints) {
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
    public static NearSpan of(Constraint... constraints) {
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
    public NearSpan inOrder(){
        this.inOrder = true;
        return this;
    }

    @Override
    public SpanNearQueryBuilder createQuery() {
        Monoflop mflop = Monoflop.create();
        SpanNearQueryBuilder builder = null;

        for (Constraint constraint : constraints) {
            if (mflop.firstCall()) {
                builder = QueryBuilders.spanNearQuery(constraint.createSpanQuery(), slop);
            } else {
                builder.addClause(constraint.createSpanQuery());
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
        return null;
    }
}
