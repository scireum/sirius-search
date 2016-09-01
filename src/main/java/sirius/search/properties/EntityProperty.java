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
import sirius.search.EntityRef;
import sirius.search.Index;
import sirius.search.annotations.RefType;
import sirius.web.http.WebContext;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Represents a property which references another entity. Such a field must wear a
 * {@link sirius.search.annotations.RefType} annotation.
 */
public class EntityProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return EntityRef.class.equals(field.getType()) && field.isAnnotationPresent(RefType.class);
        }

        @Override
        public Property create(Field field) {
            return new EntityProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private EntityProperty(Field field) {
        super(field);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(Entity entity) throws Exception {
        field.set(entity, new EntityRef<>((Class<Entity>) field.getAnnotation(RefType.class).type()));
    }

    @Override
    public boolean acceptsSetter() {
        return false;
    }

    @Override
    protected Object transformToSource(Object o) {
        EntityRef<?> entityRef = (EntityRef<?>) o;
        if (!entityRef.isFilled()) {
            return null;
        }
        return entityRef.getId();
    }

    @Override
    public void readFromRequest(Entity entity, WebContext ctx) {
        try {
            if (ctx.get(getName()).isNull()) {
                return;
            }
            ((EntityRef<?>) field.get(entity)).setId(ctx.get(getName()).asString());
        } catch (IllegalAccessException e) {
            throw Exceptions.handle(Index.LOG, e);
        }
    }

    @Override
    public void readFromSource(Entity entity, Object value) {
        try {
            EntityRef<?> entityRef = (EntityRef<?>) field.get(entity);
            entityRef.setId((String) value);
            entityRef.clearDirty();
        } catch (IllegalAccessException e) {
            throw Exceptions.handle(Index.LOG, e);
        }
    }

    @Override
    protected Object transformFromSource(Object value) {
        throw new UnsupportedOperationException();
    }
}
