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
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an aggregation using terms
 */
public class Term extends BucketAggregation {

    private List<Tuple<String, Boolean>> orderBys = new ArrayList<>();

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
        orderBys.add(Tuple.create(field, true));
        return this;
    }

    /**
     * Sets ordering by {@link #field} to descending.
     *
     * @return the term aggregation helper itself for fluent method calls
     */
    public Term orderDesc() {
        orderBys.add(Tuple.create(field, false));
        return this;
    }

    /**
     * Sets the ordering by a contained aggregation to descending.
     *
     * @param aggregationName the name of the aggregation which should be used for ordering
     * @return the term aggregation helper itself for fluent method calls
     */
    public Term orderDesc(String aggregationName) {
        orderBys.add(Tuple.create(aggregationName, false));
        return this;
    }

    /**
     * Sets the ordering by a contained aggregation to ascending.
     *
     * @param aggregationName the name of the aggregation which should be used for ordering
     * @return the term aggregation helper itself for fluent method calls
     */
    public Term orderAsc(String aggregationName) {
        orderBys.add(Tuple.create(aggregationName, true));
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

        if (!this.orderBys.isEmpty()) {
            List<Terms.Order> orders = new ArrayList<>();

            for (Tuple<String, Boolean> orderBy : this.orderBys) {
                if (Strings.areEqual(orderBy.getFirst(), field)) {
                    orders.add(Terms.Order.term(orderBy.getSecond()));
                } else {
                    orders.add(Terms.Order.aggregation(orderBy.getFirst(), orderBy.getSecond()));
                }
            }

            builder.order(orders);
        }


        return builder;
    }
}
