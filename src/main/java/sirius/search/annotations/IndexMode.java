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
 * Changes the index mode of the annotated field.
 * <p>
 * By default, fields are not analysed. However, using this annotation, Elasticsearch can be instructed to apply
 * an analyzer or to not index the field at all (relevant for large fields, not intended to be searched in).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IndexMode {

    /**
     * Sets the {@link #indexMode()} to "analyzed".
     * <p>
     * This will make elasticsearch use an analyzer to split the contents
     * of the field into separate tokens which are then searchable.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String MODE_ANALYZED = "analyzed";

    /**
     * Sets the {@link #indexMode()} to "not_analyzed".
     * <p>
     * This will make elasticsearch treat the contents of the field as a single token which is then searchable.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String MODE_NOT_ANALYZED = "not_analyzed";

    /**
     * Sets the {@link #indexMode()} to "no".
     * <p>
     * This will disable indexing of the field entirely. Therefore no tokens are stored. This is essential for large
     * fields, as the token limit is 64k.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String MODE_NO = "no";

    /**
     * Sets the {@link #normEnabled()} to "true".
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String NORMS_ENABLED = "true";

    /**
     * Sets the {@link #normEnabled()} to "false".
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String NORMS_DISABLED = "false";

    /**
     * Sets the {@link #indexMode()} ()} to "docs".
     * <p>
     * This will instruct elasticsearch to only store the doc id for a token.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String INDEX_OPTION_DOCS = "docs";

    /**
     * Sets the {@link #indexMode()} ()} to "freqs".
     * <p>
     * This will instruct elasticsearch to store the doc id and the term frequency for a token.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String INDEX_OPTION_FREQS = "freqs";

    /**
     * Sets the {@link #indexMode()} ()} to "positions".
     * <p>
     * This will instruct elasticsearch to store the doc id, the term frequency and the position for a token.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String INDEX_OPTION_POSITIONS = "positions";

    /**
     * Sets the {@link #analyzer()} ()} ()} to "whitespace".
     * <p>
     * This will instruct elasticsearch to use the whitespace analyzer which creates a token for each whitespace
     * separated word.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String ANALYZER_WHITESPACE = "whitespace";

    /**
     * Sets the {@link #analyzer()} ()} ()} to "trigram".
     * <p>
     * This will instruct elasticsearch to use a trigram analyzer which creates "shingles" of min length 2 and max
     * length 3. This analyzer is useful in combination with {@link sirius.search.suggestion.Suggest}.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String ANALYZER_TRIGRAM = "trigram";

    /**
     * Determines the index mode used by elastricsearch.
     *
     * @return use one of the constants defined by this annotations to specify the index mode
     */
    String indexMode() default MODE_NOT_ANALYZED;

    /**
     * Permits to specify the analyzer to use for this field. By default the standard analyzer set by
     * elasticsearch is used.
     *
     * @return the name of the analyzer to use for this field. If left empty, no value will be sent to elasticsearch
     * so that it will use its default analyzer.
     */
    String analyzer() default "";

    /**
     * Permits to specify additional index options sent to elasticsearch.
     *
     * @return additional options sent to elasticsearch (use constants defined by this annotation). If empty, nothing
     * will be sent to elasticsearch (which will then apply its default values).
     */
    String indexOptions() default "";

    /**
     * Permits to specify if norms are enabled for this field or not.
     *
     * @return use one of the string constants defined by this annotation to specify if norms are enabled or not. If
     * an empty string is specified, the default values of elasticsearch will be applied
     */
    String normEnabled() default "";

    /**
     * Permits to specify if the contents of this field are included in the _all field.
     *
     * @return <tt>true</tt> if the contents of this field should be included in the <tt>_all</tt> field,
     * <tt>false</tt> otherwise
     */
    boolean includeInAll() default true;

    /**
     * Permits to exclude the contents of this field from the _source field. This should be used with care!
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    boolean excludeFromSource() default false;
}
