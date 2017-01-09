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
 * An aggregation can be seen as a unit-of-work that builds analytic information over a set of documents. The context of
 * the execution defines what this document set is (e.g. a top-level aggregation executes within the context of the
 * executed query/filters of the search request).
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
     * Lists all sub aggregations.
     *
     * @return a list of all sub aggregations
     */
    public List<Aggregation> getSubAggregations() {
        return subAggregations;
    }

    /**
     * Determines if there are sub aggregations
     *
     * @return <tt>true</tt> if there are sub aggregations, <tt>false</tt> otherwise
     */
    public boolean hasSubAggregations() {
        return !subAggregations.isEmpty();
    }

    /**
     * Specifies the list of sub aggregations.
     *
     * @param subAggregations the list of sub aggegrations to use
     */
    public void setSubAggregations(List<Aggregation> subAggregations) {
        this.subAggregations = subAggregations;
    }

    /**
     * Adds a single sub aggregations.
     *
     * @param aggregation the sub aggregation to add
     */
    public void addSubAggregation(Aggregation aggregation) {
        subAggregations.add(aggregation);
    }

    /**
     * Generates a new aggregation on the given field.
     *
     * @param field specifies the field to build the aggregation on
     * @return the newly constructed aggregation
     */
    public Aggregation on(String field) {
        this.field = field;
        return this;
    }

    /**
     * Returns the field the aggregation was built on.
     *
     * @return the field the aggregation was built on
     */
    public String getField() {
        return field;
    }

    /**
     * Returns the name specified for this aggregation.
     *
     * @return the name of this aggregation
     */
    public String getName() {
        return name;
    }

    /**
     * Specifies the name of this aggregation.
     *
     * @param name the name of this aggregation.
     * @return the aggregation itself for fluent method calls
     */
    public Aggregation withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Determines the size (number of buckets). for this aggregation.
     *
     * @return the size of this aggregation.
     */
    public int getSize() {
        return size;
    }

    /**
     * Specifies the size (number of buckets) used for this aggregation.
     *
     * @param size the size of this aggregation
     * @return the aggregation itself for fluent method calls
     */
    public Aggregation withSize(int size) {
        this.size = size;
        return this;
    }

    /**
     * Determines the access path used when computing aggregations for inner values.
     *
     * @return the access path of the aggregation
     */
    public String getPath() {
        return path;
    }

    /**
     * Specifies the access path when creating an aggregation for an inner value (within a nested object).
     *
     * @param path the access path to use
     * @return the aggregation itself for fluent method calls
     */
    public Aggregation withPath(String path) {
        this.path = path;
        return this;
    }
}
