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
 * Represents a long property for fields of type <tt>long</tt> or <tt>Long</tt>.
 */
public class LongProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return Long.class.equals(field.getType()) || long.class.equals(field.getType());
        }

        @Override
        public Property create(Field field) {
            return new LongProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private LongProperty(Field field) {
        super(field);
    }

    @Override
    protected String getMappingType() {
        return "long";
    }

    @Override
    protected Object transformFromSource(Object value) {
        if (value instanceof String && Strings.isFilled(value)) {
            try {
                value = Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                Exceptions.ignore(e);
            }
        }
        if (value instanceof Integer) {
            value = Long.valueOf((Integer) value);
        }
        if (value != null && !(value instanceof Long) && !(value instanceof Integer)) {
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
