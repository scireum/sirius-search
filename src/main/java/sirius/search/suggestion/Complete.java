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
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.search.suggest.completion.CompletionSuggestionFuzzyBuilder;
import sirius.search.Entity;
import sirius.search.IndexAccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a completion against the database which are created via {@link IndexAccess#complete(Class)}.
 * <p>
 * A completion can ONLY be used on fields annotated with {@link sirius.search.annotations.NestedObject} and  {@link
 * sirius.search.annotations.FastCompletion}. This is necessary as elasticsearch internally builds a fast datastructure
 * (FST) to allow fast completions.
 *
 * @param <E> the type of entities for which a field should be completed
 */
public class Complete<E extends Entity> {

    private IndexAccess index;
    private Class<E> clazz;
    private String field;
    private String query;
    private int limit = 5;
    private String contextName;
    private List<String> contextValues;
    private Fuzziness fuzziness;

    public Complete(IndexAccess index, Class<E> clazz) {
        this.index = index;
        this.clazz = clazz;
    }

    /**
     * Sets the query that should be completed and the field that should be used
     *
     * @param field a field that is annotated with {@link sirius.search.annotations.NestedObject} and  {@link
     *              sirius.search.annotations.FastCompletion}
     * @param query the string that should be completed
     * @return a newly created completion helper
     */
    public Complete<E> on(String field, String query) {
        this.field = field;
        this.query = query;
        return this;
    }

    /**
     * Limits the number of completions that are generated
     *
     * @param limit the maximum number of completions to generate
     * @return the completion helper itself for fluent method calls
     */
    public Complete<E> limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Allows to restrict/filter completions by adding context.
     *
     * @param name   name of the context defined in the field mapping
     * @param values values that should be used to restrict/filter completions
     * @return the completion helper itself for fluent method calls
     */
    public Complete<E> context(String name, List<String> values) {
        this.contextName = name;
        this.contextValues = values;
        return this;
    }

    /**
     * Sets the fuzziness for the completer to compensate misspellings
     *
     * @param fuzziness defines the levenshtein distance
     * @return the completion helper itself for fluent method calls
     */
    public Complete<E> fuzziness(Fuzziness fuzziness) {
        this.fuzziness = fuzziness;
        return this;
    }

    /**
     * Executes available completion-options or an empty list otherwise
     *
     * @return the generated completions or an empty list
     */
    public List<CompletionSuggestion.Entry.Option> completeList() {
        CompletionSuggestionFuzzyBuilder csb = generateCompletionBuilder();
        SuggestRequestBuilder sqb = generateRequestBuilder(csb);
        return execute(sqb);
    }

    private List<CompletionSuggestion.Entry.Option> execute(SuggestRequestBuilder sqb) {
        SuggestResponse response = sqb.execute().actionGet();
        CompletionSuggestion completionSuggestion = response.getSuggest().getSuggestion("completion");
        ArrayList<String> completions = new ArrayList<>();

        List<CompletionSuggestion.Entry> entryList = completionSuggestion.getEntries();

        if (entryList != null && entryList.get(0) != null && entryList.get(0).getOptions() != null) {
            return entryList.get(0).getOptions();
        }

        return Collections.emptyList();
    }

    private SuggestRequestBuilder generateRequestBuilder(CompletionSuggestionFuzzyBuilder csb) {
        return index.getClient()
                    .prepareSuggest(index.getIndexName(index.getDescriptor(clazz).getIndex()))
                    .addSuggestion(csb);
    }

    private CompletionSuggestionFuzzyBuilder generateCompletionBuilder() {
        CompletionSuggestionFuzzyBuilder csfb = new CompletionSuggestionFuzzyBuilder("completion");

        csfb.field(field);
        csfb.size(limit);
        csfb.text(query);
        csfb.addContextField(contextName, contextValues);
        csfb.setFuzziness(fuzziness);

        return csfb;
    }
}
