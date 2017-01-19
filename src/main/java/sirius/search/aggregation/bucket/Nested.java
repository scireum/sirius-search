/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.aggregation.bucket;

import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;

/**
 * An Aggregation that is needed for nested objects
 */
public class Nested extends BucketAggregation {

    protected Nested(String name) {
        super(name);
    }

    /**
     * Used to generate a nested-aggregation with the given name and path
     *
     * @param path the path to be used
     * @param name the name of the aggregation
     * @return a newly created nested-aggregation helper
     */
    public static Nested on(String name, String path) {
        Nested nested = new Nested(name);
        nested.path = path;
        return nested;
    }

    @Override
    public AbstractAggregationBuilder getBuilder() {
        return AggregationBuilders.nested(getName()).path(getPath());
    }
}
