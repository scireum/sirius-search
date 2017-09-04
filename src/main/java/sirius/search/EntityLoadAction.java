/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.kernel.Sirius;
import sirius.kernel.di.ClassLoadAction;
import sirius.kernel.di.MutableGlobalContext;
import sirius.search.annotations.Indexed;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;

/**
 * Scans for subclasses of {@link Entity} and loads them into the object model so that the {@link Schema} can
 * identify all entity classes.
 */
public class EntityLoadAction implements ClassLoadAction {

    @Override
    public Class<? extends Annotation> getTrigger() {
        return null;
    }

    @Override
    public void handle(MutableGlobalContext ctx, Class<?> clazz) throws Exception {
        if (Entity.class.isAssignableFrom(clazz)
            && !Modifier.isAbstract(clazz.getModifiers())
            && acceptedByFrameworkFilter(clazz)) {
            ctx.registerPart(clazz.newInstance(), Entity.class);
        }
    }

    protected boolean acceptedByFrameworkFilter(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(Indexed.class)) {
            // This will be rejected later by the Schema class...
            return true;
        }
        return Sirius.isFrameworkEnabled(clazz.getAnnotation(Indexed.class).framework());
    }
}
