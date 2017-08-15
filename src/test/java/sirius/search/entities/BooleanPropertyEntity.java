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
public class BooleanPropertyEntity extends Entity {

    private boolean primitiveValue;
    private Boolean value;

    public boolean getPrimitiveValue() {
        return primitiveValue;
    }

    public void setPrimitiveValue(boolean primitiveValue) {
        this.primitiveValue = primitiveValue;
    }

    public Boolean getValue() {
        return value;
    }

    public void setValue(Boolean value) {
        this.value = value;
    }
}
