/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.aggregation.bucket;

import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;

/**
 * Represents an aggregation used to filter for a specific value
 */
public class FilterAggregation extends BucketAggregation {

    private String value;

    private FilterAggregation(String name) {
        super(name);
    }

    /**
     * Used to generate a filter-aggregation with the given name and on the provided field
     *
     * @param field the field to be used
     * @param name  the name of the aggregation
     * @return a newly created filter-aggregation helper
     */
    public static FilterAggregation on(String field, String name) {
        FilterAggregation aggregation = new FilterAggregation(name);
        aggregation.field = field;
        return aggregation;
    }

    /**
     * Sets the value which is used for filtering
     *
     * @param value the used filter value
     * @return the filter-aggregation helper itself for fluent method calls
     */
    public FilterAggregation withValue(String value) {
        this.value = value;
        return this;
    }

    /**
     * @return the filter value
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Constructs the builder
     *
     * @return the filter-builder
     */
    public FilterAggregationBuilder getBuilder() {
        return AggregationBuilders.filter(getName()).filter(QueryBuilders.termQuery(getField(), getValue()));
    }
}
