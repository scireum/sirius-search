/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.aggregation.bucket;

import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

/**
 * Represents an aggregation using terms
 */
public class Term extends BucketAggregation {

    private Boolean orderAsc = null;

    private Term(String name) {
        super(name);
    }

    /**
     * Used to generate a termaggregation with the given name and on the provided field
     *
     * @param field the field to be used
     * @param name  the name of this aggregation
     * @return a newly created termaggregation helper
     */
    public static Term on(String field, String name) {
        Term aggregation = new Term(name);
        aggregation.field = field;
        return aggregation;
    }

    /**
     * Sets ordering by {@link #field} to ascending.
     *
     * @return the term aggregation helper itself for fluent method calls
     */
    public Term orderAsc() {
        orderAsc = true;
        return this;
    }

    /**
     * Sets ordering by {@link #field} to descending.
     *
     * @return the term aggregation helper itself for fluent method calls
     */
    public Term orderDesc() {
        orderAsc = false;
        return this;
    }

    /**
     * Constructs the termbuilder
     *
     * @return the termbuilder
     */
    @Override
    public TermsAggregationBuilder getBuilder() {
        TermsAggregationBuilder builder = AggregationBuilders.terms(getName()).field(getField()).size(getSize());

        if (orderAsc != null) {
            builder.order(Terms.Order.term(orderAsc));
        }

        return builder;
    }
}
