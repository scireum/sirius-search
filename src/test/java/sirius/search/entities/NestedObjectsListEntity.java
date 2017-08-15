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

import java.util.ArrayList;
import java.util.List;

@Indexed(index = "test")
public class NestedObjectsListEntity extends Entity {

    @ListType(POJO.class)
    private List<POJO> nestedObjects = new ArrayList<>();
    public static final String NESTED_OBJECTS = "nestedObjects";

    public List<POJO> getNestedObjects() {
        return nestedObjects;
    }
}
