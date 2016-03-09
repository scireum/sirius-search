/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.util;

import sirius.kernel.commons.Context;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.search.IndexAccess;
import sirius.web.templates.ContentContextExtender;

import javax.annotation.Nonnull;

/**
 * Provides access to {@link IndexAccess} as "index" in scripts.
 */
@Register
public class SearchContextExtender implements ContentContextExtender {

    @Part
    private IndexAccess index;

    @Override
    public void extend(@Nonnull Context context) {
        context.put("index", index);
    }
}
