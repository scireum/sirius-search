/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.suggestion;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.search.suggest.completion.CompletionSuggestionBuilder;
import org.elasticsearch.search.suggest.completion.RegexOptions;
import sirius.search.Entity;
import sirius.search.IndexAccess;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private Map<String, List<? extends ToXContent>> contexts;
    private Fuzziness fuzziness = Fuzziness.AUTO;
    private boolean isRegexQuery = false;
    private int maxDeterminizedStates = -1;

    /**
     * Creates a new completion for the given index access and entity type.
     *
     * @param index the instance of <tt>IndexAccess</tt> being used
     * @param clazz the entity type to operate on
     */
    public Complete(IndexAccess index, Class<E> clazz) {
        this.index = index;
        this.clazz = clazz;
    }

    /**
     * Defines the field which is used for completion
     *
     * @param field a field of type {@link AutoCompletion}
     * @return a newly created completion helper
     */
    public Complete<E> on(String field) {
        this.field = field;
        return this;
    }

    /**
     * Sets a regex as completion query.
     *
     * @param regex the query regex
     * @return the completion helper itself for fluent method calls
     */
    public Complete<E> regex(String regex) {
        this.query = regex;
        this.isRegexQuery = true;
        return this;
    }

    /**
     * Sets the query to a prefix that must be matched.
     *
     * @param prefix the query prefix
     * @return the completion helper itself for fluent method calls
     */
    public Complete<E> prefix(String prefix) {
        this.query = prefix;
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
     * Specifies the maximal number of determinized states.
     *
     * @param maxDeterminizedStates the maximal number of determinized states
     * @return the completion helper itself for fluent method calls
     */
    public Complete<E> maxDeterminizedStates(int maxDeterminizedStates) {
        this.maxDeterminizedStates = maxDeterminizedStates;
        return this;
    }

    /**
     * Allows to restrict/filter completions by adding context.
     *
     * @param contexts the contexts that should be applied
     * @return the completion helper itself for fluent method calls
     */
    public Complete<E> contexts(Map<String, List<? extends ToXContent>> contexts) {
        this.contexts = contexts;
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
        SuggestBuilder csb = generateCompletionBuilder();
        SearchRequestBuilder sqb = generateRequestBuilder(csb);
        return execute(sqb);
    }

    private List<CompletionSuggestion.Entry.Option> execute(SearchRequestBuilder sqb) {
        SearchResponse response = sqb.execute().actionGet();
        CompletionSuggestion completionSuggestion = response.getSuggest().getSuggestion("completion");

        if (completionSuggestion != null) {
            List<CompletionSuggestion.Entry> entryList = completionSuggestion.getEntries();

            if (entryList != null && entryList.get(0) != null && entryList.get(0).getOptions() != null) {
                return entryList.get(0).getOptions();
            }
        }
        return Collections.emptyList();
    }

    private SearchRequestBuilder generateRequestBuilder(SuggestBuilder builder) {
        return index.getClient()
                    .prepareSearch(index.getIndexName(index.getDescriptor(clazz).getIndex()))
                    .suggest(builder);
    }

    private SuggestBuilder generateCompletionBuilder() {
        CompletionSuggestionBuilder builder = SuggestBuilders.completionSuggestion(field).size(limit);

        if (isRegexQuery) {
            RegexOptions.Builder regexOptionsBuilder = RegexOptions.builder();

            if (maxDeterminizedStates != -1) {
                regexOptionsBuilder.setMaxDeterminizedStates(maxDeterminizedStates);
            }

            builder.regex(query, regexOptionsBuilder.build());
        } else {
            builder.prefix(query, fuzziness);
        }

        if (contexts != null) {
            builder.contexts(contexts);
        }

        return new SuggestBuilder().addSuggestion("completion", builder);
    }
}
