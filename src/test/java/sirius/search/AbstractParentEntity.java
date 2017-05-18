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

@Indexed(index = "test", routing = AbstractParentEntity.ROUTING)
public abstract class AbstractParentEntity extends Entity {

    private String name;

    @RefType(type = ParentEntity.class, cascade = Cascade.CASCADE)
    private EntityRef<ParentEntity> routing;
    public static final String ROUTING = "routing";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EntityRef<ParentEntity> getRouting() {
        return routing;
    }
}
