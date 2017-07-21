/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.util;

import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.search.IndexAccess;
import sirius.web.templates.GlobalContextExtender;

import java.util.function.BiConsumer;

/**
 * Provides access to {@link IndexAccess} as "index" in scripts.
 */
@Register
public class SearchContextExtender implements GlobalContextExtender {

    @Part
    private IndexAccess index;

    @Override
    public void collectTemplate(BiConsumer<String, Object> globalParameterCollector) {
        // Nothing provided
    }

    @Override
    public void collectScripting(BiConsumer<String, Object> globalParameterCollector) {
        globalParameterCollector.accept("index", index);
    }
}
