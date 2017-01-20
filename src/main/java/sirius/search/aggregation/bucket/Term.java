/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.aggregation.bucket;

import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;

/**
 * Represents an aggregation using terms
 */
public class Term extends BucketAggregation {

    private Term(String name) {
        super(name);
    }

    /**
     * Used to generate a termaggregation with the given name and on the provided field
     *
     * @param field the field to be used
     * @param name  the name of this aggregation
     * @return a newly created termaggregation helper
     */
    public static Term on(String field, String name) {
        Term aggregation = new Term(name);
        aggregation.field = field;
        return aggregation;
    }

    /**
     * Constructs the termbuilder
     *
     * @return the termbuilder
     */
    @Override
    public TermsBuilder getBuilder() {
        return AggregationBuilders.terms(getName()).field(getField()).size(getSize());
    }
}
