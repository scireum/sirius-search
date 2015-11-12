/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.search.annotations.Indexed;
import sirius.search.annotations.RefField;
import sirius.search.annotations.RefType;

@Indexed(index = "test")
public class SetNullChildEntity extends Entity {

    @RefType(type = ParentEntity.class, cascade = Cascade.SET_NULL)
    private EntityRef<ParentEntity> parent;

    @RefField(localRef = "parent", remoteField = "name")
    private String parentName;

    public EntityRef<ParentEntity> getParent() {
        return parent;
    }

    public String getParentName() {
        return parentName;
    }
}
