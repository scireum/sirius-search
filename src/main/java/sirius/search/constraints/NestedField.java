/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.SpanQueryBuilder;

/**
 * Represents a constraint which wraps constraints that should be applied to a nested field
 */
public class NestedField implements Constraint {

    private Constraint constraint;
    private String path;
    private ScoreMode scoreMode = ScoreMode.None;

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
    public static NestedField on(String path, Constraint constraint) {
        return new NestedField(path, constraint);
    }

    /**
     * Sets the score mode for this nested query
     *
     * @param scoreMode the used score mode
     * @return the constraint itself for fluent method calls
     */
    public NestedField scoreMode(ScoreMode scoreMode) {
        this.scoreMode = scoreMode;
        return this;
    }

    @Override
    public QueryBuilder createQuery() {
        return QueryBuilders.nestedQuery(path, constraint.createQuery(), scoreMode);
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return "NESTED[PATH=" + path + "] (" + constraint.toString() + ")";
    }
}
