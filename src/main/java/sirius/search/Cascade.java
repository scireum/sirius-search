/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

/**
 * Determines if and how operations are cascaded across foreign keys (field references).
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/12
 */
public enum Cascade {
    REJECT, CASCADE, LAZY_CASCADE, SET_NULL, IGNORE;
}
