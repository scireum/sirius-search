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
import sirius.kernel.nls.NLS;
import sirius.search.IndexAccess;
import sirius.search.annotations.FastCompletion;
import sirius.search.annotations.NestedObject;
import sirius.search.annotations.Transient;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a property which contains a  POJO object. Such fields must wear a {@link
 * NestedObject} annotation.
 */
public class ObjectProperty extends Property {

    protected final String analyzer;

    /**
     * Factory for generating properties based on having a {@link NestedObject} annotation.
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return field.isAnnotationPresent(NestedObject.class) && !field.isAnnotationPresent(FastCompletion.class);
        }

        @Override
        public Property create(Field field) {
            return new ObjectProperty(field);
        }
    }

    /**
     * Generates a new property for the given field
     *
     * @param field the underlying field from which the property was created
     */
    public ObjectProperty(Field field) {
        super(field);
        analyzer = field.getAnnotation(NestedObject.class).analyzer();
    }

    @Override
    protected String getMappingType() {
        return "object";
    }

    @Override
    public void createMapping(XContentBuilder builder) throws IOException {
        builder.startObject(getName());
        builder.field("type", getMappingType());
        builder.field("analyzer", analyzer);
        builder.endObject();
    }

    @Override
    protected Object transformToSource(Object o) {
        Map<String, Object> valueMap = new HashMap<>();

        if (o != null) {
            Class<?> targetClass = field.getAnnotation(NestedObject.class).value();
            for (Field innerField : targetClass.getDeclaredFields()) {
                transformField(o, valueMap, innerField);
            }
        }

        return valueMap;
    }

    private void transformField(Object o, Map<String, Object> valueMap, Field innerField) {
        if (innerField.isAnnotationPresent(Transient.class) || Modifier.isStatic(innerField.getModifiers())) {
            return;
        }
        try {
            innerField.setAccessible(true);
            Object val = innerField.get(o);
            if (val != null) {
                if (val instanceof Map) {
                    valueMap.put(innerField.getName(), val);
                } else {
                    valueMap.put(innerField.getName(), NLS.toMachineString(val));
                }
            }
        } catch (Throwable e) {
            Exceptions.handle()
                      .error(e)
                      .to(IndexAccess.LOG)
                      .withSystemErrorMessage("Cannot save POJO field %s of %s: %s (%s)",
                                              innerField.getName(),
                                              toString())
                      .handle();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Object transformFromSource(Object value) {
        try {
            Class<?> targetClass = field.getAnnotation(NestedObject.class).value();
            Object obj = targetClass.newInstance();

            if (value instanceof Map) {
                Map<String, String> values = (Map<String, String>) value;
                for (Field innerField : targetClass.getDeclaredFields()) {
                    fillField(obj, values, innerField);
                }
            }
            return obj;
        } catch (Throwable e) {
            throw Exceptions.handle()
                            .error(e)
                            .to(IndexAccess.LOG)
                            .withSystemErrorMessage("Cannot load POJO in %s: %s (%s)", toString())
                            .handle();
        }
    }

    private void fillField(Object obj, Map<String, String> values, Field innerField) {
        if (innerField.isAnnotationPresent(Transient.class) || Modifier.isStatic(innerField.getModifiers())) {
            return;
        }

        try {
            if (values.containsKey(innerField.getName())) {
                innerField.setAccessible(true);
                if (innerField.getType().equals(Map.class)) {
                    innerField.set(obj, values.get(innerField.getName()));
                } else {
                    innerField.set(obj, NLS.parseMachineString(innerField.getType(), values.get(innerField.getName())));
                }
            }
        } catch (Throwable e) {
            Exceptions.handle()
                      .error(e)
                      .to(IndexAccess.LOG)
                      .withSystemErrorMessage("Cannot load POJO field %s of %s: %s (%s)",
                                              innerField.getName(),
                                              toString())
                      .handle();
        }
    }
}