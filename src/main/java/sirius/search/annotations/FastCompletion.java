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
     * The name for the context used to filter completions
     *
     * @return the name of the context used
     */
    String contextName() default "";

    /**
     * The type of context. ElasticSearch provides "category" and "geo".
     *
     * @return the type of the context
     */
    String contextType() default "category";
}
