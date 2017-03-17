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

/**
 * Represents a set of constraints of which every one must be fulfilled.
 */
public class And implements Constraint {

    private Constraint[] constraints;

    /*
     * Use the #on(Constraint[]) factory method
     */
    private And(Constraint... constraints) {
        this.constraints = constraints;
    }

    /**
     * Creates a new constraint where every one of the given constraints must be fulfilled.
     *
     * @param constraints the constraints to group together
     * @return the newly created constraint
     */
    public static Constraint on(Constraint... constraints) {
        return new And(constraints);
    }

    @Override
    public QueryBuilder createQuery() {
        BoolQueryBuilder result = QueryBuilders.boolQuery();
        for (Constraint constraint : constraints) {
            QueryBuilder qb = constraint.createQuery();
            if (qb != null) {
                result.must(qb);
            }
        }
        if (!result.hasClauses()) {
            return null;
        }

        return result;
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        StringBuilder sb = new StringBuilder("(");
        for (Constraint child : constraints) {
            if (sb.length() > 1) {
                sb.append(") AND (");
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
