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
import org.elasticsearch.action.count.CountRequestBuilder;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.deletebyquery.DeleteByQueryRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
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
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Microtiming;
import sirius.kernel.nls.NLS;
import sirius.search.constraints.Constraint;
import sirius.search.constraints.FieldEqual;
import sirius.search.constraints.FieldNotEqual;
import sirius.search.constraints.Filled;
import sirius.search.constraints.Or;
import sirius.search.constraints.QueryString;
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
 * Represents a query against the database which are created via {@link Index#select(Class)}.
 *
 * @param <E> the type of entities being queried
 */
public class Query<E extends Entity> {

    private static final int DEFAULT_LIMIT = 999;
    private static final int SCROLL_TTL_SECONDS = 60 * 5;
    private static final int MAX_SCROLL_RESULTS_FOR_SINGLE_SHARD = 50;
    private static final int MAX_SCROLL_RESULTS_PER_SHARD = 10;

    /**
     * Specifies tbe default field to search in used by {@link #query(String)}. Use
     * {@link #query(String, String, Function, boolean)} to specify a custom field.
     */
    private static final String DEFAULT_FIELD = "_all";

    @ConfigValue("index.termFacetLimit")
    private static int termFacetLimit;

    private Class<E> clazz;
    private List<Constraint> constraints = Lists.newArrayList();
    private List<Tuple<String, Boolean>> orderBys = Lists.newArrayList();
    private List<Facet> termFacets = Lists.newArrayList();
    private boolean randomize;
    private String randomizeField;
    private int start;
    private Integer limit = null;
    private String query;
    private int pageSize = 25;
    private boolean primary = false;
    private String index;
    private boolean forceFail = false;
    private String routing;
    // Used to signal that deliberately no routing was given
    private boolean deliberatelyUnrouted;
    private int scrollTTL = SCROLL_TTL_SECONDS;

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
     * Adds a textual query using the given field.
     * <p>
     * It will try to parse queries in Lucene syntax (+token, -token, AND, OR and brackets are supported) but it
     * will never fail for a malformed query. If such a case, the valid tokens are sent to the server.
     *
     * @param query        the query to search for
     * @param defaultField the default field to search in
     * @param tokenizer    the function to use for tokenization
     * @param autoexpand   determines if for single term queries an expansion (like term*) should be added
     * @return the query itself for fluent method calls
     */
    public Query<E> query(String query,
                          String defaultField,
                          Function<String, Iterable<List<String>>> tokenizer,
                          boolean autoexpand) {
        if (Strings.isFilled(query)) {
            this.query = query;
            RobustQueryParser rqp = new RobustQueryParser(defaultField, query, tokenizer, autoexpand);
            rqp.compileAndApply(this);
        }

        return this;
    }

    /**
     * Adds a textual query using the given field. If the given query is too short, no results will be
     * generated.
     * <p>
     * If a non-empty query string which contains at least two characters (without "*") is given, this will behave just
     * like {@link #query(String, String, Function, boolean)}. Otherwise the completed query will be failed by calling
     * {@link
     * #fail()} to ensure that no results are generated.
     *
     * @param query        the query to search for
     * @param defaultField the default field to search in
     * @param tokenizer    the function to use for tokenization
     * @return the query itself for fluent method calls
     */
    public Query<E> forceQuery(String query, String defaultField, Function<String, Iterable<List<String>>> tokenizer) {
        String effectiveQuery = (query == null) ? "" : query.replace("*", "").trim();
        if (effectiveQuery.length() < 3) {
            fail();
            return this;
        }
        this.query = query;
        RobustQueryParser rqp = new RobustQueryParser(defaultField, query, tokenizer, false);
        rqp.compileAndApply(this);

        return this;
    }

    /**
     * Adds a textual query across all searchable fields.
     * <p>
     * Uses the DEFAULT_FIELD and DEFAULT_ANALYZER while calling {@link #query(String, String,
     * java.util.function.Function, boolean)}.
     *
     * @param query the query to search for
     * @return the query itself for fluent method calls
     */
    public Query<E> query(String query) {
        return query(query, DEFAULT_FIELD, this::defaultTokenizer, false);
    }

    /**
     * Adds a textual query across all searchable fields.
     * <p>
     * If a single term query is given, an expansion like "term*" will be added.
     * <p>
     * Uses the DEFAULT_FIELD and DEFAULT_ANALYZER while calling {@link #query(String, String,
     * java.util.function.Function, boolean)}.
     *
     * @param query the query to search for
     * @return the query itself for fluent method calls
     */
    public Query<E> expandedQuery(String query) {
        return query(query, DEFAULT_FIELD, this::defaultTokenizer, true);
    }

    /**
     * Uses the StandardAnalyzer to perform tokenization.
     *
     * @param input the value to tokenize
     * @return a list of token based on the given input
     */
    private Iterable<List<String>> defaultTokenizer(String input) {
        List<List<String>> result = Lists.newArrayList();
        StandardAnalyzer std = new StandardAnalyzer();
        try {
            TokenStream stream = std.tokenStream("std", input);
            stream.reset();
            while (stream.incrementToken()) {
                CharTermAttribute attr = stream.getAttribute(CharTermAttribute.class);
                String token = new String(attr.buffer(), 0, attr.length());
                result.add(Collections.singletonList(token));
            }
        } catch (IOException e) {
            Exceptions.handle(Index.LOG, e);
        }

        return result;
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
        this.index = Index.getIndexName(indexToUse);
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
    protected Query<?> autoRoute(String field, String value) {
        EntityDescriptor descriptor = Index.getDescriptor(clazz);
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
     * Adds a term facet to be filled by the query.
     *
     * @param field      the field to scan
     * @param value      the value to filter by
     * @param translator the translator used to turn field values into visible filter values
     * @return the query itself for fluent method calls
     */
    public Query<E> addTermFacet(String field, String value, ValueComputer<String, String> translator) {
        final Property p = Index.getDescriptor(clazz).getProperty(field);
        if (p instanceof EnumProperty && translator == null) {
            translator = (v) -> String.valueOf(((EnumProperty) p).transformFromSource(v));
        }
        termFacets.add(new Facet(NLS.get(p.getField().getDeclaringClass().getSimpleName() + "." + field),
                                 field,
                                 value,
                                 translator));
        if (Strings.isFilled(value)) {
            where(FieldEqual.on(field, value).asFilter());
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
        if (limit < 1 || limit > maxLimit) {
            limit = maxLimit;
        }
        return start(start).limit(limit);
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
            if (Index.LOG.isFINE()) {
                Index.LOG.FINE("SEARCH-FIRST: %s.%s: %s",
                               Index.getIndex(clazz),
                               Index.getDescriptor(clazz).getType(),
                               buildQuery());
            }
            return transformFirst(srb);
        } catch (Throwable e) {
            throw Exceptions.handle(Index.LOG, e);
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
        EntityDescriptor ed = Index.getDescriptor(clazz);
        SearchRequestBuilder srb = Index.getClient()
                                        .prepareSearch(index != null ? index : Index.getIndexName(ed.getIndex()))
                                        .setTypes(ed.getType());
        srb.setVersion(true);
        if (primary) {
            srb.setPreference("_primary");
        }

        applyRouting(ed, srb);
        applyOrderBys(srb);
        applyFacets(srb);
        applyQueriesAndFilters(srb);
        applyLimit(srb);

        return srb;
    }

    private void applyLimit(SearchRequestBuilder srb) {
        if (start > 0) {
            srb.setFrom(start);
        }
        if (limit != null && limit > 0) {
            srb.setSize(limit);
        }
    }

    private void applyFacets(SearchRequestBuilder srb) {
        for (Facet field : termFacets) {
            srb.addAggregation(AggregationBuilders.terms(field.getName()).field(field.getName()).size(termFacetLimit));
        }
    }

    private void applyOrderBys(SearchRequestBuilder srb) {
        if (randomize) {
            if (randomizeField != null) {
                srb.addSort(SortBuilders.scriptSort("Math.random()*doc['" + randomizeField + "'].value", "number")
                                        .order(SortOrder.DESC));
            } else {
                srb.addSort(SortBuilders.scriptSort("Math.random()", "number").order(SortOrder.ASC));
            }
        } else {
            for (Tuple<String, Boolean> sort : orderBys) {
                srb.addSort(sort.getFirst(), sort.getSecond() ? SortOrder.ASC : SortOrder.DESC);
            }
        }
    }

    private void applyQueriesAndFilters(SearchRequestBuilder srb) {
        QueryBuilder qb = buildQuery();
        if (qb != null) {
            srb.setQuery(qb);
        }
        FilterBuilder sb = buildFilter();
        if (sb != null) {
            if (qb == null && termFacets.isEmpty()) {
                srb.setPostFilter(sb);
            } else {
                srb.setQuery(QueryBuilders.filteredQuery(qb == null ? QueryBuilders.matchAllQuery() : qb, sb));
            }
        }
    }

    private void applyRouting(EntityDescriptor ed, SearchRequestBuilder srb) {
        if (Strings.isFilled(routing)) {
            if (!ed.hasRouting()) {
                Exceptions.handle()
                          .to(Index.LOG)
                          .withSystemErrorMessage("Performing a search on %s with a routing "
                                                  + "- but entity has no routing attribute (in @Indexed)! "
                                                  + "This will most probably FAIL!", clazz.getName())
                          .handle();
            }
            srb.setRouting(routing);
        } else if (ed.hasRouting() && !deliberatelyUnrouted) {
            Exceptions.handle()
                      .to(Index.LOG)
                      .withSystemErrorMessage("Performing a search on %s without providing a routing. "
                                              + "Consider providing a routing for better performance "
                                              + "or call deliberatelyUnrouted() to signal that routing was "
                                              + "intentionally skipped.", clazz.getName())
                      .handle();
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
                return new ResultList<>(Lists.newArrayList(), null);
            }
            boolean defaultLimitEnforced = false;
            if (limit == null) {
                limit = DEFAULT_LIMIT;
                defaultLimitEnforced = true;
            }
            SearchRequestBuilder srb = buildSearch();
            if (Index.LOG.isFINE()) {
                Index.LOG.FINE("SEARCH: %s.%s: %s",
                               Index.getIndex(clazz),
                               Index.getDescriptor(clazz).getType(),
                               buildQuery());
            }
            ResultList<E> resultList = transform(srb);
            if (defaultLimitEnforced && resultList.size() == DEFAULT_LIMIT) {
                Index.LOG.WARN("Default limit was hit when using Query.queryList or Query.queryResultList! "
                               + "Please provide an explicit limit or use Query.iterate to remove this warning. "
                               + "Query: %s, Location: %s", this, ExecutionPoint.snapshot());
            }
            return resultList;
        } catch (Throwable e) {
            throw Exceptions.handle(Index.LOG, e);
        }
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
            EntityDescriptor ed = Index.getDescriptor(clazz);
            CountRequestBuilder crb = Index.getClient()
                                           .prepareCount(index != null ? index : Index.getIndex(clazz))
                                           .setTypes(ed.getType());
            if (Strings.isFilled(routing)) {
                if (!ed.hasRouting()) {
                    Exceptions.handle()
                              .to(Index.LOG)
                              .withSystemErrorMessage("Performing a search on %s with a routing "
                                                      + "- but entity has no routing attribute (in @Indexed)! "
                                                      + "This will most probably FAIL!", clazz.getName())
                              .handle();
                }
                crb.setRouting(routing);
            } else if (ed.hasRouting() && !deliberatelyUnrouted) {
                Exceptions.handle()
                          .to(Index.LOG)
                          .withSystemErrorMessage("Performing a search on %s without providing a routing. "
                                                  + "Consider providing a routing for better performance "
                                                  + "or call deliberatelyUnrouted() to signal that routing "
                                                  + "was intentionally skipped.", clazz.getName())
                          .handle();
            }
            QueryBuilder qb = buildQuery();
            if (qb != null) {
                crb.setQuery(qb);
            }
            FilterBuilder sb = buildFilter();
            if (sb != null) {
                crb.setQuery(QueryBuilders.filteredQuery(qb, sb));
            }
            if (Index.LOG.isFINE()) {
                Index.LOG.FINE("COUNT: %s.%s: %s", Index.getIndex(clazz), ed.getType(), buildQuery());
            }
            return transformCount(crb);
        } catch (Throwable t) {
            throw Exceptions.handle(Index.LOG, t);
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
        List<QueryBuilder> queries = new ArrayList<QueryBuilder>();
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

    private FilterBuilder buildFilter() {
        List<FilterBuilder> filters = new ArrayList<FilterBuilder>();
        for (Constraint constraint : constraints) {
            FilterBuilder sb = constraint.createFilter();
            if (sb != null) {
                filters.add(sb);
            }
        }
        if (filters.isEmpty()) {
            return null;
        } else if (filters.size() == 1) {
            return filters.get(0);
        } else {
            BoolFilterBuilder result = FilterBuilders.boolFilter();
            for (FilterBuilder qb : filters) {
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
        EntityDescriptor descriptor = Index.getDescriptor(clazz);
        for (SearchHit hit : searchResponse.getHits()) {
            E entity = clazz.newInstance();
            entity.initSourceTracing();
            entity.setId(hit.getId());
            entity.setVersion(hit.getVersion());
            descriptor.readSource(entity, hit.getSource());
            result.getResults().add(entity);
        }
        if (Index.LOG.isFINE()) {
            Index.LOG.FINE("SEARCH: %s.%s: SUCCESS: %d - %d ms",
                           Index.getIndex(clazz),
                           Index.getDescriptor(clazz).getType(),
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
            Index.getDescriptor(clazz).readSource(result, hit.getSource());
        }
        if (Index.LOG.isFINE()) {
            Index.LOG.FINE("SEARCH-FIRST: %s.%s: SUCCESS: %d - %d ms",
                           Index.getIndex(clazz),
                           Index.getDescriptor(clazz).getType(),
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
    protected long transformCount(CountRequestBuilder builder) {
        Watch w = Watch.start();
        CountResponse res = builder.execute().actionGet();
        if (Index.LOG.isFINE()) {
            Index.LOG.FINE("COUNT: %s.%s: SUCCESS: %d",
                           Index.getIndex(clazz),
                           Index.getDescriptor(clazz).getType(),
                           res.getCount());
        }
        if (Microtiming.isEnabled()) {
            w.submitMicroTiming("ES", "COUNT: " + toString(true));
        }
        return res.getCount();
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
        return new Page<E>().withQuery(query)
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
            EntityDescriptor entityDescriptor = Index.getDescriptor(clazz);
            SearchResponse searchResponse = createScroll(entityDescriptor);
            try {
                TaskContext ctx = TaskContext.get();
                RateLimit rateLimit = RateLimit.timeInterval(1, TimeUnit.SECONDS);
                long lastScroll = 0;
                Limit lim = new Limit(start, limit);

                while (true) {
                    searchResponse = scrollFurther(entityDescriptor, searchResponse);
                    lastScroll = performScrollMonitoring(lastScroll);

                    for (SearchHit hit : searchResponse.getHits()) {
                        E entity = clazz.newInstance();
                        entity.setId(hit.getId());
                        entity.initSourceTracing();
                        entity.setVersion(hit.getVersion());
                        entityDescriptor.readSource(entity, hit.getSource());

                        try {
                            if (lim.nextRow()) {
                                if (!handler.handleRow(entity)) {
                                    return;
                                }
                                if (!lim.shouldContinue()) {
                                    return;
                                }
                            }
                            if (rateLimit.check()) {
                                // Check is the user tries to cancel this task
                                if (!ctx.isActive()) {
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            Exceptions.handle().to(Index.LOG).error(e).handle();
                        }
                    }
                    if (searchResponse.getHits().hits().length == 0) {
                        return;
                    }
                }
            } finally {
                clearScroll(searchResponse);
            }
        } catch (Throwable t) {
            throw Exceptions.handle(Index.LOG, t);
        }
    }

    private void clearScroll(SearchResponse searchResponse) {
        try {
            Index.getClient().prepareClearScroll().addScrollId(searchResponse.getScrollId()).execute().actionGet();
        } catch (Throwable e) {
            Exceptions.handle(Index.LOG, e);
        }
    }

    private long performScrollMonitoring(long lastScroll) {
        long now = System.currentTimeMillis();
        if (lastScroll > 0) {
            // Warn if processing of one scroll took longer thant our keep alive....
            if (TimeUnit.SECONDS.convert(now - lastScroll, TimeUnit.MILLISECONDS) > scrollTTL) {
                Exceptions.handle()
                          .withSystemErrorMessage(
                                  "A scroll query against elasticserach took too long to process its data! "
                                  + "The result is probably inconsistent! Query: %s",
                                  this)
                          .to(Index.LOG)
                          .handle();
            }
        }
        return now;
    }

    private SearchResponse scrollFurther(EntityDescriptor entityDescriptor, SearchResponse searchResponse) {
        searchResponse = Index.getClient()
                              .prepareSearchScroll(searchResponse.getScrollId())
                              .setScroll(org.elasticsearch.common.unit.TimeValue.timeValueSeconds(scrollTTL))
                              .execute()
                              .actionGet();
        if (Index.LOG.isFINE()) {
            Index.LOG.FINE("SEARCH-SCROLL: %s.%s: SUCCESS: %d/%d - %d ms",
                           Index.getIndex(clazz),
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
            Index.LOG.WARN("An iterated query cannot be sorted! Use '.blockwise(...)'. Query: %s, Location: %s",
                           this,
                           ExecutionPoint.snapshot());
        }
        srb.setSearchType(SearchType.SCAN);
        srb.setFrom(0);
        // If a routing is present, we will only hit one shard. Therefore we fetch up to 50 documents.
        // Otherwise we limit to 10 documents per shard...
        srb.setSize(routing != null ? MAX_SCROLL_RESULTS_FOR_SINGLE_SHARD : MAX_SCROLL_RESULTS_PER_SHARD);
        srb.setScroll(org.elasticsearch.common.unit.TimeValue.timeValueSeconds(scrollTTL));
        if (Index.LOG.isFINE()) {
            Index.LOG.FINE("ITERATE: %s.%s: %s", Index.getIndex(clazz), entityDescriptor.getType(), buildQuery());
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
                SearchRequestBuilder srb = buildSearch();
                if (Index.LOG.isFINE()) {
                    Index.LOG.FINE("PAGED-SEARCH: %s.%s: %s",
                                   Index.getIndex(clazz),
                                   Index.getDescriptor(clazz).getType(),
                                   buildQuery());
                }
                ResultList<E> resultList = transform(srb);
                for (E entity : resultList) {
                    try {
                        if (!entityDeDuplicator.contains(entity.getId())) {
                            if (lim.nextRow()) {
                                if (!consumer.apply(entity)) {
                                    return;
                                }
                                if (!lim.shouldContinue()) {
                                    return;
                                }
                            }
                            if (rateLimit.check()) {
                                // Check is the user tries to cancel this task
                                if (!ctx.isActive()) {
                                    return;
                                }
                            }
                        }
                    } catch (Exception e) {
                        Exceptions.handle().to(Index.LOG).error(e).handle();
                    }
                }
                // Re-create entity the duplicator
                entityDeDuplicator.clear();
                resultList.getResults().stream().map(Entity::getId).collect(Lambdas.into(entityDeDuplicator));
                start += resultList.size();
                if (resultList.size() < limit) {
                    break;
                }
            }
        } catch (Throwable e) {
            throw Exceptions.handle(Index.LOG, e);
        }
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
        if (!constraints.isEmpty()) {
            sb.append(" WHERE ");
            Monoflop mf = Monoflop.create();
            for (Constraint constraint : constraints) {
                if (mf.successiveCall()) {
                    sb.append(" AND ");
                }
                sb.append(constraint.toString(skipConstraintValues));
            }
        }
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
        if (start > 0 || (limit != null && limit > 0)) {
            sb.append(" LIMIT ");
            sb.append(skipConstraintValues ? "?" : start);
            sb.append(", ");
            sb.append(skipConstraintValues ? "?" : limit);
        }
        return sb.toString();
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
            EntityDescriptor ed = Index.getDescriptor(clazz);
            // If there are foreign keys we cannot use delete by query...
            if (ed.hasForeignKeys()) {
                deleteByIteration();
            } else {
                deleteByQuery(ed);
            }
            if (Microtiming.isEnabled()) {
                w.submitMicroTiming("ES", "DELETE: " + toString(true));
            }
        } catch (Throwable e) {
            throw Exceptions.handle(Index.LOG, e);
        }
    }

    protected void deleteByQuery(EntityDescriptor ed) {
        DeleteByQueryRequestBuilder builder = Index.getClient()
                                                   .prepareDeleteByQuery(index != null ? index : Index.getIndex(clazz))
                                                   .setTypes(ed.getType());
        if (Strings.isFilled(routing)) {
            if (!ed.hasRouting()) {
                throw Exceptions.handle()
                                .to(Index.LOG)
                                .withSystemErrorMessage("Performing a delete on %s with a routing "
                                                        + "- but entity has no routing attribute (in @Indexed)! "
                                                        + "This will most probably FAIL!", clazz.getName())
                                .handle();
            }
            builder.setRouting(routing);
        } else if (ed.hasRouting() && !deliberatelyUnrouted) {
            throw Exceptions.handle()
                            .to(Index.LOG)
                            .withSystemErrorMessage("Performing a delete on %s without providing a routing. "
                                                    + "Consider providing a routing for better performance "
                                                    + "or call deliberatelyUnrouted() to signal that routing was "
                                                    + "intentionally skipped.", clazz.getName())
                            .handle();
        }
        QueryBuilder qb = buildQuery();
        if (qb != null) {
            builder.setQuery(qb);
        }
        if (Index.LOG.isFINE()) {
            Index.LOG.FINE("DELETE: %s.%s: %s", Index.getIndex(clazz), ed.getType(), buildQuery());
        }
        builder.execute().actionGet();
    }

    protected void deleteByIteration() throws Throwable {
        ValueHolder<Throwable> error = ValueHolder.of(null);
        iterate(e -> {
            try {
                Index.delete(e);
            } catch (Throwable ex) {
                error.set(ex);
            }
            return true;
        });
        if (error.get() != null) {
            throw error.get();
        }
    }
}
