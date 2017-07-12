/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.annotations;

import sirius.search.properties.ESOption;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Changes the index mode of the annotated field.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IndexMode {

    /**
     * Determines whether this field should be indexed in general by elasticsearch.
     *
     * @return true if this field should be indexed or not.
     */
    ESOption indexed() default ESOption.DEFAULT;

    /**
     * Determines whether this field should be stored separately from the _source field by elasticsearch.
     *
     * @return true if this field should be stored separately from the _source field by elasticsearch.
     */
    ESOption stored() default ESOption.DEFAULT;

    /**
     * Only for {@link String} and {@link java.util.List}&lt;{@link String}&gt; fields.
     * Permits to specify if norms are enabled for this field or not.
     *
     * @return whether norms are enabled or not.
     */
    ESOption normsEnabled() default ESOption.DEFAULT;

    /**
     * Permits to specify if the contents of this field are included in the _all field.
     *
     * @return <tt>"true"</tt> if the contents of this field should be included in the <tt>_all</tt> field,
     * <tt>"false"</tt> otherwise.
     */
    ESOption includeInAll() default ESOption.DEFAULT;

    /**
     * Permits to specify if the contents of this field are stored on disk in a column-stride fashion.
     *
     * @return <tt>"true"</tt> if the contents of this field should be stored on disk in a column-stride fashion,
     * <tt>"false"</tt> otherwise.
     */
    ESOption docValues() default ESOption.DEFAULT;

    /**
     * Permits to exclude the contents of this field from the _source field. This should be used with care!
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    boolean excludeFromSource() default false;
}
