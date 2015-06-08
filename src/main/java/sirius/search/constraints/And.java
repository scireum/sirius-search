/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import sirius.kernel.commons.Strings;

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
        boolean queriesFound = false;
        boolean filtersFound = false;
        for (Constraint constraint : constraints) {
            QueryBuilder qb = constraint.createQuery();
            if (qb != null) {
                queriesFound = true;
                result.must(qb);
            }
            FilterBuilder fb = constraint.createFilter();
            if (fb != null) {
                filtersFound = true;
            }
        }
        if (!result.hasClauses()) {
            return null;
        }
        if (!queriesFound) {
            return null;
        }
        if (filtersFound) {
            throw new IllegalArgumentException(Strings.apply(
                    "You must not mix filters and queries in an AND constraint! %s",
                    this));
        }

        return result;
    }

    @Override
    public FilterBuilder createFilter() {
        BoolFilterBuilder result = FilterBuilders.boolFilter();
        boolean queriesFound = false;
        boolean filtersFound = false;

        for (Constraint constraint : constraints) {
            FilterBuilder fb = constraint.createFilter();
            if (fb != null) {
                filtersFound = true;
                result.must(fb);
            }
            QueryBuilder qb = constraint.createQuery();
            if (qb != null) {
                queriesFound = true;
            }
        }
        if (!filtersFound) {
            return null;
        }
        if (queriesFound) {
            throw new IllegalArgumentException(Strings.apply(
                    "You must not mix filters and queries in an AND constraint! %s",
                    this));
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
