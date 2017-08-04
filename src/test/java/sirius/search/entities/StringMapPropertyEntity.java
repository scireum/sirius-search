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
import sirius.search.annotations.MapType;

import java.util.Map;

@Indexed(index = "test")
public class StringMapPropertyEntity extends Entity {

    @MapType(String.class)
    private Map<String, String> stringMap;

    public Map<String, String> getStringMap() {
        return stringMap;
    }
}
