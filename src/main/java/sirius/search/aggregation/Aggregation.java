/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.aggregation;

import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;

/**
 * An aggregation can be seen as a unit-of-work that builds analytic information over a set of documents. The context of
 * the execution defines what this document set is (e.g. a top-level aggregation executes within the context of the
 * executed query/filters of the search request).
 */
public abstract class Aggregation {

    protected String field;
    protected String name;
    protected String path;
    protected int size = 50;

    protected Aggregation(String name) {
        this.name = name;
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
     * Returns the field used by this aggregation.
     *
     * @return the used field by this aggregation
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

    /**
     * Constructs the builder for the specific aggregation
     *
     * @return the aggregationbuilder
     */
    public abstract AbstractAggregationBuilder<?> getBuilder();
}
