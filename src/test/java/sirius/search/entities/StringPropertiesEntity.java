/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.entities;

import sirius.search.Entity;
import sirius.search.annotations.Indexed;
import sirius.search.annotations.ListType;

import java.util.List;

@Indexed(index = "test")
public class StringPropertiesEntity extends Entity {

    private String soloString;

    @ListType(String.class)
    private List<String> stringList;

    public String getSoloString() {
        return soloString;
    }

    public void setSoloString(String soloString) {
        this.soloString = soloString;
    }

    public List<String> getStringList() {
        return stringList;
    }
}
