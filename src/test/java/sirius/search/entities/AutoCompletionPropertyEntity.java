/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.entities;

import sirius.search.Entity;
import sirius.search.annotations.FastCompletion;
import sirius.search.annotations.Indexed;
import sirius.search.annotations.NestedObject;
import sirius.search.suggestion.AutoCompletion;

@Indexed(index = "test")
public class AutoCompletionPropertyEntity extends Entity {

    @FastCompletion(contextNames = "filter")
    @NestedObject(AutoCompletion.class)
    private AutoCompletion complete;
    public static final String COMPLETE = "complete";

    public AutoCompletion getComplete() {
        return complete;
    }

    public void setComplete(AutoCompletion complete) {
        this.complete = complete;
    }
}
