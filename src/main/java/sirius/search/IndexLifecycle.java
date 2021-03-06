/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.kernel.Killable;
import sirius.kernel.Sirius;
import sirius.kernel.Startable;
import sirius.kernel.Stoppable;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import static sirius.search.IndexAccess.LOG;

/**
 * Starts and stops the elasticsearch client.
 */
@Register(classes = {Startable.class, Stoppable.class, Killable.class})
public class IndexLifecycle implements Startable, Stoppable, Killable {

    @Part
    private IndexAccess index;

    @Override
    public int getPriority() {
        return 75;
    }

    @Override
    public void started() {
        if (!isEnabled()) {
            LOG.INFO("ElasticSearch is disabled! (index.host is not set)");
            return;
        }

        index.startup();
    }

    private boolean isEnabled() {
        return Strings.isFilled(Sirius.getSettings().getString("index.host"));
    }

    @Override
    public void stopped() {
        if (!isEnabled()) {
            return;
        }

        if (index.delayLineTimer != null) {
            index.delayLineTimer.cancel();
        }
    }

    @Override
    public void awaitTermination() {
        if (!isEnabled()) {
            LOG.INFO("ElasticSearch is disabled! (index.host is not set)");
            return;
        }

        // may take longer than 10 seconds
        index.getSchema().dropTemporaryIndices();

        // We wait until this last call before we cut the connection to the database (elasticsearch) to permit
        // other stopping lifecycles access until the very end...
        index.ready = false;
        index.client.close();
    }
}
