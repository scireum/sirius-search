/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import org.elasticsearch.common.xcontent.XContentBuilder;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Register;
import sirius.search.annotations.FastCompletion;
import sirius.search.annotations.IndexMode;
import sirius.search.suggestion.AutoCompletion;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a field for fast completion using the es-completion-suggester
 */
public class CompleterProperty extends ObjectProperty {

    private final String analyzer;
    private final List<Tuple<String, String>> contexts = new ArrayList<>();
    private final int maxInputLength;

    /**
     * Factory for generating properties of type {@link AutoCompletion} .
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

        if (field.getAnnotation(FastCompletion.class).contextNames().length != field.getAnnotation(FastCompletion.class)
                                                                                    .contextTypes().length) {
            throw new IllegalStateException("Different dimensions for context names and context types!");
        }

        for (int i = 0; i < field.getAnnotation(FastCompletion.class).contextNames().length; i++) {
            contexts.add(Tuple.create(field.getAnnotation(FastCompletion.class).contextNames()[i],
                                      field.getAnnotation(FastCompletion.class).contextTypes()[i]));
        }

        analyzer = field.isAnnotationPresent(IndexMode.class) ?
                   field.getAnnotation(IndexMode.class).analyzer() :
                   "whitespace";

        maxInputLength = field.getAnnotation(FastCompletion.class).maxInputLength();
    }

    @Override
    public void createMapping(XContentBuilder builder) throws IOException {
        builder.startObject(getName());
        builder.field("type", getMappingType());
        builder.field("analyzer", analyzer);
        builder.field("max_input_length", maxInputLength);

        if (!contexts.isEmpty()) {
            builder.startArray("contexts");

            for (Tuple<String, String> context : contexts) {
                builder.startObject();
                builder.field("name", context.getFirst());
                builder.field("type", context.getSecond());
                builder.endObject();
            }

            builder.endArray();
        }
        builder.endObject();
    }

    @Override
    protected String getMappingType() {
        return "completion";
    }
}