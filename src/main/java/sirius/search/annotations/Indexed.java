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
 * Marks an entity class as persistent. Permits to specify additional metadata on how the values are stored in
 * ElasticSearch
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Indexed {
    /**
     * Contains the name of the ElasticSearch index in which the entities are stored
     *
     * @return the name of the index used to store the entities
     */
    String index();

    /**
     * The type name used for the entities of the class.
     *
     * @return the type name used for entities of the given class. If left empty (default), the simple name of the
     * class is used
     */
    String type() default "";

    /**
     * Determines the field used for custom routing.
     *
     * @return the field name used for custom routing (Note that routing values for search requests need to be
     * specified manually).
     */
    String routing() default "";

    /**
     * Determines the framework this entity belongs to. If a non empty string is given, the entity is only loaded, if
     * {@link sirius.kernel.Sirius#isFrameworkEnabled(String)} returns <tt>true</tt> for the given framework.
     *
     * @return the framework which must be enabled in order to load this entity class
     */
    String framework() default "";

}
