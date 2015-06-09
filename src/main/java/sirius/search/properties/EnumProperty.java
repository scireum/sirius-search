/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;

import java.lang.reflect.Field;

/**
 * Represents a property which contains an enum value.
 */
public class EnumProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return field.getType().isEnum();
        }

        @Override
        public Property create(Field field) {
            return new EnumProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private EnumProperty(Field field) {
        super(field);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object transformFromSource(Object value) {
        try {
            return Value.of(value)
                        .coerce((Class<Object>) field.getType(),
                                isNullAllowed() ? null : field.getType().getEnumConstants()[0]);
        } catch (IllegalArgumentException e) {
            Exceptions.ignore(e);
            return isNullAllowed() ? null : field.getType().getEnumConstants()[0];
        }
    }

    @Override
    protected Object transformToSource(Object o) {
        if (o == null) {
            return null;
        }
        return ((Enum<?>) o).name();
    }
}
