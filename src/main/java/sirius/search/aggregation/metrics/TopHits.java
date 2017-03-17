/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.aggregation.metrics;

import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHitsAggregationBuilder;

/**
 * This aggregator is intended to be used as a sub aggregator, so that the top matching documents can be aggregated per
 * bucket.
 */
public class TopHits extends MetricsAggregation {

    private int from = 0;

    protected TopHits(String name) {
        super(name);
    }

    /**
     * Used to generate a top-hits-aggregation with the given name and field.
     *
     * @param name the name for the aggregation
     * @return the newly top-hits-aggregation helper for fluent method calls
     */
    public static TopHits on(String name) {
        return new TopHits(name);
    }

    /**
     * Sets the from parameter which can be used to page inside buckets.
     *
     * @param from the offset value
     * @return the top-hits-aggregation helper itself for fluent method calls
     */
    public TopHits withFrom(int from) {
        this.from = from;
        return this;
    }

    @Override
    public TopHitsAggregationBuilder getBuilder() {
        return AggregationBuilders.topHits(name).from(from).size(size);
    }
}
