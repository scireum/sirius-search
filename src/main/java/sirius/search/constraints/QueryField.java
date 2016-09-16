/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import sirius.search.RobustQueryParser;

import java.util.List;
import java.util.function.Function;

/**
 * Represents a constraint which queries the given field.
 */
public class QueryField implements Constraint {

    private final String input;
    private final String field;
    private final Function<String, Iterable<List<String>>> tokenizer;
    private final boolean autoExpand;

    /*
     * Use the #on(Object, String) factory method
     */
    private QueryField(String input,
                       String field,
                       Function<String, Iterable<List<String>>> tokenizer,
                       boolean autoExpand) {
        this.input = input;
        this.field = field;
        this.tokenizer = tokenizer;
        this.autoExpand = autoExpand;
    }

    /**
     * Creates a new constraint which queriess the given field.
     *
     * @param input      the query to parse
     * @param field      the field to search in
     * @param tokenizer  the function to used for tokenization of the input
     * @param autoExpand should single token queries be auto expanded. That will put a "*" after the resulting token
     *                   but limits the number of expansions to the top 256 terms.
     * @return the newly created constraint
     */
    public static QueryField on(String input,
                                String field,
                                Function<String, Iterable<List<String>>> tokenizer,
                                boolean autoExpand) {
        return new QueryField(input, field, tokenizer, autoExpand);
    }

    @Override
    public QueryBuilder createQuery() {
        return new RobustQueryParser(field, input, tokenizer, autoExpand).compile();
    }

    @Override
    public FilterBuilder createFilter() {
        return null;
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return "'" + (skipConstraintValues ? "?" : input) + "' QUERY " + field;
    }
}
