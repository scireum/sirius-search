/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.search.annotations.Indexed;
import sirius.search.annotations.RefType;

@Indexed(index = "test")
public class SetNullManyChildEntity extends Entity {

    @RefType(type = ParentEntity.class, cascade = Cascade.SET_NULL)
    private EntityRefList<ParentEntity> parents;

    public EntityRefList<ParentEntity> getParents() {
        return parents;
    }
}
