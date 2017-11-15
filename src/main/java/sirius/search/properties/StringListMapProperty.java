/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Register;
import sirius.search.annotations.MapType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a property which contains a map of strings to strings. Such fields must wear a {@link
 * sirius.search.annotations.ListType} annotation with <tt>String</tt> as their value.
 */
public class StringListMapProperty extends StringMapProperty {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return Map.class.equals(field.getType()) && field.isAnnotationPresent(MapType.class) && List.class.equals(
                    field.getAnnotation(MapType.class).value());
        }

        @Override
        public Property create(Field field) {
            return new StringListMapProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private StringListMapProperty(Field field) {
        super(field);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object transformFromSource(Object value) {
        Map<String, List<String>> result = new HashMap<>();
        if (value instanceof Collection) {
            ((Collection<Map<String, Object>>) value).forEach(entry -> {
                Object val = entry.get(VALUE);
                List<String> values = new ArrayList<>();
                if (value instanceof String) {
                    values.add((String) val);
                } else if (value instanceof List) {
                    values.addAll((List<String>) val);
                }
                result.put((String) entry.get(KEY), values);
            });
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object transformToSource(Object o) {
        List<Map<String, Object>> valueList = new ArrayList<>();

        if (o instanceof Map) {
            ((Map<String, List<String>>) o).forEach((key, value) -> valueList.add(Context.create()
                                                                                         .set(KEY, key)
                                                                                         .set(VALUE, value)));
        }
        return valueList;
    }
}
