/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.aggregation.bucket;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an aggregation used to filter for a specific value
 */
public class Filter extends BucketAggregation {

    private List<String> filters;

    private Filter(String name) {
        super(name);
    }

    /**
     * Used to generate a filter-aggregation with the given name and on the provided field
     *
     * @param field the field to be used
     * @param name  the name of the aggregation
     * @return a newly created filter-aggregation helper
     */
    public static Filter on(String field, String name) {
        Filter aggregation = new Filter(name);
        aggregation.field = field;
        return aggregation;
    }

    /**
     * Sets the value which is used for filtering
     *
     * @param value the used filter value
     * @return the filter-aggregation helper itself for fluent method calls
     */
    public Filter withValue(String value) {
        filters = new ArrayList<>();
        this.filters.add(value);
        return this;
    }

    /**
     * Sets the values which are used for filtering
     *
     * @param values the used filter values
     * @return the filter-aggregation helper itself for fluent method calls
     */
    public Filter withValues(List<String> values) {
        this.filters = values;
        return this;
    }

    /**
     * @return the filter values
     */
    public List<String> getValue() {
        return this.filters;
    }

    /**
     * Constructs the builder
     *
     * @return the filter-builder
     */
    public FilterAggregationBuilder getBuilder() {
        BoolQueryBuilder filterQuery = QueryBuilders.boolQuery();

        for (String filter : filters) {
            filterQuery.should(QueryBuilders.termQuery(getField(), filter));
        }

        return AggregationBuilders.filter(getName()).filter(filterQuery);
    }
}
