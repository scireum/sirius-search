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
 * Only for {@link String}, {@link java.util.List}&lt;{@link String}&gt; and {@link
 * sirius.search.suggestion.AutoCompletion} fields.
 * This will make elasticsearch use an analyzer to split the contents of the field into separate tokens which are then
 * searchable.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Analyzed {

    /**
     * Sets the {@link #analyzer()} to "whitespace".
     * <p>
     * This will instruct elasticsearch to use the whitespace analyzer which creates a token for each whitespace
     * separated word.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String ANALYZER_WHITESPACE = "whitespace";

    /**
     * Sets the {@link #analyzer()} to "trigram".
     * <p>
     * This will instruct elasticsearch to use a trigram analyzer which creates "shingles" of min length 2 and max
     * length 3. This analyzer is useful in combination with {@link sirius.search.suggestion.Suggest}.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String ANALYZER_TRIGRAM = "trigram";

    /**
     * Sets the {@link #indexOptions()} to "docs".
     * <p>
     * This will instruct elasticsearch to only store the doc id for a token.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String INDEX_OPTION_DOCS = "docs";

    /**
     * Sets the {@link #indexOptions()} to "freqs".
     * <p>
     * This will instruct elasticsearch to store the doc id and the term frequency for a token.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String INDEX_OPTION_FREQS = "freqs";

    /**
     * Sets the {@link #indexOptions()} to "positions".
     * <p>
     * This will instruct elasticsearch to store the doc id, the term frequency and the position for a token.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String INDEX_OPTION_POSITIONS = "positions";

    /**
     * Sets the {@link #indexOptions()} to "offsets".
     * <p>
     * This will instruct elasticsearch to store the doc id, the term frequency, the position and the start and end
     * character offsets for a token.
     * <p>
     * See the elasticsearch docs for a detailed description of the behaviour.
     */
    String INDEX_OPTION_OFFSETS = "offsets";

    /**
     * Permits to specify the analyzer to use for this field. By default the standard analyzer set by elasticsearch is
     * used.
     *
     * @return the name of the analyzer to use for this field. If left empty, no value will be sent to elasticsearch
     * so that it will use its default analyzer.
     * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/analyzer.html">https://www.elastic.co/guide/en/elasticsearch/reference/current/analyzer.html</a>
     */
    String analyzer() default "";

    /**
     * Permits to specify additional index options sent to elasticsearch.
     *
     * @return additional options sent to elasticsearch (use a constant defined by this annotation). If empty, nothing
     * will be sent to elasticsearch (which will then apply its default value).
     * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/index-options.html">https://www.elastic.co/guide/en/elasticsearch/reference/current/index-options.html</a>
     */
    String indexOptions() default "";
}
