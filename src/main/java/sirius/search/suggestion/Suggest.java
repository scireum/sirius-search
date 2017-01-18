/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.suggestion;

import org.elasticsearch.action.suggest.SuggestRequestBuilder;
import org.elasticsearch.action.suggest.SuggestResponse;
import org.elasticsearch.search.suggest.phrase.PhraseSuggestion;
import org.elasticsearch.search.suggest.phrase.PhraseSuggestionBuilder;
import sirius.search.Entity;
import sirius.search.IndexAccess;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Provides a simple helper to utilize suggestions for field tokens as supported by ElasticSearch.
 *
 * @param <E> the type of entities supported by this suggester.
 */
public class Suggest<E extends Entity> {
    private IndexAccess index;
    private Class<E> clazz;
    private String field = "_all";
    private String query;
    private float maxErrors = 2f;
    private int limit = 5;
    private float confidence = 1f;
    private SuggestMode suggestMode = SuggestMode.MISSING;
    private String analyzer = "whitespace";

    private String collateQuery;
    private Map<String, Object> collateParams;
    private boolean collatePrune;

    /**
     * Used to create a new suggestion for entities of the given class
     *
     * @param index the IndexAccess instance
     * @param clazz the type of entities to suggest for
     */
    public Suggest(IndexAccess index, Class<E> clazz) {
        this.index = index;
        this.clazz = clazz;
    }

    /**
     * Sets the query string to generate suggestions for and the field to generate the suggestions from
     *
     * @param field the field to get the suggestions from
     * @param query the query to generate suggestions for
     * @return the newly created suggester
     */
    public Suggest<E> on(String field, String query) {
        this.field = field;
        this.query = query;
        return this;
    }

    /**
     * Sets the max. number of suggestions to return.
     *
     * @param limit the max. number of suggestion strings to return
     * @return the helper itself for fluent method calls
     */
    public Suggest<E> limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Sets a collate query, which can be used to filter retrieved suggestions. E.g. a lookup query can be used to
     * check whether a suggestions is present in a index field.
     *
     * @param collateQuery the query that should be executed per suggestion
     * @return the helper itself for fluent method calls
     */
    public Suggest<E> collateQuery(String collateQuery) {
        this.collateQuery = collateQuery;
        return this;
    }

    /**
     * Sets a map which contains parameters for the collate query.
     *
     * @param collateParams the parameters for the collate query
     * @return the helper itself for fluent method calls
     */
    public Suggest<E> collateParams(Map<String, Object> collateParams) {
        this.collateParams = collateParams;
        return this;
    }

    /**
     * Controls whether all suggestions should be returned by elasticsearch (but tagged with collate_match) or be
     * directly pruned
     *
     * @param collatePrune controls whether pruned suggestions nevertheless be returned (tagged) or completly removed
     * @return the helper itself for fluent method calls
     */
    public Suggest<E> collatePrune(boolean collatePrune) {
        this.collatePrune = collatePrune;
        return this;
    }

    /**
     * Wraps executing methods and returns a list of the generated suggestion phrases combined in tuples with the
     * original query string.
     *
     * @return the generated suggestion phrases
     */
    public List<PhraseSuggestion.Entry.Option> suggestList() {
        PhraseSuggestionBuilder psb = generateSuggestionBuilder();
        SuggestRequestBuilder sqb = generateRequestBuilder(psb);
        return execute(sqb);
    }

    /**
     * Sets the analyzer to analyze the query with.
     * <p>
     * Defaults to the search analyzer of the suggest field passed via 'field'.
     *
     * @param analyzer the analyzer to analyze the query with
     * @return the helper itself for fluent method calls
     */
    public Suggest<E> analyzer(String analyzer) {
        this.analyzer = analyzer;
        return this;
    }

    /**
     * Sets the confidence level of the suggestion builder (float value).
     * <p>
     * The confidence level defines a factor applied to the input phrases score which is used as a threshold for other
     * suggest candidates.
     * Only candidates that score higher than the threshold will be included in the result. For instance a confidence
     * level of 1.0 will only return suggestions that score higher than the input phrase.
     * If set to 0.0 the top N candidates are returned. The default is 1.0.
     *
     * @param confidence the level of confidence
     * @return the helper itself for fluent method calls
     */
    public Suggest<E> confidence(float confidence) {
        this.confidence = confidence;
        return this;
    }

    /**
     * Declares all supported suggestion modes.
     */
    public enum SuggestMode {
        MISSING, POPULAR, ALWAYS
    }

    /**
     * Controls for which terms suggestions should be given, and what kind.
     * <p>
     * Options:
     * "missing" = only suggest for query terms not in the shard. This is the default. If suggestions are still given
     * for words that are in the index,
     * set confidence level higher.
     * "popular" =  only suggest terms that occur in more docs on the shard then the original term.
     * "always" = suggest any matching suggestions based on terms in the suggest text.
     *
     * @param suggestMode the suggest mode 'missing', 'popular' or 'always'
     * @return the helper itself for fluent method calls
     */
    public Suggest<E> suggestMode(SuggestMode suggestMode) {
        this.suggestMode = suggestMode;
        return this;
    }

    /**
     * Builds the phrase suggestion builder with all given settings
     *
     * @return the PhraseSuggestionBuilder
     */
    private PhraseSuggestionBuilder generateSuggestionBuilder() {
        PhraseSuggestionBuilder phraseSuggestionBuilder = new PhraseSuggestionBuilder("suggestPhrase");
        phraseSuggestionBuilder.field(field)
                               .text(query)
                               .maxErrors(maxErrors)
                               .size(limit)
                               .analyzer(analyzer)
                               .confidence(confidence)
                               .collateQuery(collateQuery)
                               .collateParams(collateParams)
                               .collatePrune(collatePrune);

        if ((query.split("\\s+").length == 1) && (query.length() < 8)) {
            phraseSuggestionBuilder.addCandidateGenerator(new PhraseSuggestionBuilder.DirectCandidateGenerator(field).maxEdits(
                    1).suggestMode(suggestMode.name().toLowerCase()).minWordLength(4));
        } else {
            phraseSuggestionBuilder.addCandidateGenerator(new PhraseSuggestionBuilder.DirectCandidateGenerator(field).maxEdits(
                    2).suggestMode(suggestMode.name().toLowerCase()).minWordLength(4));
        }
        //phraseSuggestionBuilder.smoothingModel(new PhraseSuggestionBuilder.StupidBackoff(0.5));

        return phraseSuggestionBuilder;
    }

    /**
     * Builds the SuggestRequest to be given to the index
     *
     * @param phraseSuggestionBuilder the phrase suggestion
     * @return the index suggest request builder
     */
    private SuggestRequestBuilder generateRequestBuilder(PhraseSuggestionBuilder phraseSuggestionBuilder) {
        return index.getClient()
                    .prepareSuggest(index.getIndexName(index.getDescriptor(clazz).getIndex()))
                    .addSuggestion(phraseSuggestionBuilder);
    }

    /**
     * Executes the suggest request and returns a list of tuples of the query string and the suggest options.
     *
     * @return tuples of the query string and all suggest options
     */
    private List<PhraseSuggestion.Entry.Option> execute(SuggestRequestBuilder sqb) {

        SuggestResponse response = sqb.execute().actionGet();
        PhraseSuggestion phraseSuggestion = response.getSuggest().getSuggestion("suggestPhrase");
        List<PhraseSuggestion.Entry> entryList = phraseSuggestion.getEntries();

        if (entryList != null && entryList.get(0) != null && entryList.get(0).getOptions() != null) {
            return entryList.get(0).getOptions();
        } else {
            return Collections.emptyList();
        }
    }
}