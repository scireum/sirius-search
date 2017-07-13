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
import sirius.search.Entity;
import sirius.search.IndexAccess;
import sirius.search.annotations.ListType;
import sirius.search.annotations.Transient;
import sirius.web.http.WebContext;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a property which contains a list of POJO objects. Such fields must wear a {@link
 * ListType} annotation with <tt>Object</tt> as their value.
 */
public class ObjectListProperty extends Property {

    private final boolean nested;

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return List.class.equals(field.getType())
                   && field.isAnnotationPresent(ListType.class)
                   && !String.class.equals(field.getAnnotation(ListType.class).value())
                   && !field.getAnnotation(ListType.class).value().isEnum();
        }

        @Override
        public Property create(Field field) {
            return new ObjectListProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private ObjectListProperty(Field field) {
        super(field);
        setInnerProperty(true);

        this.nested = field.getAnnotation(ListType.class).nested();
    }

    @Override
    public void init(Entity entity) throws IllegalAccessException {
        getField().set(entity, new ArrayList<>());
    }

    @Override
    public boolean acceptsSetter() {
        return false;
    }

    @Override
    protected String getMappingType() {
        return nested ? "nested" : "object";
    }

    @Override
    public void addMappingProperties(XContentBuilder builder) throws IOException {
        super.addMappingProperties(builder);

        builder.startObject("properties");
        addNestedMappingProperties(builder, getField().getAnnotation(ListType.class).value());
        builder.endObject();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Object transformFromSource(Object value) {
        List<Object> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object object : (List<?>) value) {
                if (object instanceof Map) {
                    try {
                        Map<String, String> values = (Map<String, String>) object;
                        Class<?> targetClass = getField().getAnnotation(ListType.class).value();
                        Object obj = targetClass.newInstance();

                        for (Field innerField : targetClass.getDeclaredFields()) {
                            if (!innerField.isAnnotationPresent(Transient.class)
                                && !Modifier.isStatic(innerField.getModifiers())) {
                                try {
                                    if (values.containsKey(innerField.getName())) {
                                        innerField.setAccessible(true);
                                        innerField.set(obj,
                                                       NLS.parseMachineString(innerField.getType(),
                                                                              values.get(innerField.getName())));
                                    }
                                } catch (Exception e) {
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

                        result.add(obj);
                    } catch (Exception e) {
                        Exceptions.handle()
                                  .error(e)
                                  .to(IndexAccess.LOG)
                                  .withSystemErrorMessage("Cannot load POJO in %s: %s (%s)", toString())
                                  .handle();
                    }
                }
            }
        }

        return result;
    }

    @Override
    protected Object transformToSource(Object o) {
        List<Map<String, String>> result = new ArrayList<>();
        if (o instanceof List<?>) {
            for (Object obj : (List<?>) o) {
                if (obj != null) {
                    Map<String, String> valueMap = new HashMap<>();
                    Class<?> targetClass = getField().getAnnotation(ListType.class).value();
                    for (Field innerField : targetClass.getDeclaredFields()) {
                        if (!innerField.isAnnotationPresent(Transient.class)
                            && !Modifier.isStatic(innerField.getModifiers())) {
                            try {
                                innerField.setAccessible(true);
                                Object val = innerField.get(obj);
                                if (val != null) {
                                    valueMap.put(innerField.getName(), NLS.toMachineString(val));
                                }
                            } catch (Exception e) {
                                Exceptions.handle()
                                          .error(e)
                                          .to(IndexAccess.LOG)
                                          .withSystemErrorMessage("Cannot save POJO field %s of %s: %s (%s)",
                                                                  innerField.getName(),
                                                                  toString())
                                          .handle();
                            }
                        }
                    }
                    result.add(valueMap);
                }
            }
        }
        return result;
    }

    @Override
    public void readFromRequest(Entity entity, WebContext ctx) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object transformFromRequest(String name, WebContext ctx) {
        throw new UnsupportedOperationException();
    }
}
