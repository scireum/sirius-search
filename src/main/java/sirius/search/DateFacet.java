/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import com.google.common.collect.Maps;
import sirius.web.controller.Facet;

import java.util.Map;

/**
 * Represents a facet which can be applied on a date field to group entites by given date ranges.
 */
class DateFacet extends Facet {

    private Map<String, DateRange> values = Maps.newLinkedHashMap();

    /*
     * Instances are created by calling Query.addDateRangeFacet
     */
    protected DateFacet(String title, String field, String value, DateRange[] ranges) {
        super(title, field, value, null);
        for (DateRange range : ranges) {
            values.put(range.getKey(), range);
        }
    }

    protected DateRange getRangeByName(String value) {
        return values.get(value);
    }

    protected Iterable<DateRange> getRanges() {
        return values.values();
    }
}
