/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field so that it can be used for fast query completion. Internally additional datastructures are
 * generated.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FastCompletion {
    /**
     * Allows to store additional data for completion, e.g. an entity-id. This can be used to save additional queries.
     */
    boolean payloads() default false;

    /**
     * The name for the context used to filter completions
     */
    String contextName();

    /**
     * The type of context. ElasticSearch provides "category" and "geo".
     */
    String contextType() default "category";
}
