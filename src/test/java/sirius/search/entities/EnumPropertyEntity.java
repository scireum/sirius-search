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
import sirius.search.properties.ESOption;

@Indexed(index = "test")
public class EnumPropertyEntity extends Entity {

    private ESOption value;

    public ESOption getValue() {
        return value;
    }

    public void setValue(ESOption value) {
        this.value = value;
    }
}
