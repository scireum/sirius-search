/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.util;

import sirius.kernel.di.std.Register;
import sirius.search.Index;
import sirius.web.health.console.Command;

/**
 * Performs a re-index of all indices into new ones starting with the given index prefix instead of the
 * currently active one.
 */
@Register
public class ReIndexCommand implements Command {
    @Override
    public void execute(Output output, String... params) throws Exception {
        if (params.length != 1) {
            output.line("Usage: reindex <newIndexPrefix>");
        } else {
            Index.getSchema().reIndex(params[0]);
            output.line("Operation has started!");
        }
    }

    @Override
    public String getName() {
        return "reindex";
    }

    @Override
    public String getDescription() {
        return "Runs an re-index on ElasticSearch";
    }
}
