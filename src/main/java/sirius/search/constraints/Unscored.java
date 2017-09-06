/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

/**
 * Represents a constraint which marks the wrapped constraint so that it is not considered during score calculation.
 */
public class Unscored implements Constraint {

    private Constraint constraint;

    public static Unscored of(Constraint constraint) {
        Unscored unscored = new Unscored();
        unscored.constraint = constraint;
        return unscored;
    }

    @Override
    public QueryBuilder createQuery() {
        return QueryBuilders.boolQuery().filter(constraint.createQuery());
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return "Unscored ( " + constraint.toString(skipConstraintValues) + " )";
    }
}
