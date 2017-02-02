/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import sirius.kernel.commons.Amount;
import sirius.kernel.di.std.Register;

import java.lang.reflect.Field;
import java.math.BigDecimal;

/**
 * Represents an exactly mapped property for fields of type <tt>Amount</tt>.
 * <p>
 * Values are stored as string to ensure exact representation without any rounding errors.
 */
public class AmountProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return Amount.class.equals(field.getType());
        }

        @Override
        public Property create(Field field) {
            return new AmountProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private AmountProperty(Field field) {
        super(field);
    }

    @Override
    protected Object transformFromSource(Object value) {
        if (value != null) {
            return Amount.of(new BigDecimal(value.toString()));
        }

        return Amount.NOTHING;
    }

    @Override
    protected Object transformToSource(Object o) {
        if (o != null) {
            Amount amount = (Amount) o;
            if (amount.isFilled()) {
                return amount.getAmount().toPlainString();
            }
        }
        return null;
    }
}
