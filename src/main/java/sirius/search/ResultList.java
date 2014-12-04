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
import org.elasticsearch.search.facet.terms.TermsFacet;
import sirius.kernel.commons.Monoflop;
import sirius.web.controller.Facet;

import java.util.Iterator;
import java.util.List;

/**
 * Combines the result items of a query along with the collected facet filters.
 * <p>
 * Instances of this class are created by {@link sirius.search.Query#queryResultList()} as a result of a database
 * query.
 * </p>
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
public class ResultList<T> implements Iterable<T> {

    private final List<Facet> termFacets;
    private List<T> results = Lists.newArrayList();
    private SearchResponse response;
    private Monoflop factesProcessed = Monoflop.create();

    /**
     * Creates a new result list
     *
     * @param termFacets list of facets created by the query
     * @param response   underlying search response building the result
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
     * Returns the facet filters collected when creating the result
     *
     * @return the facet filters defined by the query
     */
    public List<Facet> getFacets() {
        if (factesProcessed.firstCall() && response != null) {
            for (Facet facet : termFacets) {
                TermsFacet tf = response.getFacets().facet(TermsFacet.class, facet.getName());
                for (TermsFacet.Entry entry : tf.getEntries()) {
                    String key = entry.getTerm().string();
                    facet.addItem(key, key, entry.getCount());
                }
            }
        }
        return termFacets;
    }
}
