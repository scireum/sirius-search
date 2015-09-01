/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import com.google.common.collect.Lists;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.SpanNearQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import parsii.tokenizer.LookaheadReader;
import sirius.search.constraints.Wrapper;

import java.io.StringReader;
import java.util.Collections;
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
class RobustQueryParser {

    private String defaultField;
    private String input;
    private Function<String, Iterable<List<String>>> tokenizer;
    private boolean autoexpand;

    /**
     * Creates a parser using the given query, defaultField and analyzer.
     *
     * @param defaultField the default field to search in, if no field is given.
     * @param input        the query to parse
     * @param tokenizer    the function to used for tokenization of the input
     * @param autoexpand   should single token queries be auto expanded. That will put a "*" after the resulting token
     *                     but limits the number of expansions to the top 256 terms.
     */
    RobustQueryParser(String defaultField,
                      String input,
                      Function<String, Iterable<List<String>>> tokenizer,
                      boolean autoexpand) {
        this.defaultField = defaultField;
        this.input = input;
        this.tokenizer = tokenizer;
        this.autoexpand = autoexpand;
    }

    /**
     * Compiles an applies the query to the given one.
     *
     * @param query the query to enhance with the parsed result
     */
    void compileAndApply(Query<?> query) {
        LookaheadReader reader = new LookaheadReader(new StringReader(input));
        QueryBuilder main = parseQuery(reader);
        if (!reader.current().isEndOfInput()) {
            Index.LOG.FINE("Unexpected character in query: " + reader.current());
        }
        if (main != null) {
            Index.LOG.FINE("Compiled '%s' into '%s'", query, main);
            query.where(Wrapper.on(main));
        }
    }

    private void skipWhitespace(LookaheadReader reader) {
        while (reader.current().isWhitepace()) {
            reader.consume();
        }
    }

    /*
     * Entry point of the recursive descendant parser. Parses all input until a ) or its end is reached
     */
    private QueryBuilder parseQuery(LookaheadReader reader) {
        while (!reader.current().isEndOfInput() && !reader.current().is(')')) {
            skipWhitespace(reader);

            List<QueryBuilder> bqb = parseOR(reader);
            if (bqb != null) {
                if (bqb.size() == 1) {
                    if (autoexpand && bqb.get(0) instanceof TermQueryBuilder) {
                        return QueryBuilders.prefixQuery(defaultField, input.toLowerCase()).rewrite("top_terms_256");
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

    /*
     * Parses all tokens which are combined with AND and creates a subquery for each occurence of
     * OR. This way operator precedence is handled correctly.
     */
    private List<QueryBuilder> parseOR(LookaheadReader reader) {
        List<QueryBuilder> result = Lists.newArrayList();
        List<QueryBuilder> subQuery = parseAND(reader);
        if (!subQuery.isEmpty()) {
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
        while (!reader.current().isEndOfInput()) {
            skipWhitespace(reader);
            if (reader.current().is('o', 'O') && reader.next().is('r', 'R')) {
                reader.consume(2);
                subQuery = parseAND(reader);
                if (!subQuery.isEmpty()) {
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
            } else {
                break;
            }
        }
        return result;
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
            if (reader.current().is('o', 'O') && reader.next().is('r', 'R')) {
                break;
            }
            if (reader.current().is('a', 'A') && reader.next().is('n', 'N') && reader.next().is('d', 'D')) {
                // AND is the default operation -> ignore
                reader.consume(3);
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

    /*
     * Parses a token or an expression in brackets
     */
    private List<QueryBuilder> parseToken(LookaheadReader reader) {
        if (reader.current().is('(')) {
            return parseTokenInBrackets(reader);
        }
        String field = defaultField;
        boolean couldBeFieldNameSoFar = true;
        StringBuilder sb = new StringBuilder();
        boolean negate = checkIfNegated(reader);
        while (!reader.current().isEndOfInput() && !reader.current().isWhitepace() && !reader.current().is(')')) {
            if (reader.current().is(':') && sb.length() > 0 && couldBeFieldNameSoFar) {
                field = sb.toString();
                autoexpand = false;
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
            return compileToken(field, sb, negate);
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
            autoexpand = false;
        }
        return negate;
    }

    private List<QueryBuilder> parseTokenInBrackets(LookaheadReader reader) {
        reader.consume();
        QueryBuilder qb = parseQuery(reader);
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
        if (value.length() > 1 && value.endsWith("*")) {
            return compileTokenWithAsterisk(field, negate, value);
        }

        List<QueryBuilder> result = Lists.newArrayList();
        BoolQueryBuilder qry = QueryBuilders.boolQuery();

        if (field.equals(defaultField)) {
            for (List<String> tokens : tokenizer.apply(value)) {
                QueryBuilder qb = transformTokenList(field, tokens);
                if (negate) {
                    qry.mustNot(qb);
                } else {
                    result.add(qb);
                }
            }
        } else {
            // if we're looking for an id, the field is called _id in elastic search.
            if ("id".equals(field)) {
                field = "_id";
            }

            if (negate) {
                qry.mustNot(QueryBuilders.termQuery(field, value));
            } else {
                result.add(QueryBuilders.termQuery(field, value));
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
            SpanNearQueryBuilder builder = QueryBuilders.spanNearQuery();
            builder.slop(3);
            for (String token : tokens) {
                builder.clause(QueryBuilders.spanTermQuery(field, token));
            }
            return builder;
        }
    }
}
