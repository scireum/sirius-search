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
 * Specifies the type used in {@link java.util.Map} fields.
 * <p>
 * The most common example would by a map that maps strings to strings. As generics are removed at compile time, this
 * annotation is required to provide the type of the list to select the matching {@link
 * sirius.search.properties.PropertyFactory}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MapType {

    /**
     * Contains the class of the elements kept in the list field
     *
     * @return the element class of the annotated list field
     */
    Class<?> value();

    /**
     * Determines the mapping type in elasticsearch. "object" (<tt>false</tt>) is faster than "nested", but does not
     * allow searching for key-value pairs (it would match documents where the key <strong>or</strong> the value match).
     *
     * @return whether to use "nested" or "object" as mapping type
     */
    boolean nested() default false;
}
