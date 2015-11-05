/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.search.Entity;
import sirius.search.Index;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a constraint which checks if the given field contains one or all of the values given as a comma
 * separated string. On default the given String will be split on "," and "|" but you can also use factory methods
 * to split by a custom Regular Expression.
 * <p/>
 * Therefore the constraint translates x,y,z for field f to: {@code f = x OR f = y OR f = z}. Empty strings
 * are gracefully handled (ignored). If {@link #orEmpty()} is used, the constraint also succeeds if
 * the target field is empty. This is only valid when <tt>containsAny</tt> is used.
 */
public class CSVFilter implements Constraint {

    private static final String defaultSplitter = "[,\\|]";

    /**
     * Specifies the matching mode for a filter.
     */
    enum Mode {
        CONTAINS_ANY, CONTAINS_ALL
    }

    private final String field;
    private List<String> values;
    private boolean isFilter;
    private boolean orEmpty = false;
    private Mode mode;

    /*
     * Use one of the factory methods
     */
    private CSVFilter(String field, String value, Mode mode, String splitter, boolean lowercaseValues) {
        // In search queries the id field must be referenced via "_id" not "id..
        if (Entity.ID.equalsIgnoreCase(field)) {
            this.field = Index.ID_FIELD;
        } else {
            this.field = field;
        }
        if (Strings.isFilled(value)) {
            Stream<String> stream = Arrays.asList(value.split(splitter))
                                          .stream()
                                          .filter(Objects::nonNull)
                                          .map(String::trim)
                                          .filter(Strings::isFilled);
            if (lowercaseValues) {
                stream = stream.map(String::toLowerCase);
            }
            this.values = stream.collect(Collectors.toList());
        } else {
            this.values = Collections.emptyList();
        }
        this.mode = mode;
    }

    /**
     * Creates a new constraint for the given field which asserts that one of the given values in the string is
     * present.
     * <p/>
     * The string can have a form like A,B,C or A|B|C.
     *
     * @param field                the field to check
     * @param commaSeparatedValues the comma separated values to check for
     * @return a new constraint representing the given filter setting
     */
    public static CSVFilter containsAny(String field, Value commaSeparatedValues) {
        return new CSVFilter(field, commaSeparatedValues.asString(), Mode.CONTAINS_ANY, defaultSplitter, false);
    }

    /**
     * Creates a new constraint for the given field which asserts that one of the given values converted to lowercase
     * in the string is present.
     * <p/>
     * The string can have a form like A,B,C or A|B|C.
     *
     * @param field                the field to check
     * @param commaSeparatedValues the comma separated values to check for
     * @return a new constraint representing the given filter setting
     */
    public static CSVFilter containsAnyLowercase(String field, Value commaSeparatedValues) {
        return new CSVFilter(field, commaSeparatedValues.asString(), Mode.CONTAINS_ANY, defaultSplitter, true);
    }

    /**
     * Creates a new constraint for the given field which asserts that all of the given values in the string is
     * present.
     * <p/>
     * The string can have a form like A,B,C or A|B|C.
     *
     * @param field                the field to check
     * @param commaSeparatedValues the comma separated values to check for
     * @return a new constraint representing the given filter setting
     */
    public static CSVFilter containsAll(String field, Value commaSeparatedValues) {
        return new CSVFilter(field, commaSeparatedValues.asString(), Mode.CONTAINS_ALL, defaultSplitter, false);
    }

    /**
     * Creates a new constraint for the given field which asserts that all of the given values converted to lowercase
     * in the string is present.
     * <p/>
     * The string can have a form like A,B,C or A|B|C.
     *
     * @param field                the field to check
     * @param commaSeparatedValues the comma separated values to check for
     * @return a new constraint representing the given filter setting
     */
    public static CSVFilter containsAllLowercase(String field, Value commaSeparatedValues) {
        return new CSVFilter(field, commaSeparatedValues.asString(), Mode.CONTAINS_ALL, defaultSplitter, true);
    }

    /**
     * Creates a new constraint for the given field which asserts that one of the given values in the string is
     * present.
     * <p/>
     * The string will be split using the given Regular Expression
     *
     * @param field                the field to check
     * @param commaSeparatedValues the comma separated values to check for
     * @param customSplitter       the Regular Expression which is used to split the given values
     * @return a new constraint representing the given filter setting
     */
    public static CSVFilter containsAny(String field, Value commaSeparatedValues, String customSplitter) {
        return new CSVFilter(field, commaSeparatedValues.asString(), Mode.CONTAINS_ANY, customSplitter, false);
    }

    /**
     * Creates a new constraint for the given field which asserts that one of the given values converted to lowercase
     * in the string is present.
     * <p/>
     * The string will be split using the given Regular Expression
     *
     * @param field                the field to check
     * @param commaSeparatedValues the comma separated values to check for
     * @param customSplitter       the Regular Expression which is used to split the given values
     * @return a new constraint representing the given filter setting
     */
    public static CSVFilter containsAnyLowercase(String field, Value commaSeparatedValues, String customSplitter) {
        return new CSVFilter(field, commaSeparatedValues.asString(), Mode.CONTAINS_ANY, customSplitter, true);
    }

    /**
     * Creates a new constraint for the given field which asserts that all of the given values in the string is
     * present.
     * <p/>
     * The string will be split using the given Regular Expression
     *
     * @param field                the field to check
     * @param commaSeparatedValues the comma separated values to check for
     * @param customSplitter       the Regular Expression which is used to split the given values
     * @return a new constraint representing the given filter setting
     */
    public static CSVFilter containsAll(String field, Value commaSeparatedValues, String customSplitter) {
        return new CSVFilter(field, commaSeparatedValues.asString(), Mode.CONTAINS_ALL, customSplitter, false);
    }

    /**
     * Creates a new constraint for the given field which asserts that all of the given values converted to lowercase
     * in the string is present.
     * <p/>
     * The string will be split using the given Regular Expression
     *
     * @param field                the field to check
     * @param commaSeparatedValues the comma separated values to check for
     * @param customSplitter       the Regular Expression which is used to split the given values
     * @return a new constraint representing the given filter setting
     */
    public static CSVFilter containsAllLowercase(String field, Value commaSeparatedValues, String customSplitter) {
        return new CSVFilter(field, commaSeparatedValues.asString(), Mode.CONTAINS_ALL, customSplitter, true);
    }

    /**
     * Signals the constraint to also accept an empty target field if <tt>containsAny</tt> is used.
     *
     * @return the constraint itself for fluent method calls
     */
    public CSVFilter orEmpty() {
        if (mode == Mode.CONTAINS_ALL) {
            throw new IllegalStateException("Cannot apply a CONTAINS ALL constraint which accepts empty values.");
        }
        this.orEmpty = true;
        return this;
    }

    /**
     * Forces this constraint to be applied as filter not as query.
     *
     * @return the constraint itself for fluent method calls
     */
    public CSVFilter asFilter() {
        isFilter = true;
        return this;
    }

    @Override
    public QueryBuilder createQuery() {
        if (values.isEmpty()) {
            return null;
        }
        if (!isFilter && !orEmpty) {
            BoolQueryBuilder bqb = QueryBuilders.boolQuery();
            switch (mode) {
                case CONTAINS_ANY:
                    for (String val : values) {
                        bqb.should(QueryBuilders.termQuery(field, val));
                    }
                    break;
                case CONTAINS_ALL:
                    for (String val : values) {
                        bqb.must(QueryBuilders.termQuery(field, val));
                    }
                    break;
            }
            return bqb;
        }
        return null;
    }

    @Override
    public FilterBuilder createFilter() {
        if (values.isEmpty()) {
            return null;
        }
        if (isFilter || orEmpty) {
            BoolFilterBuilder bfb = FilterBuilders.boolFilter();
            switch (mode) {
                case CONTAINS_ANY:
                    for (String val : values) {
                        bfb.should(FilterBuilders.termFilter(field, val));
                    }
                    break;
                case CONTAINS_ALL:
                    for (String val : values) {
                        bfb.must(FilterBuilders.termFilter(field, val));
                    }
                    break;
            }
            if (orEmpty) {
                bfb.should(FilterBuilders.missingFilter(field));
            }
            return bfb;
        }
        return null;
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return field + " " + mode + " '" + (skipConstraintValues ? "?" : values) + "' " + (orEmpty ?
                                                                                           " OR IS EMPTY" :
                                                                                           "");
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
