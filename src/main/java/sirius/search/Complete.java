/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import org.elasticsearch.action.suggest.SuggestRequestBuilder;
import org.elasticsearch.action.suggest.SuggestResponse;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.search.suggest.completion.CompletionSuggestionBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a completion against the database which are created via {@link Index#complete(Class)}.
 * <p>
 * A completion can ONLY be used on fields annotated with {@link sirius.search.annotations.NestedObject} and  {@link
 * sirius.search.annotations.FastCompletion}. This is necessary as elasticsearch internally builds a fast datastructure
 * (FST) to allow fast completions.
 *
 * @param <E> the type of entities for which a field should be completed
 */
public class Complete<E extends Entity> {

    private Class<E> clazz;
    private String field;
    private String query;
    private int limit = 5;
    private String contextName;
    private List<String> contextValues;

    protected Complete(Class<E> clazz) {
        this.clazz = clazz;
    }

    /**
     * Sets the query that should be completed and the field that should be used
     *
     * @param field a field that is annotated with {@link sirius.search.annotations.NestedObject} and  {@link
     *              sirius.search.annotations.FastCompletion}
     * @param query the string that should be completed
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
     */
    public Complete<E> limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Allows to restrict/filter completions by adding context.
     *
     * @param name name of the context defined in the field mapping
     * @param values values that should be used to restrict/filter completions
     * @return
     */
    public Complete<E> context(String name, List<String> values){
        this.contextName = name;
        this.contextValues = values;
        return this;
    }

    /**
     * Executes the completion
     *
     * @return the generated completions
     */
    public List<String> completeList() {
        CompletionSuggestionBuilder csb = generateCompletionBuilder();
        SuggestRequestBuilder sqb = generateRequestBuilder(csb);
        return execute(sqb);
    }

    private List<String> execute(SuggestRequestBuilder sqb) {
        SuggestResponse response = sqb.execute().actionGet();
        CompletionSuggestion completionSuggestion = response.getSuggest().getSuggestion("completion");
        ArrayList<String> completions = new ArrayList<>();

        List<CompletionSuggestion.Entry> entryList = completionSuggestion.getEntries();

        if (entryList != null) {
            for (CompletionSuggestion.Entry entry : entryList) {
                List<CompletionSuggestion.Entry.Option> options = entry.getOptions();

                if (options != null) {
                    for (CompletionSuggestion.Entry.Option option : options) {
                        completions.add(option.getText().toString());
                    }
                }
            }
        }
        return completions;
    }

    private SuggestRequestBuilder generateRequestBuilder(CompletionSuggestionBuilder csb) {
        return Index.getClient()
                    .prepareSuggest(Index.getIndexName(Index.getDescriptor(clazz).getIndex()))
                    .addSuggestion(csb);
    }

    private CompletionSuggestionBuilder generateCompletionBuilder() {
        CompletionSuggestionBuilder csb = new CompletionSuggestionBuilder("completion");
        csb.field(field);
        csb.size(limit);
        csb.text(query);
        csb.addContextField(contextName, contextValues);
        return csb;
    }
}
