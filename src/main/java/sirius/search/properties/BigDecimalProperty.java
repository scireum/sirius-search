/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import sirius.kernel.di.std.Register;

import java.lang.reflect.Field;
import java.math.BigDecimal;

/**
 * Represents an exactly mapped property for fields of type <tt>BigDecimal</tt>.
 * <p>
 * Values are stored as string to ensure exact representation without any rounding errors.
 */
public class BigDecimalProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return BigDecimal.class.equals(field.getType());
        }

        @Override
        public Property create(Field field) {
            return new BigDecimalProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private BigDecimalProperty(Field field) {
        super(field);
    }

    @Override
    protected String getMappingType() {
        return "string";
    }

    @Override
    protected Object transformFromSource(Object value) {
        if (value != null) {
            return new BigDecimal(value.toString());
        }
        if (value == null) {
            if (!isNullAllowed()) {
                return BigDecimal.ZERO;
            } else {
                return value;
            }
        }
        return value;
    }

    @Override
    protected Object transformToSource(Object o) {
        if (o != null) {
            return ((BigDecimal) o).toPlainString();
        }
        return null;
    }
}
