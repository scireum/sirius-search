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

/**
 * Can be used to combine multiple filters. This results in filters like:
 * (field1 = "value1" OR field1 = "value2" OR ... ) AND (field2 = "value3" OR ...)
 */
public class Filters extends BucketAggregation {

    private Filter[] filters;

    protected Filters(String name) {
        super(name);
    }

    /**
     * Sets the filters that should be combined using must-clauses
     *
     * @param filters the filters that should be combined
     */
    public static Filters of(Filter... filters) {
        Filters multipleFilters = new Filters("");
        multipleFilters.filters = filters;
        return multipleFilters;
    }

    /**
     * Constructs the builder by combining all sub-filters with must-clauses
     *
     * @return the filter-aggregation builder
     */
    @Override
    public FilterAggregationBuilder getBuilder() {
        BoolQueryBuilder filtersQuery = QueryBuilders.boolQuery();

        for (Filter filter : filters) {
            filtersQuery.must(filter.getQueryBuilder());
        }

        return AggregationBuilders.filter(getName()).filter(filtersQuery);
    }
}
