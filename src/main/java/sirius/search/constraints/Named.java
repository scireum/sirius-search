/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.QueryBuilder;

/**
 * Permits to define a name for a (sub-)query which allows to check whether a (sub-)query matched on an entity
 * using {@link sirius.search.Entity#isMatchedNamedQuery(String)}.
 */
public class Named implements Constraint {
    private Constraint constraint;
    private String queryName;

    private Named(Constraint constraint) {
        this.constraint = constraint;
    }

    /**
     * Creates a new constraint which defines a query name for the child constraint.
     *
     * @param constraint the constraint which should be named
     * @param queryName  the name which should be used
     * @return the newly created constraint
     */
    public static Named of(Constraint constraint, String queryName) {
        Named named = new Named(constraint);
        named.queryName = queryName;
        return named;
    }

    @Override
    public QueryBuilder createQuery() {
        return constraint.createQuery().queryName(queryName);
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return constraint.toString(skipConstraintValues) + " WITH QUERY_NAME('" + queryName + "')";
    }
}
