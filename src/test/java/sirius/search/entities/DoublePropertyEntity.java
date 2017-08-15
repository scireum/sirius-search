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
public class DoublePropertyEntity extends Entity {

    private double primitiveValue;
    private Double value;

    public double getPrimitiveValue() {
        return primitiveValue;
    }

    public void setPrimitiveValue(double primitiveValue) {
        this.primitiveValue = primitiveValue;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }
}
