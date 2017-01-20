/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.aggregation.metrics;

import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.min.MinBuilder;

/**
 * Represents an aggregation that determines the minimal value of a field
 */
public class Min extends MetricsAggregation {

    private Min(String name) {
        super(name);
    }

    /**
     * Used to generate a min-aggregation with the given name and on the provided field
     *
     * @param field the field to be used
     * @param name  the name of the aggregation
     * @return the min-aggregation helper itself for fluent method calls
     */
    public static Min on(String field, String name) {
        Min aggregation = new Min(name);
        aggregation.field = field;
        return aggregation;
    }

    /**
     * Constructs the builder
     *
     * @return the min-builder
     */
    @Override
    public MinBuilder getBuilder() {
        return AggregationBuilders.min(getName()).field(getField());
    }
}
