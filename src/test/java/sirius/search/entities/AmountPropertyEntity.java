/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.entities;

import sirius.kernel.commons.Amount;
import sirius.search.Entity;
import sirius.search.annotations.Indexed;

@Indexed(index = "test")
public class AmountPropertyEntity extends Entity {

    private Amount amount;

    public Amount getAmount() {
        return amount;
    }

    public void setAmount(Amount amount) {
        this.amount = amount;
    }
}
