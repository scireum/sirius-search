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

public class Aggregation {
    private List<Aggregation> subAggregations = Lists.newArrayList();
    private String field;
    private String name;
    private String path;
    private int size = 50;

    public Aggregation(String name) {
        this.name = name;
    }

    public List<Aggregation> getSubAggregations() {
        return subAggregations;
    }

    public boolean hasSubAggregations() {
        return !subAggregations.isEmpty();
    }

    public void setSubAggregations(List<Aggregation> subAggregations) {
        this.subAggregations = subAggregations;
    }

    public void addSubAggregation(Aggregation aggregation) {
        subAggregations.add(aggregation);
    }

    public Aggregation on(String field) {
        this.field = field;
        return this;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
