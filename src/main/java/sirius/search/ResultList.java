/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import com.google.common.collect.Lists;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import sirius.kernel.commons.Monoflop;
import sirius.search.aggregation.Aggregation;
import sirius.web.controller.Facet;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Combines the result items of a query along with the collected facet filters and aggregations.
 * <p>
 * Instances of this class are created by {@link sirius.search.Query#queryResultList()} as a result of a database
 * query.
 *
 * @param <T> the type of entities in the result
 */
public class ResultList<T> implements Iterable<T> {

    private final List<Facet> termFacets;
    private List<T> results = Lists.newArrayList();
    private SearchResponse response;
    private Monoflop facetsProcessed = Monoflop.create();

    /**
     * Creates a new result list
     *
     * @param termFacets   list of facets created by the query
     * @param response     underlying search response building the result
     */
    protected ResultList(List<Facet> termFacets, SearchResponse response) {
        this.termFacets = termFacets;
        this.response = response;
    }

    @Override
    public Iterator<T> iterator() {
        return results.iterator();
    }

    /**
     * Returns the number of entities in the result list.
     *
     * @return the number of entities
     */
    public int size() {
        return results.size();
    }

    /**
     * Determines if there were no results
     *
     * @return <tt>true</tt> if there are no entities in the result list, <tt>false</tt> otherwise
     */
    public boolean isEmpty() {
        return results.isEmpty();
    }

    /**
     * Opposite of {@link #isEmpty()}.
     *
     * @return <tt>true</tt> if there is at least one result item, <tt>false</tt> otherwise
     */
    public boolean isFilled() {
        return !results.isEmpty();
    }

    /**
     * Returns the list of all result entities
     *
     * @return the underlying list of result entities. Note that this is not a copy so modifying this list will alter
     * the result
     */
    public List<T> getResults() {
        return results;
    }

    /**
     * Returns the total number of hits not just the fetched ones.
     *
     * @return the total number of hits
     */
    public long getTotalNumberOfHits() {
        return this.response.getHits().getTotalHits();
    }

    /**
     * Returns the facet filters collected when creating the result
     *
     * @return the facet filters defined by the query
     */
    public List<Facet> getFacets() {
        if (facetsProcessed.firstCall() && response != null) {
            for (Facet facet : termFacets) {
                if (facet instanceof DateFacet) {
                    Range range = response.getAggregations().get(facet.getName());
                    for (Range.Bucket bucket : range.getBuckets()) {
                        if (bucket.getDocCount() > 0) {
                            DateRange dateRange = ((DateFacet) facet).getRangeByName(bucket.getKeyAsString());
                            if (dateRange != null) {
                                facet.addItem(dateRange.getKey(), dateRange.getName(), bucket.getDocCount());
                            }
                        }
                    }
                } else {
                    Terms terms = response.getAggregations().get(facet.getName());
                    for (Terms.Bucket bucket : terms.getBuckets()) {
                        String key = bucket.getKeyAsString();
                        facet.addItem(key, key, bucket.getDocCount());
                    }
                }
            }
        }
        return termFacets;
    }

    /**
     * Returns all computed aggregations as a map
     *
     * @return a map containing the computed aggregations per field
     */
    public Map<String, org.elasticsearch.search.aggregations.Aggregation> getAggregations() {
        return response.getAggregations().asMap();
    }
}
