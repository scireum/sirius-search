/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

/**
 * Handles default values for boolean options in elasticsearch
 */
public enum ESOption {

    /**
     * send <tt>true</tt> to elasticsearch as the value for this option
     */
    TRUE("true"),

    /**
     * send <tt>false</tt> to elasticsearch as the value for this option
     */
    FALSE("false"),

    /**
     * send no value to elasticsearch, hence using the default value of elasticserach
     */
    ES_DEFAULT(""),

    /**
     * send the default value of the {@link Property} to elasticsearch
     */
    DEFAULT("");

    private final String value;

    ESOption(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return getValue();
    }
}
