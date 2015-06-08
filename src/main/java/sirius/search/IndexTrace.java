/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.kernel.commons.Tuple;

import java.util.List;

/**
 * Used to trace optimistic lock errors.
 * <p>
 * Contains all relevant information recorded for a change event.
 */
class IndexTrace {
    String id;
    String type;
    String threadName;
    StackTraceElement[] stackTrace;
    long timestamp;
    List<Tuple<String, String>> mdc;
}
