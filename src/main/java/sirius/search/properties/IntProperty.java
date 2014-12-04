/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;


import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import java.lang.reflect.Field;

/**
 * Represents an integer property for fields of type <tt>int</tt> or <tt>Integer</tt>.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
public class IntProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return Integer.class.equals(field.getType()) || int.class.equals(field.getType());
        }

        @Override
        public Property create(Field field) {
            return new IntProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private IntProperty(Field field) {
        super(field);
    }

    @Override
    protected String getMappingType() {
        return "integer";
    }

    @Override
    protected Object transformFromSource(Object value) {
        if (value != null) {
            if (value instanceof Long) {
                value = ((Long) value).intValue();
            }
            if (value instanceof String && Strings.isFilled(value)) {
                try {
                    value = Integer.parseInt((String)value);
                } catch (NumberFormatException e) {
                    Exceptions.ignore(e);
                }
            }
            if (!(value instanceof Integer)) {
                value = null;
            }
        }
        if (value == null) {
            if (!isNullAllowed()) {
                return 0;
            } else {
                return value;
            }
        }
        return value;
    }
}
