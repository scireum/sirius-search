/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import sirius.search.Entity;
import sirius.search.Index;
import sirius.search.annotations.ListType;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.http.WebContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a property which contains a list of strings. Such fields must wear a {@link ListType} annotation with
 * <tt>String</tt> as their value.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
public class StringListProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return List.class.equals(field.getType()) && field.isAnnotationPresent(ListType.class) && String.class.equals(
                    field.getAnnotation(ListType.class).value());
        }

        @Override
        public Property create(Field field) {
            return new StringListProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private StringListProperty(Field field) {
        super(field);
    }

    @Override
    protected Object transformFromSource(Object value) {
        if (!(value instanceof List)) {
            return new ArrayList<String>();
        } else {
            return value;
        }
    }

    @Override
    public void readFromRequest(Entity entity, WebContext ctx) {
        try {
            field.set(entity, transformFromRequest(getName(), ctx));
        } catch (IllegalAccessException e) {
            Exceptions.handle(Index.LOG, e);
        }
    }

    @Override
    protected Object transformFromRequest(String name, WebContext ctx) {
        return ctx.getParameters(name);
    }

    @Override
    public void init(Entity entity) throws Exception {
        field.set(entity, new ArrayList<String>());
    }

    @Override
    public boolean acceptsSetter() {
        return false;
    }

}
