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
     * The names for the contexts used to filter completions
     *
     * @return the names of the contexts used
     */
    String[] contextNames() default "";

    /**
     * The types of contexts. ElasticSearch provides "category" and "geo".
     *
     * @return the types of the contexts
     */
    String[] contextTypes() default "category";
}
