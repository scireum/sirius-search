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
public class NumberQueryEntity extends Entity {

    public static final String NUMBER = "number";

    private int number;

    public static final String RANKING = "ranking";
    private long ranking;

    public Number getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public long getRanking() {
        return ranking;
    }

    public void setRanking(long ranking) {
        this.ranking = ranking;
    }
}
