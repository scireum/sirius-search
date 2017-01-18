/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.suggestion;

import java.util.List;
import java.util.Map;

/**
 * Implements the API for the elasticsearch completion-suggester
 */
public class AutoCompletion {

    /**
     * All terms for which should be searched/autocompleted
     */
    public static final String INPUT = "input";
    private List<String> input;

    /**
     * The term that should be returned, if one of the terms in input can be completed
     */
    public static final String OUTPUT = "output";
    private String output;

    /**
     * Used to filter, e.g. a catalog-id
     */
    public static final String CONTEXT = "context";
    private Map<String, List<String>> context;

    /**
     * Additional data can be stored to prevent further queries, e.g. an item-id
     */
    public static final String PAYLOAD = "payload";
    private Map<String, List<String>> payload;

    /**
     * Used for ranking
     */
    public static final String WEIGHT = "weight";
    private String weight;

    public List<String> getInput() {
        return input;
    }

    public void setInput(List<String> input) {
        this.input = input;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public Map<String, List<String>> getContext() {
        return context;
    }

    public void setContext(Map<String, List<String>> context) {
        this.context = context;
    }

    public Map<String, List<String>> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, List<String>> payload) {
        this.payload = payload;
    }

    public String getWeight() {
        return weight;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }
}

