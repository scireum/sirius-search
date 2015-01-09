/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import java.lang.reflect.Field;

/**
 * Describes a property factory which generates a {@link Property} for a given {@link Field}.
 * <p>
 * When scanning a class to compute its {@link sirius.search.EntityDescriptor}, for each field each
 * <tt>PropertyFactory</tt> is queried. The first to return <tt>true</tt> as result of
 * {@link #accepts(java.lang.reflect.Field)} will be used to compute the property fro a field by calling
 * {@link #create(java.lang.reflect.Field)}.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
public interface PropertyFactory {

    /**
     * Determines if the given field is handled by this property factory.
     *
     * @param field the field to create a property from
     * @return <tt>true</tt> if the factory can create a property for the given field, <tt>false</tt> otherwise
     */
    boolean accepts(Field field);

    /**
     * Computes a {@link Property} for the given field.
     *
     * @param field the field to create a property from
     * @return a property handing marshalling etc. for the given field and its values
     */
    Property create(Field field);
}
