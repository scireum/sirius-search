/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * Provides access to the aggregation functionality of elasticsearch
 */
public class Aggregation {

    private List<Aggregation> subAggregations = Lists.newArrayList();
    private String field;
    private String name;
    private String path;
    private int size = 50;

    private Aggregation(String name) {
        this.name = name;
    }

    /**
     * Sets the field and name used for the aggregation.
     *
     * @param field the used field
     * @param name  name of the aggregation
     * @return a newly created aggregation helper
     */
    public static Aggregation on(String field, String name) {
        Aggregation aggregation = new Aggregation(name);
        aggregation.field = field;
        return aggregation;
    }

    /**
     * Restricts the number of generated values
     *
     * @param size the max. number of values returned
     * @return the aggregation helper itself used for fluent mehtod calls
     */
    public Aggregation size(int size) {
        this.size = size;
        return this;
    }

    /**
     * Sets the path for this aggregation
     *
     * @param path the path that should be used
     * @return the aggregation helper itself used for fluent mehtod calls
     */
    public Aggregation path(String path) {
        this.path = path;
        return this;
    }

    /**
     * @return all sub-aggregations for this aggregation
     */
    public List<Aggregation> getSubAggregations() {
        return subAggregations;
    }

    /**
     * @return true if sub-aggregations are available else otherwise
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

    /**
     * @return the used field by this aggregation
     */
    public String getField() {
        return field;
    }

    /**
     * @return the used name by this aggregation
     */
    public String getName() {
        return name;
    }

    /**
     * @return the used path by this aggregation
     */
    public String getPath() {
        return path;
    }

    /**
     * @return the used size by this aggregation
     */
    public int getSize() {
        return size;
    }
}
