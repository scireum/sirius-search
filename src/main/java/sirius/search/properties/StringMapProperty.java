/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import org.elasticsearch.common.xcontent.XContentBuilder;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.search.Entity;
import sirius.search.Index;
import sirius.search.annotations.ListType;
import sirius.web.http.WebContext;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a property which contains a map of strings to strings. Such fields must wear a {@link
 * sirius.search.annotations.ListType} annotation with
 * <tt>String</tt> as their value.
 */
public class StringMapProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return Map.class.equals(field.getType())
                   && field.isAnnotationPresent(ListType.class)
                   && String.class.equals(field.getAnnotation(ListType.class).value());
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
    }

    @Override
    protected Object transformFromSource(Object value) {
        if (!(value instanceof Map)) {
            return new HashMap<>();
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
        throw new UnsupportedOperationException();
    }

    @Override
    public void init(Entity entity) throws Exception {
        field.set(entity, new HashMap<>());
    }

    @Override
    protected String getMappingType() {
        return "object";
    }

    @Override
    public boolean acceptsSetter() {
        return false;
    }

    /**
     * Generates the mapping used by this property
     *
     * @param builder the builder used to generate JSON
     * @throws IOException in case of an io error while generating the mapping
     */
    public void createMapping(XContentBuilder builder) throws IOException {
        builder.startObject(getName());
        builder.field("type", getMappingType());
        if (isIgnoreFromAll()) {
            builder.field("include_in_all", false);
        }
        builder.endObject();
    }
}
