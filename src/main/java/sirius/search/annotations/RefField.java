/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a copy of a referenced entities field.
 * <p>
 * This annotation needs to go along with a {@link RefType} annotation and can be used to signal that a field is
 * a copy of a field of the referenced entity. These values will be automatically updated if possible.
 * </p>
 *
 * @author Andreas Haufler
 * @since 2013/12
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RefField {
    /**
     * Contains the name of the field which contains the entity and wears the {@link RefType} annotation.
     *
     * @return the name of the entity field
     */
    String localRef();

    /**
     * Contains the name of the copied field in the referenced entity.
     *
     * @return the name of the field being copied in the referenced entity
     */
    String remoteField();
}
