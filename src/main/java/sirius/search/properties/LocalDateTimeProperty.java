/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents a timestamp property which contains a date along with a time value. This is used to represents fields of
 * type
 * {@link  java.time.LocalDateTime}
 */
public class LocalDateTimeProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return LocalDateTime.class.equals(field.getType());
        }

        @Override
        public Property create(Field field) {
            return new LocalDateTimeProperty(field);
        }
    }

    @Override
    protected String getMappingType() {
        return "date";
    }

    @Override
    protected Object transformFromRequest(String name, WebContext ctx) {
        Value value = ctx.get(name);
        if (value.isEmptyString()) {
            return null;
        }
        try {
            return NLS.parseUserString(LocalDateTime.class, value.getString());
        } catch (IllegalArgumentException e) {
            UserContext.setFieldError(name, value.get());
            throw Exceptions.createHandled()
                            .withNLSKey("Property.invalidInput")
                            .set("field", NLS.get(field.getDeclaringClass().getSimpleName() + "." + name))
                            .set("value", value.asString())
                            .handle();
        }
    }

    @Override
    protected Object transformFromSource(Object value) {
        if (Strings.isEmpty(value)) {
            return null;
        }
        String valueAsString = (String) value;
        if (valueAsString.contains("+")) {
            return LocalDateTime.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(valueAsString));
        } else {
            return LocalDateTime.from(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(valueAsString));
        }
    }

    @Override
    protected Object transformToSource(Object o) {
        if (o == null || !(o instanceof LocalDateTime)) {
            return null;
        }
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(((LocalDateTime) o).atZone(ZoneId.systemDefault()));
    }

    /*
     * Instances are only created by the factory
     */
    private LocalDateTimeProperty(Field field) {
        super(field);
    }
}
