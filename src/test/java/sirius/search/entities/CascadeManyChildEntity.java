/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.entities;

import sirius.search.Cascade;
import sirius.search.Entity;
import sirius.search.EntityRefList;
import sirius.search.annotations.Indexed;
import sirius.search.annotations.RefType;

@Indexed(index = "test")
public class CascadeManyChildEntity extends Entity {

    @RefType(type = ParentEntity.class, cascade = Cascade.CASCADE)
    private EntityRefList<ParentEntity> parents;

    public EntityRefList<ParentEntity> getParents() {
        return parents;
    }
}
