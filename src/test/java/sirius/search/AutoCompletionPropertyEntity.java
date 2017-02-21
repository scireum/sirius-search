/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.search.annotations.*;
import sirius.search.suggestion.AutoCompletion;

@Indexed(index = "test")
public class AutoCompletionPropertyEntity extends Entity{

    public static final String COMPLETE = "complete";
    @FastCompletion(contextName = "filter")
    @sirius.search.annotations.NestedObject(sirius.search.suggestion.AutoCompletion.class)
    private AutoCompletion complete;

    public AutoCompletion getComplete() {
        return complete;
    }

    public void setComplete(AutoCompletion complete) {
        this.complete = complete;
    }
}
