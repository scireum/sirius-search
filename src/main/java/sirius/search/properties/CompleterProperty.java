/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import org.elasticsearch.common.xcontent.XContentBuilder;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.search.Index;
import sirius.search.annotations.FastCompletion;
import sirius.search.annotations.IndexMode;
import sirius.search.suggestion.AutoCompletion;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Generates a field for fast completion using the es-completion-suggester
 */
public class CompleterProperty extends ObjectProperty {

    private final boolean payloads;
    private final String contextName;
    private final String contextType;
    private final String analyzer;

    /**
     * Factory for generating properties based on having a {@link FastCompletion} annotation.
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return field.getType().equals(AutoCompletion.class);
        }

        @Override
        public Property create(Field field) {
            return new CompleterProperty(field);
        }
    }

    /**
     * Generates a new property for the given field
     *
     * @param field the underlying field from which the property was created
     */
    private CompleterProperty(Field field) {
        super(field);
        payloads = field.isAnnotationPresent(FastCompletion.class) ?
                   field.getAnnotation(FastCompletion.class).payloads() :
                   false;
        contextName = field.isAnnotationPresent(FastCompletion.class) ?
                      field.getAnnotation(FastCompletion.class).contextName() :
                      "";
        contextType = field.isAnnotationPresent(FastCompletion.class) ?
                      field.getAnnotation(FastCompletion.class).contextType() :
                      "";
        analyzer = field.isAnnotationPresent(IndexMode.class) ?
                   field.getAnnotation(IndexMode.class).analyzer() :
                   "whitespace";
    }

    @Override
    public void createMapping(XContentBuilder builder) throws IOException {
        builder.startObject(getName());
        builder.field("type", getMappingType());
        builder.field("analyzer", analyzer);
        builder.field("payloads", payloads);

        if (Strings.isFilled(contextName)) {
            builder.startObject("context");
            builder.startObject(contextName);
            builder.field("type", contextType);
            builder.endObject();
            builder.endObject();
        }
        builder.endObject();
    }

    @Override
    protected String getMappingType() {
        return "completion";
    }
}