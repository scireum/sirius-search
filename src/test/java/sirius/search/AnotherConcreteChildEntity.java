/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.search.annotations.Indexed;

@Indexed(index = "test", routing = AbstractParentEntity.ROUTING, subClassCode = "another-concrete")
public class AnotherConcreteChildEntity extends AbstractParentEntity {

}
