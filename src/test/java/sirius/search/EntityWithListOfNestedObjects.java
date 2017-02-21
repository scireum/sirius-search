/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.search.annotations.Indexed;
import sirius.search.annotations.ListType;

import java.util.ArrayList;
import java.util.List;

@Indexed(index = "test")
public class EntityWithListOfNestedObjects extends Entity{

    public static final String NESTED_OBJECTS = "nestedObjects";
    @ListType(value = NestedObject.class, nested = true)
    private List<NestedObject> nestedObjects = new ArrayList<>();

    public List<NestedObject> getNestedObjects() {
        return nestedObjects;
    }
}
