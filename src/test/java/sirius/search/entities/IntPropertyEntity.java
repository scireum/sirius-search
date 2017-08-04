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

@Indexed(index = "test")
public class IntPropertyEntity extends Entity {

    private int primitiveValue;
    private Integer value;

    public int getPrimitiveValue() {
        return primitiveValue;
    }

    public void setPrimitiveValue(int primitiveValue) {
        this.primitiveValue = primitiveValue;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
