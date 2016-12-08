package sirius.search;

import org.elasticsearch.action.suggest.SuggestRequestBuilder;
import org.elasticsearch.action.suggest.SuggestResponse;
import org.elasticsearch.search.suggest.phrase.PhraseSuggestion;
import org.elasticsearch.search.suggest.phrase.PhraseSuggestionBuilder;
import sirius.kernel.commons.Tuple;

import java.util.ArrayList;
import java.util.List;

public class Suggest<E extends Entity> {
    private Class<E> clazz;
    private String field = "_all";
    private String query;
    /* maxErrors => errors for the whole query
       maxEdit => errors per token */
    private Float maxErrors = 2f; // config? 1 or 2 recommended
    private int limit = 5;
    private Float confidence = 1f;
    private String suggestMode = "missing";
    private String analyzer = "whitespace";

    /**
     * Used to create a new suggestion for entities of the given class
     *
     * @param clazz the type of entities to suggest for
     */
    protected Suggest(Class<E> clazz) {
        this.clazz = clazz;
    }

    /**
     * Sets the query string to generate suggestions for and the field to generate the suggestions from
     *
     * @param field the field to get the suggestions from
     * @param query the query to generate suggestions for
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
     */
    public Suggest<E> limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Wraps executing methods and returns a list of the generated suggestion phrases combined in tuples with the
     * original query string.
     *
     * @return the generated suggestion phrases
     */
    public List<Tuple<String, String>> suggestList() {
        PhraseSuggestionBuilder psb = build();
        SuggestRequestBuilder sqb = buildSuggest(psb);
        return execute(sqb);
    }

    /**
     * Sets the analyzer to analyze the query with.
     * <p>
     * Defaults to the search analyzer of the suggest field passed via 'field'.
     *
     * @param analyzer the analyzer to analyze the query with
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
     */
    public Suggest<E> confidence(Float confidence) {
        this.confidence = confidence;
        return this;
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
     */
    public Suggest<E> suggestMode(String suggestMode) {
        this.suggestMode = suggestMode;
        return this;
    }

    /**
     * Builds the phrase suggestion builder with all given settings
     *
     * @return the PhraseSuggestionBuilder
     */
    private PhraseSuggestionBuilder build() {
        //should we also suggest for phrases containing numbers etc? so, also itemNumbers? atm we do
        PhraseSuggestionBuilder phraseSuggestionBuilder = new PhraseSuggestionBuilder("suggestPhrase");
        phraseSuggestionBuilder.field(field); // nötig?
        phraseSuggestionBuilder.text(query);
        phraseSuggestionBuilder.maxErrors(maxErrors);
        phraseSuggestionBuilder.size(limit);
        phraseSuggestionBuilder.analyzer(analyzer);
        phraseSuggestionBuilder.confidence(confidence);

        if ((query.split("\\s+").length == 1) && (query.length() < 8)) {
            phraseSuggestionBuilder.addCandidateGenerator(new PhraseSuggestionBuilder.DirectCandidateGenerator(field).maxEdits(
                    1).suggestMode(suggestMode).minWordLength(4));
        } else {
            phraseSuggestionBuilder.addCandidateGenerator(new PhraseSuggestionBuilder.DirectCandidateGenerator(field).maxEdits(
                    2).suggestMode(suggestMode).minWordLength(4));
        }

        return phraseSuggestionBuilder;
    }

    /**
     * Builds the SuggestRequest to be given to the index
     *
     * @param phraseSuggestionBuilder the phrase suggestion
     * @return the index suggest request builder
     */
    private SuggestRequestBuilder buildSuggest(PhraseSuggestionBuilder phraseSuggestionBuilder) {

        return Index.getClient()
                    .prepareSuggest(Index.getIndexName(Index.getDescriptor(clazz).getIndex()))
                    .addSuggestion(phraseSuggestionBuilder);
    }

    /**
     * Executes the suggest request and returns a list of tuples of the query string and the suggest options.
     * <p>
     *
     * @return tuples of the query string and all suggest options
     */
    private List<Tuple<String, String>> execute(SuggestRequestBuilder sqb) {

        SuggestResponse response = sqb.execute().actionGet();
        PhraseSuggestion phraseSuggestion = response.getSuggest().getSuggestion("suggestPhrase");
        ArrayList<Tuple<String, String>> suggestions = new ArrayList<>();

        if (phraseSuggestion.getEntries() != null) {
            List<PhraseSuggestion.Entry> entryList = phraseSuggestion.getEntries();
            for (PhraseSuggestion.Entry entry : entryList) {
                List<PhraseSuggestion.Entry.Option> options = entry.getOptions();
                if (!options.isEmpty()) {
                    float lastMaxScore = options.get(0).getScore();
                    for (PhraseSuggestion.Entry.Option option : options) {
                        if ((lastMaxScore > option.getScore() * 2)) {
                            continue;
                        }
                        Tuple<String, String> typoCorrectedPair =
                                new Tuple<>(entry.getText().toString(), option.getText().toString());
                        suggestions.add(typoCorrectedPair);
                    }
                }
            }
        }
        //phraseSuggestionBuilder.clearCandidateGenerators();
        return suggestions;
    }
}
