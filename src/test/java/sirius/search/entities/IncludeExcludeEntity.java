/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.entities;

import sirius.search.Entity;
import sirius.search.annotations.IndexMode;
import sirius.search.annotations.Indexed;
import sirius.search.annotations.ListType;
import sirius.search.annotations.MapType;
import sirius.search.properties.ESOption;

import java.util.List;
import java.util.Map;

@Indexed(index = "test")
public class IncludeExcludeEntity extends Entity {

    @IndexMode(includeInAll = ESOption.TRUE)
    private String stringIncluded;

    @IndexMode(includeInAll = ESOption.FALSE)
    private String stringExcluded;

    @IndexMode(includeInAll = ESOption.TRUE)
    @ListType(String.class)
    private List<String> stringListIncluded;

    @IndexMode(includeInAll = ESOption.FALSE)
    @ListType(String.class)
    private List<String> stringListExcluded;

    @IndexMode(includeInAll = ESOption.TRUE)
    @MapType(String.class)
    private Map<String, String> stringMapIncluded;

    @IndexMode(includeInAll = ESOption.FALSE)
    @MapType(String.class)
    private Map<String, String> stringMapExcluded;

    @IndexMode(includeInAll = ESOption.TRUE)
    private int intIncluded;

    @IndexMode(includeInAll = ESOption.FALSE)
    private int intExcluded;

    public String getStringIncluded() {
        return stringIncluded;
    }

    public void setStringIncluded(String stringIncluded) {
        this.stringIncluded = stringIncluded;
    }

    public String getStringExcluded() {
        return stringExcluded;
    }

    public void setStringExcluded(String stringExcluded) {
        this.stringExcluded = stringExcluded;
    }

    public List<String> getStringListIncluded() {
        return stringListIncluded;
    }

    public List<String> getStringListExcluded() {
        return stringListExcluded;
    }

    public Map<String, String> getStringMapIncluded() {
        return stringMapIncluded;
    }

    public Map<String, String> getStringMapExcluded() {
        return stringMapExcluded;
    }

    public int getIntIncluded() {
        return intIncluded;
    }

    public void setIntIncluded(int intIncluded) {
        this.intIncluded = intIncluded;
    }

    public int getIntExcluded() {
        return intExcluded;
    }

    public void setIntExcluded(int intExcluded) {
        this.intExcluded = intExcluded;
    }
}
