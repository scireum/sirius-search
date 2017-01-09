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
 * Allows the nesting of objects and therefore better structuring.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NestedObject {
    /**
     * Contains the class that should be nested
     *
     * @return the class used to map the nested data
     */
    Class<?> value();

    /**
     * Specifies which analyzer to use for inner fields.
     *
     * @return the name of the analyzer to use for inner fields
     */
    String analyzer() default "whitespace";
}