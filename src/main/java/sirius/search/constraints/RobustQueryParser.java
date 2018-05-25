/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import com.google.common.collect.Lists;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.SpanNearQueryBuilder;
import parsii.tokenizer.LookaheadReader;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.search.IndexAccess;
import sirius.search.Query;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Used to parse user queries.
 * <p>
 * Tries to emulate most of the lucene query syntax like "field:token", "-token", "AND", "OR" and prefix queries
 * ("test*"). In contrast to the regular query parser, this will never fail but do its best to create a query as
 * intended by the user. Therefore something like '1/4"' or 'test(' won't throw an error but try a search with all
 * usable tokens.
 *
 * @see Query#query(String)
 */
public class RobustQueryParser implements Constraint {

    private static final int EXPANSION_TOKEN_MIN_LENGTH = 2;
    private static final Pattern EXPANDABLE_INPUT = Pattern.compile("[\\p{L}\\d][^\\s:]+");
    private final String input;
    private final String defaultField;
    private final Function<String, Iterable<List<String>>> tokenizer;
    private final boolean autoExpand;
    private final Monoflop parsed;
    private QueryBuilder finishedQuery;

    /**
     * Creates a new constraint which queriess the given field.
     *
     * @param input        the query to parse
     * @param defaultField the defaultField to search in
     * @param tokenizer    the function to used for tokenization of the input
     * @param autoExpand   should single token queries be auto expanded. That will put a "*" after the resulting token
     *                     but limits the number of expansions to the top 256 terms.
     */
    public RobustQueryParser(String input,
                             String defaultField,
                             Function<String, Iterable<List<String>>> tokenizer,
                             boolean autoExpand) {
        this.input = Value.of(input).trim();
        this.defaultField = defaultField;
        this.tokenizer = tokenizer;
        this.autoExpand = autoExpand;
        this.parsed = Monoflop.create();
    }

    @Override
    public QueryBuilder createQuery() {
        if (parsed.firstCall()) {
            LookaheadReader reader = new LookaheadReader(new StringReader(input));
            QueryBuilder main = parseQuery(reader);
            if (!reader.current().isEndOfInput()) {
                IndexAccess.LOG.FINE("Unexpected character in query: " + reader.current());
            }
            // If we cannot compile a query from a non empty input, we probably dropped all short tokens
            // like a search for "S 8" would be completely dropped. Therefore we resort to "S8".
            if (main == null && !Strings.isEmpty(input) && input.contains(" ")) {
                reader = new LookaheadReader(new StringReader(input.replaceAll("\\s", "")));
                main = parseQuery(reader);
            }
            finishedQuery = main;
        }
        return finishedQuery;
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return "'" + (skipConstraintValues ? "?" : input) + "' ROBUST QUERY IN " + defaultField;
    }

    public boolean isEmpty() {
        return createQuery() == null;
    }

    protected void skipWhitespace(LookaheadReader reader) {
        while (reader.current().isWhitepace()) {
            reader.consume();
        }
    }

    /*
     * Entry point of the recursive descendant parser. Parses all input until a ) or its end is reached
     */
    protected QueryBuilder parseQuery(LookaheadReader reader) {
        if (autoExpand) {
            QueryBuilder qry = tryAutoExpansion();
            if (qry != null) {
                return qry;
            }
        }

        return parseOR(reader);
    }

    protected QueryBuilder tryAutoExpansion() {
        if (!EXPANDABLE_INPUT.matcher(input).matches()) {
            return null;
        }

        String singleToken = obtainSingleToken(input);
        if (singleToken == null) {
            return null;
        }

        return compileTokenWithAsterisk(singleToken);
    }

    protected String obtainSingleToken(String input) {
        Iterator<List<String>> tokenIter = tokenizer.apply(input).iterator();
        if (!tokenIter.hasNext()) {
            return null;
        }
        List<String> firstList = tokenIter.next();
        if (tokenIter.hasNext()) {
            // We tokenized into several lists -> give up an return null
            return null;
        }
        if (firstList.size() == 1) {
            // Only return the first token if it is the only token in the list
            return firstList.get(0);
        }
        return null;
    }

    /*
     * Parses all tokens which are combined with AND and creates a subquery for each occurence of
     * OR. This way operator precedence is handled correctly.
     */
    protected QueryBuilder parseOR(LookaheadReader reader) {
        List<QueryBuilder> result = Lists.newArrayList();
        QueryBuilder subQuery = parseAND(reader);
        if (subQuery == null) {
            return null;
        }
        result.add(subQuery);

        while (!reader.current().isEndOfInput()) {
            skipWhitespace(reader);
            if (isAtOR(reader)) {
                reader.consume(2);
                subQuery = parseAND(reader);
                if (subQuery != null) {
                    result.add(subQuery);
                }
            } else {
                break;
            }
        }

        if (result.size() == 1) {
            return result.get(0);
        }

        BoolQueryBuilder qry = QueryBuilders.boolQuery();
        for (QueryBuilder qb : result) {
            qry.should(qb);
        }

        return qry;
    }

    /*
     * Parses all tokens and occurrences of AND into a list of queries to be combined with the AND operator.
     */
    protected QueryBuilder parseAND(LookaheadReader reader) {
        List<QueryBuilder> result = Lists.newArrayList();
        QueryBuilder subQuery = parseToken(reader);
        if (subQuery != null) {
            result.add(subQuery);
        }

        while (!reader.current().isEndOfInput() && !reader.current().is(')')) {
            skipWhitespace(reader);
            if (isAtOR(reader)) {
                break;
            }
            if (isAtAND(reader)) {
                // AND is the default operation -> ignore
                reader.consume(3);
            }
            if (isAtBinaryAND(reader)) {
                // && is the default operation -> ignore
                reader.consume(2);
            }
            subQuery = parseToken(reader);
            if (subQuery != null) {
                result.add(subQuery);
            }
        }

        if (result.isEmpty()) {
            return null;
        }

        if (result.size() == 1) {
            return result.get(0);
        }

        BoolQueryBuilder qry = QueryBuilders.boolQuery();
        for (QueryBuilder qb : result) {
            qry.must(qb);
        }

        return qry;
    }

    protected boolean isAtBinaryAND(LookaheadReader reader) {
        return reader.current().is('&') && reader.next().is('&');
    }

    protected boolean isAtAND(LookaheadReader reader) {
        return reader.current().is('a', 'A') && reader.next().is('n', 'N') && reader.next(2).is('d', 'D');
    }

    protected boolean isAtOR(LookaheadReader reader) {
        return (reader.current().is('o', 'O') && reader.next().is('r', 'R')) || (reader.current().is('|')
                                                                                 && reader.next().is('|'));
    }

    /*
     * Parses a token or an expression in brackets
     */
    protected QueryBuilder parseToken(LookaheadReader reader) {
        if (reader.current().is('(')) {
            return parseTokenInBrackets(reader);
        }

        boolean negate = checkIfNegated(reader);
        Tuple<String, String> fieldAndValue = parseFieldAndValue(reader);

        if (Strings.isEmpty(fieldAndValue.getSecond())) {
            return null;
        }

        QueryBuilder qry = compileToken(fieldAndValue.getFirst(), fieldAndValue.getSecond());
        if (qry != null && negate) {
            return QueryBuilders.boolQuery().mustNot(qry);
        } else {
            return qry;
        }
    }

    protected Tuple<String, String> parseFieldAndValue(LookaheadReader reader) {
        String field = defaultField;
        boolean couldBeFieldNameSoFar = true;
        StringBuilder valueBuilder = new StringBuilder();
        while (!reader.current().isEndOfInput() && !reader.current().isWhitepace() && !reader.current().is(')')) {
            if (reader.current().is(':') && valueBuilder.length() > 0 && couldBeFieldNameSoFar) {
                field = valueBuilder.toString();
                valueBuilder = new StringBuilder();
                // Ignore :
                reader.consume();
                couldBeFieldNameSoFar = false;
            } else {
                if (couldBeFieldNameSoFar) {
                    couldBeFieldNameSoFar =
                            reader.current().is('-', '_') || reader.current().isLetter() || reader.current().isDigit();
                }
                valueBuilder.append(reader.consume().getValue());
            }
        }

        return Tuple.create(field, valueBuilder.toString());
    }

    protected boolean checkIfNegated(LookaheadReader reader) {
        if (reader.current().is('-')) {
            reader.consume();
            return true;
        }

        if (reader.current().is('+')) {
            // + is default behaviour and therefore just accepted and ignored to be compatible...
            reader.consume();
        }

        return false;
    }

    protected QueryBuilder parseTokenInBrackets(LookaheadReader reader) {
        reader.consume();
        QueryBuilder qb = parseOR(reader);
        if (reader.current().is(')')) {
            reader.consume();
        }

        return qb;
    }

    protected QueryBuilder compileToken(String field, String value) {
        if (value.endsWith("*")) {
            // + 1 to compensate for the "*" in the string
            if (value.length() >= EXPANSION_TOKEN_MIN_LENGTH + 1 && Strings.areEqual(field, defaultField)) {
                return compileTokenWithAsterisk(value.substring(0, value.length() - 1));
            } else if (value.length() == 1) {
                return null;
            } else {
                value = value.substring(0, value.length() - 1);
            }
        }

        if ("id".equals(field)) {
            field = "_id";
        }

        return createTokenConstraint(field, value);
    }

    protected QueryBuilder createTokenConstraint(String field, String value) {

        if (field.equals(defaultField)) {
            return createTokenizedConstraint(field, value);
        }

        if ("-".equals(value)) {
            return QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(field));
        }

        return QueryBuilders.termQuery(field, value);
    }

    protected QueryBuilder createTokenizedConstraint(String field, String value) {
        List<List<String>> tokenLists = new ArrayList<>();
        tokenizer.apply(value).forEach(tokenLists::add);
        if (tokenLists.isEmpty()) {
            return null;
        }
        if (tokenLists.size() == 1) {
            return transformTokenList(field, tokenLists.get(0));
        }

        BoolQueryBuilder qry = QueryBuilders.boolQuery();
        for (List<String> tokens : tokenLists) {
            if (tokens != null && !tokens.isEmpty()) {
                QueryBuilder qb = transformTokenList(field, tokens);
                qry.must(qb);
            }
        }

        return qry;
    }

    protected QueryBuilder compileTokenWithAsterisk(String value) {
        return QueryBuilders.prefixQuery(defaultField, value.toLowerCase()).rewrite("top_terms_256");
    }

    protected QueryBuilder transformTokenList(String field, List<String> tokens) {
        if (tokens.size() == 1) {
            return QueryBuilders.termQuery(field, tokens.get(0));
        } else {
            Monoflop monoflop = Monoflop.create();
            SpanNearQueryBuilder builder = null;

            for (String token : tokens) {
                if (monoflop.firstCall()) {
                    builder = QueryBuilders.spanNearQuery(QueryBuilders.spanTermQuery(field, token), 3);
                } else {
                    builder.addClause(QueryBuilders.spanTermQuery(field, token));
                }
            }
            return builder;
        }
    }
}
