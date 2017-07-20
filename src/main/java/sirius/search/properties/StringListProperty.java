/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import com.google.common.collect.Lists;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.search.Entity;
import sirius.search.IndexAccess;
import sirius.search.annotations.ListType;
import sirius.web.http.WebContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a property which contains a list of strings. Such fields must wear a {@link ListType} annotation with
 * <tt>String</tt> as their value.
 */
public class StringListProperty extends StringProperty {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return List.class.equals(field.getType())
                   && field.isAnnotationPresent(ListType.class)
                   && String.class.equals(field.getAnnotation(ListType.class).value());
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
    public void init(Entity entity) throws IllegalAccessException {
        getField().set(entity, new ArrayList<String>());
    }

    @Override
    public boolean acceptsSetter() {
        return false;
    }

    @Override
    protected Object transformFromSource(Object value) {
        if (!(value instanceof List)) {
            if (value instanceof String) {
                return Lists.newArrayList(value);
            }
            return new ArrayList<String>();
        } else {
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void readFromRequest(Entity entity, WebContext ctx) {
        try {
            List<Object> list = (List<Object>) getField().get(entity);
            if (list == null) {
                list = Lists.newArrayList();
            } else {
                list.clear();
            }
            list.addAll((List<Object>) transformFromRequest(getName(), ctx));
        } catch (IllegalAccessException e) {
            Exceptions.handle(IndexAccess.LOG, e);
        }
    }

    @Override
    protected Object transformFromRequest(String name, WebContext ctx) {
        return ctx.getParameters(name);
    }
}
