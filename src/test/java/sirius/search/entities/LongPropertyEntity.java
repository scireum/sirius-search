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
public class LongPropertyEntity extends Entity {

    private long primitiveValue;
    private Long value;

    public long getPrimitiveValue() {
        return primitiveValue;
    }

    public void setPrimitiveValue(long primitiveValue) {
        this.primitiveValue = primitiveValue;
    }

    public Long getValue() {
        return value;
    }

    public void setValue(Long value) {
        this.value = value;
    }
}
