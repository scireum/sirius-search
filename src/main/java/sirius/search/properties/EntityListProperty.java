/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.search.Entity;
import sirius.search.EntityRefList;
import sirius.search.IndexAccess;
import sirius.search.annotations.RefType;
import sirius.web.http.WebContext;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Represents a property which references a list of other entities. Such a field must wear a
 * {@link sirius.search.annotations.RefType} annotation.
 */
public class EntityListProperty extends Property {

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return EntityRefList.class.equals(field.getType()) && field.isAnnotationPresent(RefType.class);
        }

        @Override
        public Property create(Field field) {
            return new EntityListProperty(field);
        }
    }

    /*
     * Instances are only created by the factory
     */
    private EntityListProperty(Field field) {
        super(field);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(Entity entity) throws IllegalAccessException {
        getField().set(entity, new EntityRefList<>((Class<Entity>) getField().getAnnotation(RefType.class).type()));
    }

    @Override
    public boolean acceptsSetter() {
        return false;
    }

    @Override
    protected Object transformToSource(Object o) {
        EntityRefList<?> entityRef = (EntityRefList<?>) o;
        return entityRef.getIds();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFromRequest(Entity entity, WebContext ctx) {
        try {
            ((EntityRefList<?>) getField().get(entity)).setIds((List<String>) transformFromRequest(getName(), ctx));
        } catch (IllegalAccessException e) {
            Exceptions.handle(IndexAccess.LOG, e);
        }
    }

    @Override
    protected Object transformFromRequest(String name, WebContext ctx) {
        return ctx.getParameters(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFromSource(Entity entity, Object value) {
        try {
            ((EntityRefList<?>) getField().get(entity)).setIds((List<String>) value);
        } catch (IllegalAccessException e) {
            Exceptions.handle(IndexAccess.LOG, e);
        }
    }

    @Override
    protected Object transformFromSource(Object value) {
        throw new UnsupportedOperationException();
    }
}
