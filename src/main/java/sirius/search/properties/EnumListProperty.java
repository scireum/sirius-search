/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import com.google.common.collect.Lists;
import sirius.kernel.commons.Value;
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
 * Represents a property which contains a list of strings. Such fields must wear a {@link
 * sirius.search.annotations.ListType} annotation with
 * <tt>String</tt> as their value.
 */
public class EnumListProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return List.class.equals(field.getType()) && field.isAnnotationPresent(ListType.class) &&
                   field.getAnnotation(ListType.class).value().isEnum();
        }

        @Override
        public Property create(Field field) {
            return new EnumListProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private EnumListProperty(Field field) {
        super(field);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object transformFromSource(Object value) {
        List<Object> result = new ArrayList<>();
        try {
            if (value instanceof List) {
                for (Object element : (List<?>) value) {
                    Object enumValue = Value.of(element).coerce(field.getAnnotation(ListType.class).value(), null);
                    if (enumValue != null) {
                        result.add(enumValue);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            /* IGNORE */
        }

        return result;
    }

    @Override
    protected Object transformToSource(Object o) {
        List<String> result = Lists.newArrayList();
        if (o instanceof List<?>) {
            for (Object element : (List<?>) o) {
                if (element != null) {
                    result.add(((Enum<?>) element).name());
                }
            }
        }
        return result;
    }

    @Override
    public void readFromRequest(Entity entity, WebContext ctx) {
        try {
            field.set(entity, transformFromRequest(getName(), ctx));
        } catch (IllegalAccessException e) {
            Exceptions.handle(IndexAccess.LOG, e);
        }
    }

    @Override
    protected Object transformFromRequest(String name, WebContext ctx) {
        return transformFromSource(ctx.getParameters(name));
    }

    @Override
    public void init(Entity entity) throws Exception {
        field.set(entity, new ArrayList());
    }

    @Override
    public boolean acceptsSetter() {
        return false;
    }
}
