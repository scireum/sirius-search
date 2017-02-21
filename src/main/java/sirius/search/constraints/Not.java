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
 * Negates the given constraint.
 */
public class Not implements Constraint {

    private Constraint inner;

    /*
     * Use the #on(Constraint) factory method
     */
    private Not(Constraint inner) {
        this.inner = inner;
    }

    /**
     * Creates a new constraint with negates the given inner constraint
     *
     * @param inner the constraint to negate
     * @return the newly created constraint
     */
    public static Constraint on(Constraint inner) {
        return new Not(inner);
    }

    @Override
    public QueryBuilder createQuery() {
        QueryBuilder innerQuery = inner.createQuery();
        if (innerQuery == null) {
            return null;
        }
        BoolQueryBuilder qb = QueryBuilders.boolQuery();
        qb.mustNot(innerQuery);
        return qb;
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return "!" + inner.toString(skipConstraintValues);
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
