/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.entities;

import sirius.search.annotations.Indexed;

@Indexed(index = "test")
public class POJO {
    public static final String NUMBER_VAR = "numberVar";
    private Long numberVar;

    public static final String STRING_VAR = "stringVar";
    private String stringVar;

    public static final String BOOL_VAR = "boolVar";
    private Boolean boolVar;

    public Long getNumberVar() {
        return numberVar;
    }

    public void setNumberVar(Long numberVar) {
        this.numberVar = numberVar;
    }

    public String getStringVar() {
        return stringVar;
    }

    public void setStringVar(String string) {
        this.stringVar = string;
    }

    public Boolean getBoolVar() {
        return boolVar;
    }

    public void setBoolVar(Boolean boolVar) {
        this.boolVar = boolVar;
    }
}
