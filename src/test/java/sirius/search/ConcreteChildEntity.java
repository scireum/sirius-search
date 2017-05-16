/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.search.annotations.Indexed;

@Indexed(index = "test", type = "AbstractParentEntity", subClassCode = "concrete")
public class ConcreteChildEntity extends AbstractParentEntity {

    private String subname;

    public String getSubname() {
        return subname;
    }

    public void setSubname(String subname) {
        this.subname = subname;
    }
}
