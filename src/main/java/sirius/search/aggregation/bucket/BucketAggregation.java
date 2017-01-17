/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.aggregation.bucket;

import com.google.common.collect.Lists;
import sirius.search.aggregation.Aggregation;

import java.util.List;

/**
 * An aggregation that computes buckets for different values
 */
public abstract class BucketAggregation extends Aggregation {

    protected List<Aggregation> subAggregations = Lists.newArrayList();

    protected BucketAggregation(String name) {
        super(name);
    }

    /**
     * Returns all sub-aggregations.
     *
     * @return a list of all sub aggregations
     */
    public List<Aggregation> getSubAggregations() {
        return subAggregations;
    }

    /**
     * Determines if there are sub-aggregations.
     *
     * @return <tt>true</tt> if there are sub-aggregations, <tt>false</tt> otherwise
     */
    public boolean hasSubAggregations() {
        return !subAggregations.isEmpty();
    }

    /**
     * Adds a sub-aggregation for this aggregation
     *
     * @param aggregation the sub-aggreagtion that should be added
     * @return the aggregation helper itself used for fluent mehtod calls
     */
    public Aggregation addSubAggregation(Aggregation aggregation) {
        subAggregations.add(aggregation);
        return this;
    }
}
