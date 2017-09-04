/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.range.date.DateRangeAggregationBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import sirius.kernel.async.ExecutionPoint;
import sirius.kernel.async.TaskContext;
import sirius.kernel.cache.ValueComputer;
import sirius.kernel.commons.Lambdas;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.RateLimit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.ValueHolder;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Microtiming;
import sirius.kernel.nls.NLS;
import sirius.search.aggregation.Aggregation;
import sirius.search.aggregation.bucket.BucketAggregation;
import sirius.search.constraints.Constraint;
import sirius.search.constraints.FieldEqual;
import sirius.search.constraints.FieldNotEqual;
import sirius.search.constraints.Filled;
import sirius.search.constraints.NoneInField;
import sirius.search.constraints.Or;
import sirius.search.constraints.QueryString;
import sirius.search.constraints.RobustQueryParser;
import sirius.search.constraints.ValueInField;
import sirius.search.properties.EnumProperty;
import sirius.search.properties.Property;
import sirius.web.controller.Facet;
import sirius.web.controller.Page;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents a query against the database which are created via {@link IndexAccess#select(Class)}.
 *
 * @param <E> the type of entities being queried
 */
public class Query<E extends Entity> {

    private static final int DEFAULT_LIMIT = 999;
    private static final int SCROLL_TTL_SECONDS = 60 * 5;
    private static final int MAX_SCROLL_RESULTS_FOR_SINGLE_SHARD = 50;
    private static final int MAX_SCROLL_RESULTS_PER_SHARD = 10;
    private static final int MAX_QUERY_LENGTH = 100;

    /**
     * Specifies tbe default field to search in used by {@link #query(String)}. Use
     * {@link #query(String, String, Function, boolean, boolean)} to specify a custom field.
     */
    public static final String DEFAULT_FIELD = "_all";

    @ConfigValue("index.termFacetLimit")
    private static int termFacetLimit;

    private Class<E> clazz;
    private List<Constraint> constraints = Lists.newArrayList();
    private List<Tuple<String, Boolean>> orderBys = Lists.newArrayList();
    private List<Facet> termFacets = Lists.newArrayList();
    private List<Aggregation> aggregations = Lists.newArrayList();
    private ScoreFunctionBuilder<?> scoreFunctionBuilder;
    private boolean randomize;
    private String randomizeField;
    private int start;
    private Integer limit = null;
    private String queryString;
    private int pageSize = 25;
    private boolean primary = false;
    private String index;
    private boolean forceFail = false;
    private String routing;
    protected boolean logQuery;
    // Used to signal that deliberately no routing was given
    private boolean deliberatelyUnrouted;
    private int scrollTTL = SCROLL_TTL_SECONDS;

    @Part
    private static IndexAccess indexAccess;

    /**
     * Used to create a nwe query for entities of the given class
     *
     * @param clazz the type of entities to query for
     */
    protected Query(Class<E> clazz) {
        this.clazz = clazz;
    }

    /**
     * Marks this query as failed or invalid. Therefore, no matter on what constraints are set, this query will always
     * return an empty result.
     * <p>
     * This method is intended for security checks which should not abort processing but just behave like the
     * query didn't match any entities.
     *
     * @return the query itself for fluent method calls
     */
    public Query<E> fail() {
        forceFail = true;
        return this;
    }

    /**
     * Adds the given constraints to the query.
     * <p>
     * All constraints are combined together using AND logic, meaning that all constraints need to be satisfied.
     *
     * @param constraints the array of constraints to add
     * @return the query itself for fluent method calls
     */
    public Query<E> where(Constraint... constraints) {
        this.constraints.addAll(Arrays.asList(constraints));
        return this;
    }

    /**
     * Adds the given list of alternative constraints to the query.
     * <p>
     * All constraints are combined together using OR logic, meaning that at least one constraint need to be satisfied.
     *
     * @param constraints the array of constraints to add
     * @return the query itself for fluent method calls
     */
    public Query<E> or(Constraint... constraints) {
        this.constraints.add(Or.on(constraints));
        return this;
    }

    /**
     * Adds an <tt>equals</tt> constraint for the given field and value.
     *
     * @param field the field to check
     * @param value the value to compare against
     * @return the query itself for fluent method calls
     * @see FieldEqual
     */
    public Query<E> eq(String field, Object value) {
        constraints.add(FieldEqual.on(field, value));
        return this;
    }

    /**
     * Adds an <tt>equals</tt> constraint for the given field if the given value is neither <tt>null</tt> nor an
     * empty string.
     *
     * @param field the field to check
     * @param value the value to compare against
     * @return the query itself for fluent method calls
     * @see FieldEqual
     */
    public Query<E> eqIgnoreNull(String field, Object value) {
        if (Strings.isFilled(value)) {
            eq(field, value);
        }
        return this;
    }

    /**
     * Adds a <tt>not equal</tt> constraint for the given field and value.
     *
     * @param field the field to check
     * @param value the value to compare against
     * @return the query itself for fluent method calls
     * @see FieldNotEqual
     */
    public Query<E> notEq(String field, Object value) {
        constraints.add(FieldNotEqual.on(field, value));
        return this;
    }

    /**
     * Adds a <tt>filled</tt> constraint for the given field and value.
     *
     * @param field the field to check
     * @return the query itself for fluent method calls
     * @see Filled
     */
    public Query<E> filled(String field) {
        constraints.add(Filled.on(field));
        return this;
    }

    /**
     * Adds an <tt>in</tt> constraint for the given field and value.
     * <p>
     * This requires the field to have at least the given value in it (might have others as well).
     *
     * @param field the field to check
     * @param value the value to compare against
     * @return the query itself for fluent method calls
     * @see ValueInField
     */
    public Query<E> in(String field, Object value) {
        constraints.add(ValueInField.on(value, field));
        return this;
    }

    /**
     * Adds an <tt>exclude</tt> constraint to the query in the id field.
     * <p>
     * This effectively excludes all entities from the given list from the query.
     *
     * @param entities the entities to exclude
     * @return the query itself for fluent method calls
     * @see NoneInField
     */
    public Query<E> exclude(EntityRefList<E> entities) {
        constraints.add(NoneInField.on(entities.getIds(), IndexAccess.ID_FIELD));
        return this;
    }

    /**
     * Adds a textual query using the given field.
     * <p>
     * It will try to parse queries in Lucene syntax (+token, -token, AND, OR and brackets are supported) but it
     * will never fail for a malformed query. If such a case, the valid tokens are sent to the server.
     *
     * @param query        the query to search for
     * @param defaultField the default field to search in
     * @param tokenizer    the function to use for tokenization
     * @param autoexpand   determines if for single term queries an expansion (like term*) should be added
     * @param forced       if <tt>true</tt> the query will fail (not return any results) if no valid input query was
     *                     given (i.e. empty text)
     * @return the query itself for fluent method calls
     */
    public Query<E> query(String query,
                          String defaultField,
                          Function<String, Iterable<List<String>>> tokenizer,
                          boolean autoexpand,
                          boolean forced) {
        if (Strings.isFilled(query)) {
            this.queryString = detectLogging(query);
            if (query.length() > MAX_QUERY_LENGTH) {
                throw Exceptions.createHandled().withNLSKey("Query.queryTooLong").handle();
            }
            RobustQueryParser constraint = new RobustQueryParser(this.queryString, defaultField, tokenizer, autoexpand);
            if (!constraint.isEmpty()) {
                if (IndexAccess.LOG.isFINE()) {
                    IndexAccess.LOG.FINE("Compiled '%s' into '%s'", query, constraint);
                }
                where(constraint);
            } else if (forced) {
                fail();
            }
        }

        return this;
    }

    /**
     * Adds a textual query across all searchable fields.
     * <p>
     * Uses the DEFAULT_FIELD and DEFAULT_ANALYZER while calling
     * {@link #query(String, String, Function, boolean, boolean)}.
     *
     * @param query the query to search for
     * @return the query itself for fluent method calls
     */
    public Query<E> query(String query) {
        return query(query, DEFAULT_FIELD, Query::defaultTokenizer, false, false);
    }

    /**
     * Adds a textual query to a specific field.
     * <p>
     * Uses the DEFAULT_ANALYZER while calling {@link #query(String, String, Function, boolean, boolean)}.
     *
     * @param query the query to search for
     * @param field the field to apply query to
     * @return the query itself for fluent method calls
     */
    public Query<E> query(String query, String field) {
        return query(query, field, Query::defaultTokenizer, false, false);
    }

    /**
     * Adds a textual query across all searchable fields.
     * <p>
     * If a single term query is given, an expansion like "term*" will be added.
     * <p>
     * Uses the DEFAULT_FIELD and DEFAULT_ANALYZER while calling
     * {@link #query(String, String, java.util.function.Function, boolean, boolean)}.
     *
     * @param query the query to search for
     * @return the query itself for fluent method calls
     */
    public Query<E> expandedQuery(String query) {
        return query(query, DEFAULT_FIELD, Query::defaultTokenizer, true, false);
    }

    /**
     * Adds a textual query to a specific field.
     * <p>
     * If a single term query is given, an expansion like "term*" will be added.
     * <p>
     * Uses the DEFAULT_ANALYZER while calling
     * {@link #query(String, String, java.util.function.Function, boolean, boolean)}.
     *
     * @param query the query to search for
     * @param field the field to apply query to
     * @return the query itself for fluent method calls
     */
    public Query<E> expandedQuery(String query, String field) {
        return query(query, field, Query::defaultTokenizer, true, false);
    }

    /**
     * Uses the StandardAnalyzer to perform tokenization.
     *
     * @param input the value to tokenize
     * @return a list of token based on the given input
     */
    public static Iterable<List<String>> defaultTokenizer(String input) {
        List<List<String>> result = Lists.newArrayList();

        try (StandardAnalyzer std = new StandardAnalyzer()) {
            TokenStream stream = std.tokenStream("std", input);
            stream.reset();
            while (stream.incrementToken()) {
                CharTermAttribute attr = stream.getAttribute(CharTermAttribute.class);
                String token = new String(attr.buffer(), 0, attr.length());
                result.add(Collections.singletonList(token));
            }
        } catch (IOException e) {
            Exceptions.handle(IndexAccess.LOG, e);
        }

        return result;
    }

    /**
     * Adds a textual query using the given field. If the given query is too short, no results will be
     * generated.
     * <p>
     * If a non-empty query string which contains at least two characters (without "*") is given, this will behave just
     * like {@link #query(String, String, Function, boolean, boolean)}. Otherwise the completed query will be failed by
     * calling {@link #fail()} to ensure that no results are generated.
     *
     * @param query        the query to search for
     * @param defaultField the default field to search in
     * @param tokenizer    the function to use for tokenization
     * @return the query itself for fluent method calls
     */
    public Query<E> forceQuery(String query, String defaultField, Function<String, Iterable<List<String>>> tokenizer) {
        return query(query, defaultField, tokenizer, false, true);
    }

    /**
     * Adds a textual query using the given field. If the given query is too short, no results will be
     * generated.
     * <p>
     * If a single term query is given, an expansion like "term*" will be added.
     * <p>
     * If a non-empty query string which contains at least two characters (without "*") is given, this will behave just
     * like {@link #query(String, String, Function, boolean, boolean)}. Otherwise the completed query will be failed by
     * calling {@link #fail()} to ensure that no results are generated.
     *
     * @param query        the query to search for
     * @param defaultField the default field to search in
     * @param tokenizer    the function to use for tokenization
     * @return the query itself for fluent method calls
     */
    public Query<E> forceExpandedQuery(String query,
                                       String defaultField,
                                       Function<String, Iterable<List<String>>> tokenizer) {
        return query(query, defaultField, tokenizer, true, true);
    }

    private String detectLogging(String query) {
        if (Strings.isFilled(query)) {
            if (query.startsWith("?")) {
                logQuery = true;
                return query.substring(1);
            }
        }

        return query;
    }

    /**
     * Adds a textual query across all searchable fields using Lucene syntax.
     * <p>
     * Consider using {@link #query(String)} which supports most of the Lucene syntax but will never fail for
     * a query.
     *
     * @param query the query to search for. Does actually support the complete Lucene query syntax like
     *              {@code field:value OR field2:value1}
     * @return the query itself for fluent method calls
     */
    public Query<E> directQuery(String query) {
        if (Strings.isFilled(query)) {
            where(QueryString.query(query));
        }

        return this;
    }

    /**
     * Sets the index to use.
     * <p>
     * For "normal" entities, the index is automatically computed but can be overridden by this method.
     *
     * @param indexToUse the name of the index to use. The index prefix used by this system will be automatically
     *                   added
     * @return the query itself for fluent method calls
     */
    public Query<E> index(String indexToUse) {
        this.index = indexAccess.getIndexName(indexToUse);
        return this;
    }

    /**
     * Sets the value used to perform custom routing.
     * <p>
     * This must match the value of the field as specified in <tt>routing</tt> in
     * {@link sirius.search.annotations.Indexed}.
     *
     * @param value the value used for custom routing
     * @return the query itself for fluent method calls
     */
    public Query<E> routing(String value) {
        this.routing = value;
        return this;
    }

    /**
     * If the entity being queries is routed, we try to preset the correct routing value or to disable routing.
     * <p>
     * This is used by {@link sirius.search.ForeignKey} as if cannot know if and how an entity is routed.
     * Therefore this method checks if by coincidence the given field and value (which is used by the foreign key
     * to filter entities) is also used to route those entities. If this is true, the routing is applied,
     * otherwise {@link #deliberatelyUnrouted()} is called.
     *
     * @param field the field for which a filter value is available
     * @param value the value of the field
     * @return the query itself for fluent method calls
     */
    protected Query<E> autoRoute(String field, String value) {
        EntityDescriptor descriptor = indexAccess.getDescriptor(clazz);
        if (!descriptor.hasRouting()) {
            return this;
        }
        if (Strings.areEqual(descriptor.getRouting(), field)) {
            routing(value);
        } else {
            deliberatelyUnrouted();
        }
        return this;
    }

    /**
     * Marks the query as deliberately unrouted.
     * <p>
     * This can be used to signal the system that an entity with a routing
     * (in {@link sirius.search.annotations.Indexed}) is deliberately queried without any routing
     *
     * @return the query itself for fluent method calls
     */
    public Query<E> deliberatelyUnrouted() {
        this.deliberatelyUnrouted = true;
        return this;
    }

    /**
     * Adds an order by clause for the given field in ascending order.
     *
     * @param field the field to order by
     * @return the query itself for fluent method calls
     */
    public Query<E> orderByAsc(String field) {
        orderBys.add(Tuple.create(field, true));
        return this;
    }

    /**
     * Adds an order by clause for the given field in descending order.
     *
     * @param field the field to order by
     * @return the query itself for fluent method calls
     */
    public Query<E> orderByDesc(String field) {
        orderBys.add(Tuple.create(field, false));
        return this;
    }

    /**
     * Picks random items from the result set instead of the first N.
     *
     * @return the query itself for fluent method calls
     */
    public Query<E> randomize() {
        randomize = true;
        return this;
    }

    /**
     * Picks random items from the result set instead of the first N.
     * <p>
     * A higher weight in the given field increases the chances of the entity to be part of the result.
     * </p>
     *
     * @param field the field to use as weight for the randomization.
     * @return the query itself for fluent method calls
     */
    public Query<E> randomizeWeightened(String field) {
        randomize = true;
        randomizeField = field;
        return this;
    }

    /**
     * Adds the given aggregration to be filled by the query
     *
     * @param aggregation the aggregation to fill
     * @return the query itself for fluent method calls
     */
    public Query<E> addAggregation(Aggregation aggregation) {
        aggregations.add(aggregation);
        return this;
    }

    /**
     * Adds the given score function to the query which can be used for fine grained scoring calculation.
     *
     * @param scoreFunctionBuilder the desired score function
     * @return the query itself for fluent method calls
     */
    public Query<E> addScoreFunction(ScoreFunctionBuilder<?> scoreFunctionBuilder) {
        this.scoreFunctionBuilder = scoreFunctionBuilder;
        return this;
    }

    /**
     * Adds a term facet to be filled by the query.
     *
     * @param field      the field to scan
     * @param value      the value to filter by
     * @param translator the translator used to turn field values into visible filter values
     * @return the query itself for fluent method calls
     */
    public Query<E> addTermFacet(String field, String value, ValueComputer<String, String> translator) {
        final Property p = indexAccess.getDescriptor(clazz).getProperty(field);
        ValueComputer<String, String> effectiveTranslator = translator;
        if (p instanceof EnumProperty && effectiveTranslator == null) {
            effectiveTranslator = v -> String.valueOf(((EnumProperty) p).transformFromSource(v));
        }
        termFacets.add(new Facet(p.getFieldTitle(), field, value, effectiveTranslator));
        if (Strings.isFilled(value)) {
            where(FieldEqual.on(field, value));
        }

        return this;
    }

    /**
     * Adds a term facet to be filled by the query. Loads the filter value directly from the given http request.
     *
     * @param field      the field to scan
     * @param request    the request to read the current filter value from
     * @param translator the translator used to turn field values into visible filter values
     * @return the query itself for fluent method calls
     */
    public Query<E> addTermFacet(String field, WebContext request, ValueComputer<String, String> translator) {
        addTermFacet(field, request.get(field).getString(), translator);
        return this;
    }

    /**
     * Adds a term facet to be filled by the query.
     *
     * @param field the field to scan
     * @param value the value to filter by
     * @return the query itself for fluent method calls
     */
    public Query<E> addTermFacet(String field, String value) {
        addTermFacet(field, value, null);
        return this;
    }

    /**
     * Adds a term facet to be filled by the query. Loads the filter value directly from the given http request.
     *
     * @param field   the field to scan
     * @param request the request to read the current filter value from
     * @return the query itself for fluent method calls
     */
    public Query<E> addTermFacet(String field, WebContext request) {
        addTermFacet(field, request.get(field).getString());
        return this;
    }

    /**
     * Adds a term facet for a boolean field to be filled by the query.
     *
     * @param field the field to scan
     * @param value the value to filter by
     * @return the query itself for fluent method calls
     */
    public Query<E> addBooleanTermFacet(String field, String value) {
        return addTermFacet(field, value, key -> NLS.get("true".equals(key) ? "NLS.yes" : "NLS.no"));
    }

    /**
     * Adds a term facet for a boolean field to be filled by the query.
     *
     * @param field   the field to scan
     * @param request the request to read the current filter value from
     * @return the query itself for fluent method calls
     */
    public Query<E> addBooleanTermFacet(String field, WebContext request) {
        return addTermFacet(field, request, key -> NLS.get("true".equals(key) ? "NLS.yes" : "NLS.no"));
    }

    /**
     * Adds a facet filter on a date field with the given ranges as facets (buckets).
     *
     * @param field  the field to filter on
     * @param value  the currently selected filter bucket
     * @param ranges the ranges to group entities by
     * @return the query itself for fluent method calls
     */
    public Query<E> addDateRangeFacet(String field, String value, DateRange... ranges) {
        final Property p = indexAccess.getDescriptor(clazz).getProperty(field);

        DateFacet dateFacet = new DateFacet(p.getFieldTitle(), field, value, ranges);
        termFacets.add(dateFacet);
        if (Strings.isFilled(value)) {
            DateRange range = dateFacet.getRangeByName(value);
            if (range != null) {
                range.applyToQuery(field, this);
            }
        }

        return this;
    }

    /**
     * Adds a facet filter on a date field with the given ranges as facets (buckets).
     *
     * @param field   the field to filter on
     * @param request a request to fetch the current filter value from
     * @param ranges  the ranges to group entities by
     * @return the query itself for fluent method calls
     */
    public Query<E> addDateRangeFacet(String field, WebContext request, DateRange... ranges) {
        addDateRangeFacet(field, request.get(field).getString(), ranges);
        return this;
    }

    /**
     * Sets the firsts index of the requested result slice.
     *
     * @param start the zero based index of the first requested item from within the result
     * @return the query itself for fluent method calls
     */
    public Query<E> start(int start) {
        this.start = Math.max(start, 0);
        return this;
    }

    /**
     * Sets the max. number of items to return.
     *
     * @param limit the max. number of items to return
     * @return the query itself for fluent method calls
     */
    public Query<E> limit(int limit) {
        this.limit = Math.max(0, limit);
        return this;
    }

    /**
     * Combines {@link #start(int)} and {@link #limit(int)} in one call.
     *
     * @param start the zero based index of the first requested item from within the result
     * @param limit the max. number of items to return
     * @return the query itself for fluent method calls
     */
    public Query<E> limit(int start, int limit) {
        return start(start).limit(limit);
    }

    /**
     * Sets the start and limit just like {@link #limit(int, int)} but performs additional checks (like limit
     * must always be less or equal to maxLimit.
     *
     * @param start    the zero based index of the first requested item from within the result
     * @param limit    the max. number of items to return. If the value is 0 or larger than maxLimit, it is
     *                 forced to maxLimit
     * @param maxLimit the maximal value allowed for limit
     * @return the query itself for fluent method calls
     */
    public Query<E> userLimit(int start, int limit, int maxLimit) {
        int effectiveLimit = limit;
        if (effectiveLimit < 1 || effectiveLimit > maxLimit) {
            effectiveLimit = maxLimit;
        }
        return start(start).limit(effectiveLimit);
    }

    /**
     * Sets a custom pageSize used in {@link #page(int start)} and {@link #queryPage()}.
     *
     * @param pageSize the desired number of elements in one page
     * @return the query itself for fluent method calls
     */
    public Query<E> withPageSize(int pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    /**
     * Setups start and limiting so that the result begins at the given start and contains at most one page
     * (defined by <tt>pageSize</tt>) items.
     *
     * @param start the one based index of the first item to return
     * @return the query itself for fluent method calls
     */
    public Query<E> page(int start) {
        int effectiveStart = Math.max(0, start - 1);
        return limit(effectiveStart, pageSize);
    }

    /**
     * Forces the framework to load the entities from their primary shard.
     * <p>
     * This can be used to minimize the risk of optimistic lock errors
     *
     * @return the query itself for fluent method calls
     */
    public Query<E> fromPrimary() {
        this.primary = true;
        return this;
    }

    /**
     * Executes the query and returns the first matching entity.
     *
     * @return the first matching entity or <tt>null</tt> if no entity was found
     */
    @Nullable
    public E queryFirst() {
        try {
            if (forceFail) {
                return null;
            }
            SearchRequestBuilder srb = buildSearch();
            if (IndexAccess.LOG.isFINE()) {
                IndexAccess.LOG.FINE("SEARCH-FIRST: %s.%s: %s",
                                     indexAccess.getIndex(clazz),
                                     indexAccess.getDescriptor(clazz).getType(),
                                     buildQuery());
            }
            return transformFirst(srb);
        } catch (Exception e) {
            throw Exceptions.handle(IndexAccess.LOG, e);
        }
    }

    /**
     * Executes the query and returns the first matching entity just like {@link #queryFirst()}.
     *
     * @return the result wrapped in an {@link Optional} for easier handling of <tt>null</tt> values.
     */
    @Nonnull
    public Optional<E> first() {
        return Optional.ofNullable(queryFirst());
    }

    private SearchRequestBuilder buildSearch() {
        EntityDescriptor ed = indexAccess.getDescriptor(clazz);
        SearchRequestBuilder srb = indexAccess.getClient()
                                              .prepareSearch(index != null ?
                                                             index :
                                                             indexAccess.getIndexName(ed.getIndex()))
                                              .setTypes(ed.getType());
        srb.setVersion(true);
        if (primary) {
            srb.setPreference("_primary");
        }

        applyRouting(ed, srb::setRouting);
        applyOrderBys(srb);
        applyFacets(srb);
        applyAggregations(srb);
        applyQueries(srb);
        applyLimit(srb);

        if (logQuery) {
            IndexAccess.LOG.INFO(srb);
        }

        return srb;
    }

    private void applyLimit(SearchRequestBuilder srb) {
        if (start > 0) {
            srb.setFrom(start);
        }
        if (limit != null && limit >= 0) {
            srb.setSize(limit);
        }
    }

    private void applyAggregations(SearchRequestBuilder srb) {
        for (Aggregation aggregation : aggregations) {
            AbstractAggregationBuilder<?> aggregationBuilder = aggregation.getBuilder();

            if (aggregation instanceof BucketAggregation) {
                for (Aggregation subAggregation : ((BucketAggregation) aggregation).getSubAggregations()) {
                    aggregationBuilder.subAggregation(buildSubAggregations(subAggregation));
                }
            }

            srb.addAggregation(aggregationBuilder);
        }
    }

    private AbstractAggregationBuilder<?> buildSubAggregations(Aggregation aggregation) {
        AbstractAggregationBuilder<?> aggregationBuilder = aggregation.getBuilder();

        if (aggregation instanceof BucketAggregation) {
            for (Aggregation subAggregation : ((BucketAggregation) aggregation).getSubAggregations()) {
                aggregationBuilder.subAggregation(buildSubAggregations(subAggregation));
            }
        }

        return aggregationBuilder;
    }

    private void applyFacets(SearchRequestBuilder srb) {
        for (Facet field : termFacets) {
            if (field instanceof DateFacet) {
                DateRangeAggregationBuilder rangeBuilder =
                        AggregationBuilders.dateRange(field.getName()).field(field.getName());
                for (DateRange range : ((DateFacet) field).getRanges()) {
                    range.applyTo(rangeBuilder);
                }
                srb.addAggregation(rangeBuilder);
            } else {
                srb.addAggregation(AggregationBuilders.terms(field.getName())
                                                      .field(field.getName())
                                                      .size(termFacetLimit));
            }
        }
    }

    private void applyOrderBys(SearchRequestBuilder srb) {
        if (randomize) {
            if (randomizeField != null) {
                srb.addSort(SortBuilders.scriptSort(new Script("Math.random()*doc['" + randomizeField + "'].value"),
                                                    ScriptSortBuilder.ScriptSortType.NUMBER).order(SortOrder.DESC));
            } else {
                srb.addSort(SortBuilders.scriptSort(new Script("Math.random()"),
                                                    ScriptSortBuilder.ScriptSortType.NUMBER).order(SortOrder.ASC));
            }
        } else {
            for (Tuple<String, Boolean> sort : orderBys) {
                srb.addSort(sort.getFirst(), sort.getSecond() ? SortOrder.ASC : SortOrder.DESC);
            }
        }
    }

    private void applyQueries(SearchRequestBuilder srb) {
        QueryBuilder qb = buildQuery();

        if (qb != null) {
            if (scoreFunctionBuilder != null) {
                srb.setQuery(new FunctionScoreQueryBuilder(qb, scoreFunctionBuilder));
            } else {
                srb.setQuery(qb);
            }
        }
    }

    /**
     * Executes the query and returns a list of all matching entities.
     * <p>
     * Note that a limit of <tt>999</tt> is enforced, if no limit is given. Use {@link #iterate(ResultHandler)} to
     * process large results.
     *
     * @return the list of matching entities. If no entities match, an empty list will be returned
     */
    @Nonnull
    public List<E> queryList() {
        return queryResultList().getResults();
    }

    /**
     * Executes the query and returns a list of all matching entities as well as all filter facets
     * in form of a {@link ResultList}.
     * <p>
     * Note that a limit of <tt>999</tt> is enforced, if no limit is given. Use {@link #iterate(ResultHandler)} to
     * process large results.
     *
     * @return all matching entities along with the collected facet filters
     */
    @Nonnull
    public ResultList<E> queryResultList() {
        try {
            if (forceFail) {
                return new ResultList<>(new ArrayList<>(), null);
            }
            boolean defaultLimitEnforced = false;
            if (limit == null) {
                limit = DEFAULT_LIMIT;
                defaultLimitEnforced = true;
            }
            SearchRequestBuilder srb = buildSearch();
            if (IndexAccess.LOG.isFINE()) {
                IndexAccess.LOG.FINE("SEARCH: %s.%s: %s",
                                     indexAccess.getIndex(clazz),
                                     indexAccess.getDescriptor(clazz).getType(),
                                     buildQuery());
            }
            ResultList<E> resultList = transform(srb);
            if (defaultLimitEnforced && resultList.size() == DEFAULT_LIMIT) {
                IndexAccess.LOG.WARN("Default limit was hit when using Query.queryList or Query.queryResultList! "
                                     + "Please provide an explicit limit or use Query.iterate to remove this warning. "
                                     + "Query: %s, Location: %s", this, ExecutionPoint.snapshot());
            }
            return resultList;
        } catch (Exception e) {
            throw Exceptions.handle(IndexAccess.LOG, e);
        }
    }

    /**
     * Can be used to return the raw {@link SearchResponse}. This can e.g. be useful in combination with the {@link
     * sirius.search.aggregation.metrics.TopHits} aggregation where the aggregated hits need to be parsed from the
     * aggregation section of the response.
     *
     * @return the raw {@link SearchResponse}
     */
    public SearchResponse queryRaw() {
        if (forceFail) {
            return new SearchResponse();
        }

        boolean defaultLimitEnforced = false;
        if (limit == null) {
            limit = DEFAULT_LIMIT;
            defaultLimitEnforced = true;
        }

        SearchRequestBuilder srb = buildSearch();
        if (IndexAccess.LOG.isFINE()) {
            IndexAccess.LOG.FINE("SEARCH: %s.%s: %s",
                                 indexAccess.getIndex(clazz),
                                 indexAccess.getDescriptor(clazz).getType(),
                                 buildQuery());
        }

        Watch w = Watch.start();
        SearchResponse response = srb.execute().actionGet();

        if (IndexAccess.LOG.isFINE()) {
            IndexAccess.LOG.FINE("SEARCH: %s.%s: SUCCESS: %d - %d ms",
                                 indexAccess.getIndex(clazz),
                                 indexAccess.getDescriptor(clazz).getType(),
                                 response.getHits().totalHits(),
                                 response.getTookInMillis());
        }

        if (defaultLimitEnforced && response.getHits().getHits().length == DEFAULT_LIMIT) {
            IndexAccess.LOG.WARN("Default limit was hit when using Query.queryList or Query.queryResultList! "
                                 + "Please provide an explicit limit or use Query.iterate to remove this warning. "
                                 + "Query: %s, Location: %s", this, ExecutionPoint.snapshot());
        }

        if (Microtiming.isEnabled()) {
            w.submitMicroTiming("ES", "RAW: " + toString(true));
        }

        return response;
    }

    /**
     * Helper method to parse searchHits. Can e.g. be used in combination with {@link
     * sirius.search.aggregation.metrics.TopHits} where the aggregated hits are not part of the query section.
     * <p>
     * {@code
     * query = index.select(...).where(...).addAggregation(...);
     * SearchResponse response = query.queryRaw();
     * for (Terms.Bucket bucket : response.getAggregations().get("...").getBuckets()) {
     * entities.addAll(query.transformTopHits(bucket.getAggregations().get("item")).getHits()));
     * }
     * }
     *
     * @param searchHits the JSON encoded searchHits
     * @return the transformed searchHits
     * @throws ReflectiveOperationException
     */
    public List<E> transformTopHits(SearchHits searchHits) throws ReflectiveOperationException {
        List<E> transformedHits = new ArrayList<>();

        for (SearchHit searchHit : searchHits) {
            EntityDescriptor descriptor = indexAccess.getDescriptor(clazz);
            E entity = clazz.newInstance();
            entity.initSourceTracing();
            entity.setId(searchHit.getId());
            entity.setVersion(searchHit.getVersion());
            descriptor.readSource(entity, searchHit.getSource());
            transformedHits.add(entity);
        }

        return transformedHits;
    }

    /**
     * Executes the query and counts the number of matching entities.
     *
     * @return the number of matching entities
     */
    public long count() {
        try {
            if (forceFail) {
                return 0;
            }

            EntityDescriptor ed = indexAccess.getDescriptor(clazz);
            SearchRequestBuilder crb = indexAccess.getClient()
                                                  .prepareSearch(index != null ? index : indexAccess.getIndex(clazz))
                                                  .setTypes(ed.getType());
            crb.setSize(0);
            applyRouting(ed, crb::setRouting);
            QueryBuilder qb = buildQuery();
            if (qb != null) {
                crb.setQuery(qb);
            }
            if (IndexAccess.LOG.isFINE()) {
                IndexAccess.LOG.FINE("COUNT: %s.%s: %s", indexAccess.getIndex(clazz), ed.getType(), buildQuery());
            }
            return transformCount(crb);
        } catch (Exception t) {
            throw Exceptions.handle(IndexAccess.LOG, t);
        }
    }

    private void applyRouting(EntityDescriptor ed, Consumer<String> routingTarget) {
        if (Strings.isFilled(routing)) {
            if (!ed.hasRouting()) {
                Exceptions.handle()
                          .to(IndexAccess.LOG)
                          .withSystemErrorMessage("Performing a query on %s with a routing "
                                                  + "- but entity has no routing attribute (in @Indexed)! "
                                                  + "This will most probably FAIL! Query: %s",
                                                  clazz.getName(),
                                                  this.toString())
                          .handle();
            }
            routingTarget.accept(routing);
        } else if (ed.hasRouting() && !deliberatelyUnrouted) {
            Exceptions.handle()
                      .to(IndexAccess.LOG)
                      .withSystemErrorMessage("Performing a query on %s without providing a routing. "
                                              + "Consider providing a routing for better performance "
                                              + "or call deliberatelyUnrouted() to signal that routing "
                                              + "was intentionally skipped. Query: %s",
                                              clazz.getName(),
                                              this.toString())
                      .handle();
        }
    }

    /**
     * Executes the query and checks if at least one entity matches.
     *
     * @return <tt>true</tt> if at least one entity is matched by the query, <tt>false</tt> otherwise
     */
    public boolean exists() {
        return count() > 0;
    }

    private QueryBuilder buildQuery() {
        List<QueryBuilder> queries = new ArrayList<>();
        for (Constraint constraint : constraints) {
            QueryBuilder qb = constraint.createQuery();
            if (qb != null) {
                queries.add(qb);
            }
        }
        if (queries.isEmpty()) {
            return null;
        } else if (queries.size() == 1) {
            return queries.get(0);
        } else {
            BoolQueryBuilder result = QueryBuilders.boolQuery();
            for (QueryBuilder qb : queries) {
                result.must(qb);
            }
            return result;
        }
    }

    /**
     * Internal execution of the query along with the transformation of the result for a list
     *
     * @param builder the completed query
     * @return the result of the query
     * @throws Exception in case on an error when executing the query
     */
    protected ResultList<E> transform(SearchRequestBuilder builder) throws Exception {
        Watch w = Watch.start();
        SearchResponse searchResponse = builder.execute().actionGet();
        ResultList<E> result = new ResultList<>(termFacets, searchResponse);
        EntityDescriptor descriptor = indexAccess.getDescriptor(clazz);
        for (SearchHit hit : searchResponse.getHits()) {
            E entity = clazz.newInstance();
            entity.initSourceTracing();
            entity.setId(hit.getId());
            entity.setVersion(hit.getVersion());
            descriptor.readSource(entity, hit.getSource());
            result.getResults().add(entity);
        }
        if (IndexAccess.LOG.isFINE()) {
            IndexAccess.LOG.FINE("SEARCH: %s.%s: SUCCESS: %d - %d ms",
                                 indexAccess.getIndex(clazz),
                                 indexAccess.getDescriptor(clazz).getType(),
                                 searchResponse.getHits().totalHits(),
                                 searchResponse.getTookInMillis());
        }
        if (Microtiming.isEnabled()) {
            w.submitMicroTiming("ES", "LIST: " + toString(true));
        }
        return result;
    }

    /**
     * Internal execution of the query along with the transformation of the result for a single entity
     *
     * @param builder the completed query
     * @return the result of the query
     * @throws Exception in case on an error when executing the query
     */
    protected E transformFirst(SearchRequestBuilder builder) throws Exception {
        Watch w = Watch.start();
        SearchResponse searchResponse = builder.execute().actionGet();
        E result = null;
        if (searchResponse.getHits().hits().length > 0) {
            SearchHit hit = searchResponse.getHits().hits()[0];
            result = clazz.newInstance();
            result.initSourceTracing();
            result.setId(hit.getId());
            result.setVersion(hit.getVersion());
            indexAccess.getDescriptor(clazz).readSource(result, hit.getSource());
        }
        if (IndexAccess.LOG.isFINE()) {
            IndexAccess.LOG.FINE("SEARCH-FIRST: %s.%s: SUCCESS: %d - %d ms",
                                 indexAccess.getIndex(clazz),
                                 indexAccess.getDescriptor(clazz).getType(),
                                 searchResponse.getHits().totalHits(),
                                 searchResponse.getTookInMillis());
        }
        if (Microtiming.isEnabled()) {
            w.submitMicroTiming("ES", "FIRST: " + toString(true));
        }
        return result;
    }

    /**
     * Internal execution of the query along with the transformation of the result for a count of entities
     *
     * @param builder the completed query
     * @return the result of the query
     */
    protected long transformCount(SearchRequestBuilder builder) {
        Watch w = Watch.start();
        SearchResponse res = builder.execute().actionGet();
        if (IndexAccess.LOG.isFINE()) {
            IndexAccess.LOG.FINE("COUNT: %s.%s: SUCCESS: %d",
                                 indexAccess.getIndex(clazz),
                                 indexAccess.getDescriptor(clazz).getType(),
                                 res.getHits().getTotalHits());
        }
        if (Microtiming.isEnabled()) {
            w.submitMicroTiming("ES", "COUNT: " + toString(true));
        }
        return res.getHits().getTotalHits();
    }

    /**
     * Executes the query and returns the resulting items as a {@link sirius.web.controller.Page}.
     *
     * @return the result of the query along with all facets and paging-metadata
     */
    @SuppressWarnings("unchecked")
    public Page<E> queryPage() {
        if (limit == null) {
            throw new IllegalStateException("limit must be set when using queryPage (Call .page(...)!)");
        }
        int originalLimit = limit;
        limit++;
        Watch w = Watch.start();
        ResultList<E> result = new ResultList<>(termFacets, null);
        if (!forceFail) {
            try {
                result = queryResultList();
            } catch (Exception e) {
                UserContext.handle(e);
            }
        }
        boolean hasMore = false;
        if (result.size() > originalLimit) {
            hasMore = true;
            result.getResults().remove(result.size() - 1);
        }
        final ResultList<E> finalResult = result;
        return new Page<E>().withQuery(queryString)
                            .withStart(start + 1)
                            .withItems(result.getResults())
                            .withFactesSupplier(finalResult::getFacets)
                            .withHasMore(hasMore)
                            .withDuration(w.duration())
                            .withPageSize(pageSize);
    }

    /**
     * Sets the max. duration for a scroll request kept open by the elasticsearch cluster.
     * <p>
     * {@link #iterate(ResultHandler)} and {@link #iterateAll(Consumer)} internally use scroll requests. These
     * required to specify a timeout so that the elasticsearch cluster knows how long to keep the resources. By
     * Default the timeout is 5 minutes. If you processing loop is very slow you can extend the timeout using
     * this method.
     *
     * @param scrollTimeoutInSeconds the timeout for a scroll request (time required to process one batch of entities)
     *                               in seconds
     * @return the query itself for fluent method calls
     */
    public Query<E> withCustomScrollTTL(int scrollTimeoutInSeconds) {
        this.scrollTTL = scrollTimeoutInSeconds;
        return this;
    }

    /**
     * Executes the result and calls the given <tt>handler</tt> for each item in the result.
     * <p>
     * This is intended to be used to process large result sets as these are automatically scrolls through.
     *
     * @param handler the handler used to process each result item
     */

    public void iterate(ResultHandler<? super E> handler) {
        try {
            if (forceFail) {
                return;
            }
            EntityDescriptor entityDescriptor = indexAccess.getDescriptor(clazz);
            SearchResponse searchResponse = createScroll(entityDescriptor);
            try {
                executeScroll(searchResponse, handler, entityDescriptor);
            } finally {
                clearScroll(searchResponse);
            }
        } catch (Exception t) {
            throw Exceptions.handle(IndexAccess.LOG, t);
        }
    }

    private void executeScroll(SearchResponse initialSearchResponse,
                               ResultHandler<? super E> handler,
                               EntityDescriptor entityDescriptor) {
        SearchResponse searchResponse = initialSearchResponse;
        TaskContext ctx = TaskContext.get();
        RateLimit rateLimit = RateLimit.timeInterval(1, TimeUnit.SECONDS);
        long lastScroll = 0;
        Limit lim = new Limit(start, limit);

        while (true) {
            lastScroll = performScrollMonitoring(lastScroll);

            for (SearchHit hit : searchResponse.getHits()) {
                if (!processHit(handler, entityDescriptor, ctx, rateLimit, lim, hit)) {
                    return;
                }
            }
            if (searchResponse.getHits().hits().length == 0) {
                return;
            }
            searchResponse = scrollFurther(entityDescriptor, searchResponse.getScrollId());
        }
    }

    private boolean processHit(ResultHandler<? super E> handler,
                               EntityDescriptor entityDescriptor,
                               TaskContext ctx,
                               RateLimit rateLimit,
                               Limit lim,
                               SearchHit hit) {
        try {
            E entity = clazz.newInstance();
            entity.setId(hit.getId());
            entity.initSourceTracing();
            entity.setVersion(hit.getVersion());
            entityDescriptor.readSource(entity, hit.getSource());

            if (lim.nextRow()) {
                if (!handler.handleRow(entity)) {
                    return false;
                }
                if (!lim.shouldContinue()) {
                    return false;
                }
            }
            if (rateLimit.check() && !ctx.isActive()) {
                return false;
            }
        } catch (Exception e) {
            Exceptions.handle().to(IndexAccess.LOG).error(e).handle();
        }

        return true;
    }

    private void clearScroll(SearchResponse searchResponse) {
        try {
            indexAccess.getClient()
                       .prepareClearScroll()
                       .addScrollId(searchResponse.getScrollId())
                       .execute()
                       .actionGet();
        } catch (Exception e) {
            Exceptions.handle(IndexAccess.LOG, e);
        }
    }

    private long performScrollMonitoring(long lastScroll) {
        long now = System.currentTimeMillis();
        if (lastScroll > 0) {
            long deltaInSeconds = TimeUnit.SECONDS.convert(now - lastScroll, TimeUnit.MILLISECONDS);
            // Warn if processing of one scroll took longer thant our keep alive....
            if (deltaInSeconds > scrollTTL) {
                Exceptions.handle()
                          .withSystemErrorMessage(
                                  "A scroll query against elasticserach took too long to process its data! "
                                  + "The result is probably inconsistent! Query: %s",
                                  this)
                          .to(IndexAccess.LOG)
                          .handle();
            }
        }
        return now;
    }

    private SearchResponse scrollFurther(EntityDescriptor entityDescriptor, String scrollId) {
        SearchResponse searchResponse = indexAccess.getClient()
                                                   .prepareSearchScroll(scrollId)
                                                   .setScroll(org.elasticsearch.common.unit.TimeValue.timeValueSeconds(
                                                           scrollTTL))
                                                   .execute()
                                                   .actionGet();
        if (IndexAccess.LOG.isFINE()) {
            IndexAccess.LOG.FINE("SEARCH-SCROLL: %s.%s: SUCCESS: %d/%d - %d ms",
                                 indexAccess.getIndex(clazz),
                                 entityDescriptor.getType(),
                                 searchResponse.getHits().hits().length,
                                 searchResponse.getHits().totalHits(),
                                 searchResponse.getTookInMillis());
        }
        return searchResponse;
    }

    private SearchResponse createScroll(EntityDescriptor entityDescriptor) {
        SearchRequestBuilder srb = buildSearch();
        if (!orderBys.isEmpty()) {
            IndexAccess.LOG.WARN("An iterated query cannot be sorted! Use '.blockwise(...)'. Query: %s, Location: %s",
                                 this,
                                 ExecutionPoint.snapshot());
        }

        srb.addSort("_doc", SortOrder.ASC);
        srb.setFrom(0);

        // If a routing is present, we will only hit one shard. Therefore we fetch up to 50 documents.
        // Otherwise we limit to 10 documents per shard...
        srb.setSize(routing != null ? MAX_SCROLL_RESULTS_FOR_SINGLE_SHARD : MAX_SCROLL_RESULTS_PER_SHARD);
        srb.setScroll(org.elasticsearch.common.unit.TimeValue.timeValueSeconds(scrollTTL));
        if (IndexAccess.LOG.isFINE()) {
            IndexAccess.LOG.FINE("ITERATE: %s.%s: %s",
                                 indexAccess.getIndex(clazz),
                                 entityDescriptor.getType(),
                                 buildQuery());
        }

        return srb.execute().actionGet();
    }

    /**
     * Executes the result and calls the given <tt>customer</tt> for each item in the result.
     * <p>
     * This is intended to be used to process large result sets as these are automatically scrolls through.
     * <p>
     * In contrast to {@link #iterate(ResultHandler)} this will consume all results and cannot be interrupted.
     *
     * @param consumer the handler used to process each result item
     */
    public void iterateAll(Consumer<? super E> consumer) {
        iterate(c -> {
            consumer.accept(c);
            return true;
        });
    }

    /**
     * Executes the result and calls the given <tt>customer</tt> for each item in the result.
     * <p>
     * This is intended to be used to process large result sets as only a block of items is fetched at a time.
     * <p>
     * In contrast to {@link #iterate(ResultHandler)} and {@link #iterateAll(Consumer)} this does not use
     * scroll queries but a sequence of plain queries with appropriate limit and from settings. The benefit
     * of this method is, that the result can be sorted. The downside is that intermediate inserts or deletes might
     * corrupt the result of this query as entities might occur twice or be missing at all. Although a deduplicator
     * is installed and should filter out such cases, the results still shouldn't be used for sensible calculations.
     *
     * @param consumer the handler used to process each result item
     */
    public void blockwise(Function<? super E, Boolean> consumer) {
        try {
            if (forceFail) {
                return;
            }
            Limit lim = new Limit(0, limit);
            TaskContext ctx = TaskContext.get();
            RateLimit rateLimit = RateLimit.timeInterval(1, TimeUnit.SECONDS);

            // An intermediate insert between two queries might result in an entity being reported twice
            // (by two queries, we is this set to deduplicate entities if necesseary).
            Set<String> entityDeDuplicator = Sets.newTreeSet();

            // Overwrite limit to support paging
            // This limit is quite large to process as much items as possible at once as the query is
            // expensive on its own.
            limit = 512;
            while (true) {
                if (!processBlock(consumer, lim, ctx, rateLimit, entityDeDuplicator)) {
                    return;
                }
            }
        } catch (Exception e) {
            throw Exceptions.handle(IndexAccess.LOG, e);
        }
    }

    private boolean processBlock(Function<? super E, Boolean> consumer,
                                 Limit lim,
                                 TaskContext ctx,
                                 RateLimit rateLimit,
                                 Set<String> entityDeDuplicator) throws Exception {
        SearchRequestBuilder srb = buildSearch();
        if (IndexAccess.LOG.isFINE()) {
            IndexAccess.LOG.FINE("PAGED-SEARCH: %s.%s: %s",
                                 indexAccess.getIndex(clazz),
                                 indexAccess.getDescriptor(clazz).getType(),
                                 buildQuery());
        }

        ResultList<E> resultList = transform(srb);
        for (E entity : resultList) {
            try {
                if (!processEntity(consumer, lim, ctx, rateLimit, entityDeDuplicator, entity)) {
                    return false;
                }
            } catch (Exception e) {
                Exceptions.handle().to(IndexAccess.LOG).error(e).handle();
            }
        }

        // Re-create entity the duplicator
        entityDeDuplicator.clear();
        resultList.getResults().stream().map(Entity::getId).collect(Lambdas.into(entityDeDuplicator));
        start += resultList.size();

        return resultList.size() >= limit;
    }

    private boolean processEntity(Function<? super E, Boolean> consumer,
                                  Limit lim,
                                  TaskContext ctx,
                                  RateLimit rateLimit,
                                  Set<String> entityDeDuplicator,
                                  E entity) {
        if (!entityDeDuplicator.contains(entity.getId())) {
            if (lim.nextRow()) {
                if (!consumer.apply(entity)) {
                    return false;
                }
                if (!lim.shouldContinue()) {
                    return false;
                }
            }
            if (rateLimit.check()) {
                // Check is the user tries to cancel this task
                if (!ctx.isActive()) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Executes the result and calls the given <tt>customer</tt> for each item in the result.
     * <p>
     * This is a boilerplate handler for {@link #blockwise(Function)} with a function which constantly returns
     * <tt>true</tt> .
     *
     * @param consumer the handler used to process each result item
     */
    public void blockwiseAll(Consumer<? super E> consumer) {
        blockwise(e -> {
            consumer.accept(e);
            return true;
        });
    }

    /**
     * Computes a string representation of the given query.
     *
     * @param skipConstraintValues determines if filter values should be added or be replaced with placeholders.
     * @return a string representation of the this query
     */
    public String toString(boolean skipConstraintValues) {
        StringBuilder sb = new StringBuilder("SELECT ");
        sb.append(clazz.getName());
        outputConstraints(skipConstraintValues, sb);
        outputOrder(sb);
        outputLimit(skipConstraintValues, sb);
        return sb.toString();
    }

    private void outputLimit(boolean skipConstraintValues, StringBuilder sb) {
        if (start > 0 || (limit != null && limit > 0)) {
            sb.append(" LIMIT ");
            sb.append(skipConstraintValues ? "?" : start);
            sb.append(", ");
            sb.append(skipConstraintValues ? "?" : limit);
        }
    }

    private void outputOrder(StringBuilder sb) {
        if (randomize) {
            sb.append(" RANDOMIZED");
        } else if (!orderBys.isEmpty()) {
            sb.append(" ORDER BY");
            for (Tuple<String, Boolean> orderBy : orderBys) {
                sb.append(" ");
                sb.append(orderBy.getFirst());
                sb.append(orderBy.getSecond() ? " ASC" : " DESC");
            }
        }
    }

    private void outputConstraints(boolean skipConstraintValues, StringBuilder sb) {
        if (constraints.isEmpty()) {
            return;
        }

        sb.append(" WHERE ");
        Monoflop mf = Monoflop.create();
        for (Constraint constraint : constraints) {
            if (mf.successiveCall()) {
                sb.append(" AND ");
            }
            sb.append(constraint.toString(skipConstraintValues));
        }
    }

    @Override
    public String toString() {
        return toString(false);
    }

    /**
     * Deletes all entities which are matched by this query.
     */
    public void delete() {
        try {
            if (forceFail) {
                return;
            }
            Watch w = Watch.start();
            deleteByIteration();
            if (Microtiming.isEnabled()) {
                w.submitMicroTiming("ES", "DELETE: " + toString(true));
            }
        } catch (Exception e) {
            throw Exceptions.handle(IndexAccess.LOG, e);
        }
    }

    protected void deleteByIteration() throws Exception {
        ValueHolder<Exception> error = ValueHolder.of(null);
        iterate(e -> {
            try {
                indexAccess.delete(e);
            } catch (Exception ex) {
                error.set(ex);
            }
            return true;
        });
        if (error.get() != null) {
            throw error.get();
        }
    }
}
