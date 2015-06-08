/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.search.annotations.Indexed;

/**
 * Used by the {@link IdGenerator} to create unique IDs.
 */
@Indexed(index = "core")
public class Sequence extends Entity {

    private int next;
    public static final String NEXT = "next";

    public int getNext() {
        return next;
    }

    public void setNext(int next) {
        this.next = next;
    }
}
