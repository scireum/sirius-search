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
 * Represents a constraint which wraps constraints that should be applied to a nested field
 */
public class NestedField implements Constraint {

    private Constraint constraint;
    private String path;

    /*
     * Use the #on(Constraint[]) factory method
     */
    private NestedField(String path, Constraint constraint) {
        this.path = path;
        this.constraint = constraint;
    }

    /**
     * Creates a new constraint for nested fields as they must be wrapped in a special query type
     *
     * @param constraint the constraint that contains a nested field
     * @param path       the path of the nested field
     * @return the newly created constraint
     */
    public static Constraint on(String path, Constraint constraint) {
        return new NestedField(path, constraint);
    }

    @Override
    public QueryBuilder createQuery() {
        return QueryBuilders.nestedQuery(path, constraint.createQuery());
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return "NESTED[PATH=" + path + "] (" + constraint.toString() + ")";
    }
}
