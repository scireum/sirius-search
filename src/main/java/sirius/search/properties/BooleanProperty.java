/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.search.Entity;
import sirius.search.IndexAccess;
import sirius.web.http.WebContext;

import java.lang.reflect.Field;

/**
 * Represents a boolean property for fields of type <tt>Boolean</tt> or <tt>boolean</tt>
 */
public class BooleanProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return Boolean.class.equals(field.getType()) || boolean.class.equals(field.getType());
        }

        @Override
        public Property create(Field field) {
            return new BooleanProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private BooleanProperty(Field field) {
        super(field);
    }

    @Override
    protected String getMappingType() {
        return "boolean";
    }

    @Override
    public void readFromRequest(Entity entity, WebContext ctx) {
        try {
            if (ctx.get(getName()).isNull()) {
                getField().set(entity, false);
            } else {
                getField().set(entity, transformFromRequest(getName(), ctx));
            }
        } catch (IllegalAccessException e) {
            Exceptions.handle(IndexAccess.LOG, e);
        }
    }

    @Override
    protected Object transformFromSource(Object value) {
        if (value != null && !(value instanceof Boolean)) {
            value = null;
        }
        if (value == null) {
            if (!isNullAllowed()) {
                return false;
            } else {
                return value;
            }
        }
        return value;
    }
}
