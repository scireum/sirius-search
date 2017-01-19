
/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.aggregation.metrics;

import sirius.search.aggregation.Aggregation;

/**
 * An Aggregation that computes single values instead of buckets
 */
public abstract class MetricsAggregation extends Aggregation {

    protected MetricsAggregation(String name) {
        super(name);
    }

}