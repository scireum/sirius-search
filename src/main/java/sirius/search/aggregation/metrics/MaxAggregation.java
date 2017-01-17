/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.aggregation.metrics;

import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.max.MaxBuilder;

/**
 * Represents an aggregation that determines the maximum value of a field
 */
public class MaxAggregation extends MetricsAggregation {

    private MaxAggregation(String name) {
        super(name);
    }

    /**
     * Used to generate a max-aggregation with the given name and on the provided field
     *
     * @param field the field to be used
     * @param name  the name of the aggregation
     * @return the max-aggregation helper itself for fluent method calls
     */
    public static MaxAggregation on(String field, String name) {
        MaxAggregation aggregation = new MaxAggregation(name);
        aggregation.field = field;
        return aggregation;
    }

    /**
     * Constructs the builder
     *
     * @return the max-builder
     */
    public MaxBuilder getBuilder() {
        return AggregationBuilders.max(getName()).field(getField());
    }
}
