/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import org.elasticsearch.common.xcontent.XContentBuilder;
import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.search.Entity;
import sirius.search.IndexAccess;
import sirius.search.annotations.MapType;
import sirius.web.http.WebContext;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static sirius.search.properties.ESOption.FALSE;

/**
 * Represents a property which contains a map of strings to strings. Such fields must wear a {@link
 * sirius.search.annotations.ListType} annotation with <tt>String</tt> as their value.
 */
public class StringMapProperty extends StringProperty {

    public static final String KEY = "key";
    public static final String VALUE = "value";

    private final boolean nested;

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return Map.class.equals(field.getType()) && field.isAnnotationPresent(MapType.class) && String.class.equals(
                    field.getAnnotation(MapType.class).value());
        }

        @Override
        public Property create(Field field) {
            return new StringMapProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private StringMapProperty(Field field) {
        super(field);
        setInnerProperty(true);

        this.nested = field.getAnnotation(MapType.class).nested();
    }

    @Override
    public void init(Entity entity) throws IllegalAccessException {
        getField().set(entity, new HashMap<>());
    }

    @Override
    public boolean acceptsSetter() {
        return false;
    }

    @Override
    protected ESOption isDefaultIncludeInAll() {
        return FALSE;
    }

    @Override
    protected String getMappingType() {
        return nested ? "nested" : "object";
    }

    /**
     * Generates the mapping used by this property
     *
     * @param builder the builder used to generate JSON
     * @throws IOException in case of an io error while generating the mapping
     */
    @Override
    public void addMappingProperties(XContentBuilder builder) throws IOException {
        super.addMappingProperties(builder);

        builder.startObject("properties");

        builder.startObject(KEY);
        builder.field("type", "keyword");
        builder.endObject();

        builder.startObject(VALUE);
        builder.field("type", super.getMappingType());
        builder.endObject();

        builder.endObject();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object transformFromSource(Object value) {
        Map<String, String> result = new HashMap<>();
        if (value instanceof Collection) {
            ((Collection<Map<String, String>>) value).forEach(entry -> result.put(entry.get(KEY), entry.get(VALUE)));
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object transformToSource(Object o) {
        List<Map<String, Object>> valueList = new ArrayList<>();

        if (o instanceof Map) {
            ((Map<String, String>) o).forEach((key, value) -> valueList.add(Context.create()
                                                                                   .set(KEY, key)
                                                                                   .set(VALUE, value)));
        }
        return valueList;
    }

    @Override
    public void readFromRequest(Entity entity, WebContext ctx) {
        try {
            getField().set(entity, transformFromRequest(getName(), ctx));
        } catch (IllegalAccessException e) {
            Exceptions.handle(IndexAccess.LOG, e);
        }
    }

    @Override
    protected Object transformFromRequest(String name, WebContext ctx) {
        throw new UnsupportedOperationException();
    }
}
