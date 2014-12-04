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
 * Specifies the type used in {@link java.util.List} fields.
 * <p>
 * The most common example would by a list of strings. As generics a removed at compile time, this annotation is
 * required to provide the type of the list to select the matching {@link sirius.search.properties.PropertyFactory}
 * </p>
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ListType {
    /**
     * Contains the class of the elements kept in the list field
     *
     * @return the element class of the annotated list field
     */
    Class<?> value();
}
