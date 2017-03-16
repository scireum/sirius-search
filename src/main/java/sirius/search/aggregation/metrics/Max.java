/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.aggregation.metrics;

import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.max.MaxAggregationBuilder;

/**
 * Represents an aggregation that determines the maximum value of a field
 */
public class Max extends MetricsAggregation {

    private Script script;

    private Max(String name) {
        super(name);
    }

    /**
     * Used to generate a max-aggregation with the given name and on the provided field
     *
     * @param field the field to be used
     * @param name  the name of the aggregation
     * @return the max-aggregation helper itself for fluent method calls
     */
    public static Max on(String field, String name) {
        Max aggregation = new Max(name);
        aggregation.field = field;
        return aggregation;
    }

    /**
     * Used to generate a max-aggregation with the given name and script.
     *
     * @param name   the name of the aggregation
     * @param script the script that should be executed
     * @return the max-aggregation helper itself for fluent method calls
     */
    public static Max withScript(String name, Script script) {
        Max aggregation = new Max(name);
        aggregation.script = script;
        return aggregation;
    }

    /**
     * Constructs the builder
     *
     * @return the max-builder
     */
    @Override
    public MaxAggregationBuilder getBuilder() {
        if (script == null) {
            return AggregationBuilders.max(getName()).field(getField());
        } else {
            return AggregationBuilders.max(getName()).script(script);
        }
    }
}
