/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import com.google.common.collect.Lists;
import org.elasticsearch.index.query.*;
import parsii.tokenizer.LookaheadReader;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Strings;
import sirius.search.IndexAccess;
import sirius.search.Query;

import java.io.StringReader;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

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
    private final String input;
    private final String defaultField;
    private final Function<String, Iterable<List<String>>> tokenizer;
    private boolean autoExpand;
    private QueryBuilder finishedQuery;
    private Monoflop parsed;

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
        this.input = input;
        this.defaultField = defaultField;
        this.tokenizer = tokenizer;
        this.autoExpand = autoExpand;
        this.parsed = Monoflop.create();
        this.finishedQuery = createQuery();
    }

    @Override
    public QueryBuilder createQuery() {
        if (parsed.firstCall()) {
            LookaheadReader reader = new LookaheadReader(new StringReader(input));
            QueryBuilder main = parseQuery(reader, autoExpand);
            if (!reader.current().isEndOfInput()) {
                IndexAccess.LOG.FINE("Unexpected character in query: " + reader.current());
            }
            // If we cannot compile a query from a non empty input, we probably dropped all short tokens
            // like a search for "S 8" would be completely dropped. Therefore we resort to "S8".
            if (main == null && !Strings.isEmpty(input) && input.contains(" ")) {
                reader = new LookaheadReader(new StringReader(input.replaceAll("\\s", "")));
                main = parseQuery(reader, autoExpand);
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
        return finishedQuery == null;
    }

    private void skipWhitespace(LookaheadReader reader) {
        while (reader.current().isWhitepace()) {
            reader.consume();
        }
    }

    /*
     * Entry point of the recursive descendant parser. Parses all input until a ) or its end is reached
     */
    private QueryBuilder parseQuery(LookaheadReader reader, boolean executeAutoexpansion) {
        while (!reader.current().isEndOfInput() && !reader.current().is(')')) {
            skipWhitespace(reader);

            List<QueryBuilder> bqb = parseOR(reader);
            if (!bqb.isEmpty()) {
                if (bqb.size() == 1) {
                    String singleToken = obtainSingleToken(input);
                    if (executeAutoexpansion
                        && singleToken != null
                        && bqb.get(0) instanceof TermQueryBuilder
                        && singleToken.length() >= EXPANSION_TOKEN_MIN_LENGTH) {
                        return QueryBuilders.prefixQuery(defaultField, singleToken).rewrite("top_terms_256");
                    }
                    return bqb.get(0);
                }
                BoolQueryBuilder result = QueryBuilders.boolQuery();
                for (QueryBuilder qb : bqb) {
                    result.should(qb);
                }
                return result;
            }
        }
        return null;
    }

    private String obtainSingleToken(String input) {
        Iterator<List<String>> tokenIter = tokenizer.apply(input).iterator();
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
    private List<QueryBuilder> parseOR(LookaheadReader reader) {
        List<QueryBuilder> result = Lists.newArrayList();
        List<QueryBuilder> subQuery = parseAND(reader);
        if (!subQuery.isEmpty()) {
            parseSubQuery(result, subQuery);
        }
        while (!reader.current().isEndOfInput()) {
            skipWhitespace(reader);
            if (isAtOR(reader)) {
                reader.consume(2);
                subQuery = parseAND(reader);
                if (!subQuery.isEmpty()) {
                    parseSubQuery(result, subQuery);
                }
            } else {
                break;
            }
        }
        return result;
    }

    private void parseSubQuery(List<QueryBuilder> result, List<QueryBuilder> subQuery) {
        if (subQuery.size() == 1) {
            result.add(subQuery.get(0));
        } else {
            BoolQueryBuilder qry = QueryBuilders.boolQuery();
            for (QueryBuilder qb : subQuery) {
                qry.must(qb);
            }
            result.add(qry);
        }
    }

    /*
     * Parses all tokens and occurrences of AND into a list of queries to be combined with the AND operator.
     */
    private List<QueryBuilder> parseAND(LookaheadReader reader) {
        List<QueryBuilder> result = Lists.newArrayList();
        List<QueryBuilder> subQuery = parseToken(reader);
        if (!subQuery.isEmpty()) {
            for (QueryBuilder qb : subQuery) {
                result.add(qb);
            }
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
            if (!subQuery.isEmpty()) {
                for (QueryBuilder qb : subQuery) {
                    result.add(qb);
                }
            }
        }
        return result;
    }

    private boolean isAtBinaryAND(LookaheadReader reader) {
        return reader.current().is('&') && reader.next().is('|');
    }

    private boolean isAtAND(LookaheadReader reader) {
        return reader.current().is('a', 'A') && reader.next().is('n', 'N') && reader.next(2).is('d', 'D');
    }

    private boolean isAtOR(LookaheadReader reader) {
        return (reader.current().is('o', 'O') && reader.next().is('r', 'R')) || (reader.current().is('|')
                                                                                 && reader.next().is('|'));
    }

    /*
     * Parses a token or an expression in brackets
     */
    private List<QueryBuilder> parseToken(LookaheadReader reader) {
        if (reader.current().is('(')) {
            return parseTokenInBrackets(reader);
        }
        String currentField = defaultField;
        boolean couldBeFieldNameSoFar = true;
        StringBuilder sb = new StringBuilder();
        boolean negate = checkIfNegated(reader);
        while (!reader.current().isEndOfInput() && !reader.current().isWhitepace() && !reader.current().is(')')) {
            if (reader.current().is(':') && sb.length() > 0 && couldBeFieldNameSoFar) {
                currentField = sb.toString();
                autoExpand = false;
                sb = new StringBuilder();
                // Ignore :
                reader.consume();
                couldBeFieldNameSoFar = false;
            } else {
                if (couldBeFieldNameSoFar) {
                    couldBeFieldNameSoFar =
                            reader.current().is('-', '_') || reader.current().isLetter() || reader.current().isDigit();
                }
                sb.append(reader.consume().getValue());
            }
        }
        if (sb.length() > 0) {
            return compileToken(currentField, sb, negate);
        }

        return Collections.emptyList();
    }

    private boolean checkIfNegated(LookaheadReader reader) {
        boolean negate = false;
        if (reader.current().is('-')) {
            negate = true;
            reader.consume();
        } else if (reader.current().is('+')) {
            // + is default behaviour and therefore just accepted and ignored to be compatible...
            reader.consume();
        }
        if (negate) {
            autoExpand = false;
        }
        return negate;
    }

    private List<QueryBuilder> parseTokenInBrackets(LookaheadReader reader) {
        reader.consume();
        QueryBuilder qb = parseQuery(reader, false);
        if (reader.current().is(')')) {
            reader.consume();
        }
        if (qb != null) {
            return Collections.singletonList(qb);
        } else {
            return Collections.emptyList();
        }
    }

    private List<QueryBuilder> compileToken(String field, StringBuilder sb, boolean negate) {
        String value = sb.toString();
        if (value.endsWith("*")) {
            // + 1 to compensate for the "*" in the string
            if (value.length() >= EXPANSION_TOKEN_MIN_LENGTH + 1) {
                return compileTokenWithAsterisk(field, negate, value);
            } else if (value.length() == 1) {
                return Collections.emptyList();
            } else {
                value = value.substring(0, value.length() - 1);
            }
        }

        List<QueryBuilder> result = Lists.newArrayList();
        BoolQueryBuilder qry = QueryBuilders.boolQuery();

        if (field.equals(defaultField)) {
            for (List<String> tokens : tokenizer.apply(value)) {
                if (tokens != null && !tokens.isEmpty()) {
                    QueryBuilder qb = transformTokenList(field, tokens);
                    if (negate) {
                        qry.mustNot(qb);
                    } else {
                        result.add(qb);
                    }
                }
            }
        } else {
            // if we're looking for an id, the field is called _id in elastic search.
            if ("id".equals(field)) {
                field = "_id";
            }
            if ("-".equals(value)) {
                result.add(QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(field)));
            } else {
                if (negate) {
                    qry.mustNot(QueryBuilders.termQuery(field, value));
                } else {
                    result.add(QueryBuilders.termQuery(field, value));
                }
            }
        }
        if (negate) {
            if (qry.hasClauses()) {
                return Collections.singletonList(qry);
            } else {
                return Collections.emptyList();
            }
        } else {
            return result;
        }
    }

    private List<QueryBuilder> compileTokenWithAsterisk(String field, boolean negate, String value) {
        if (negate) {
            BoolQueryBuilder qry = QueryBuilders.boolQuery();
            qry.mustNot(QueryBuilders.prefixQuery(field, value.substring(0, value.length() - 1).toLowerCase()));
            return Collections.singletonList(qry);
        } else {
            return Collections.singletonList(QueryBuilders.prefixQuery(field,
                                                                       value.substring(0, value.length() - 1)
                                                                            .toLowerCase()).rewrite("top_terms_256"));
        }
    }

    private QueryBuilder transformTokenList(String field, List<String> tokens) {
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
