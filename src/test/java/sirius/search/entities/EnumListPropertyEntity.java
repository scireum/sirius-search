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
import sirius.search.annotations.ListType;
import sirius.search.properties.ESOption;

import java.util.List;

@Indexed(index = "test")
public class EnumListPropertyEntity extends Entity {

    @ListType(ESOption.class)
    private List<ESOption> enumList;

    public List<ESOption> getEnumList() {
        return enumList;
    }
}
