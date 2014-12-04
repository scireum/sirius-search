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
 * Marks the given field as unique. This indicates that each value must occur at most once - either globally or,
 * within the same value of the given field <tt>within</tt>.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Unique {
    /**
     * Looses the uniqueness restriction so that a value must be only unique for all other entities with the same value
     * for <tt>within</tt> as this entity.
     *
     * @return the second field within the value must be unique
     */
    String within() default "";
}
