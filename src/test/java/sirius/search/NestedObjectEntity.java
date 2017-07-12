/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.search.annotations.Indexed;
import sirius.search.annotations.NestedObject;

@Indexed(index = "test")
public class NestedObjectEntity extends Entity {

    @NestedObject(POJO.class)
    private POJO nestedObject;
    public static final String NESTED_OBJECT = "nestedObject";

    public POJO getNestedObject() {
        return nestedObject;
    }

    public void setNestedObject(POJO nestedObject) {
        this.nestedObject = nestedObject;
    }
}
