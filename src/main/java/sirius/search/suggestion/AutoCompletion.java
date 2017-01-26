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
     * Used to filter, e.g. a catalog-id
     */
    public static final String CONTEXT = "context";
    private Map<String, List<String>> contexts;

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

    public Map<String, List<String>> getContext() {
        return contexts;
    }

    public void setContext(Map<String, List<String>> contexts) {
        this.contexts = contexts;
    }

    public String getWeight() {
        return weight;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }
}

