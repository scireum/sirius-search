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
import sirius.search.EntityRef;
import sirius.search.annotations.Indexed;
import sirius.search.annotations.RefType;

@Indexed(index = "test")
public class CascadingChildEntity extends Entity {

    @RefType(type = ParentEntity.class, cascade = Cascade.CASCADE)
    private EntityRef<ParentEntity> parent;

    public EntityRef<ParentEntity> getParent() {
        return parent;
    }
}
