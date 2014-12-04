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
 * Represents a double property for fields of type <tt>double</tt> or <tt>Double</tt>.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2014/06
 */
public class DoubleProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return Double.class.equals(field.getType()) || double.class.equals(field.getType());
        }

        @Override
        public Property create(Field field) {
            return new DoubleProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private DoubleProperty(Field field) {
        super(field);
    }

    @Override
    protected String getMappingType() {
        return "double";
    }

    @Override
    protected Object transformFromSource(Object value) {
        if (value instanceof String && Strings.isFilled(value)) {
            try {
                value = Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                Exceptions.ignore(e);
            }
        }
        if (value != null && !(value instanceof Double) && !(value instanceof Integer)) {
            value = null;
        }
        if (value == null) {
            if (!isNullAllowed()) {
                return 0L;
            } else {
                return value;
            }
        }
        return value;
    }

}
