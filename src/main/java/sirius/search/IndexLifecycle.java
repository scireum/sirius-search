/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.kernel.Lifecycle;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

/**
 * Starts and stops the elasticsearch client.
 */
@Register(classes = Lifecycle.class)
public class IndexLifecycle implements Lifecycle {

    @Part
    private IndexAccess index;

    @Override
    public int getPriority() {
        return 75;
    }

    @Override
    public void started() {
        if (Strings.isEmpty(Sirius.getConfig().getString("index.type"))) {
            IndexAccess.LOG.INFO("ElasticSearch is disabled! (index.type is not set)");
            return;
        }

        index.startup();
    }

    @Override
    public void stopped() {
        if (index.delayLineTimer != null) {
            index.delayLineTimer.cancel();
        }
    }

    @Override
    public void awaitTermination() {
        // We wait until this last call before we cut the connection to the database (elasticsearch) to permit
        // other stopping lifecycles access until the very end...
        index.ready = false;
        index.client.close();
        if (index.inMemoryNode != null) {
            index.inMemoryNode.close();
        }
    }

    @Override
    public String getName() {
        return "index (ElasticSearch)";
    }
}