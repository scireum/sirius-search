/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.kernel.async.Tasks;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.timer.EveryTenSeconds;

import java.util.Iterator;

/**
 * Removes outdated traces used to discover optimistic lock errors
 */
@Register
public class OptimisticLockTracer implements EveryTenSeconds {

    @Part
    private Tasks tasks;

    @Part
    private IndexAccess index;

    @Override
    public void runTimer() throws Exception {
        if (index.traceOptimisticLockErrors) {
            tasks.defaultExecutor().fork(this::cleanOldRecordings);
        }
    }

    private void cleanOldRecordings() {
        long limit = System.currentTimeMillis() - 10_000;
        Iterator<IndexTrace> iter = index.traces.values().iterator();
        while (iter.hasNext()) {
            if (iter.next().timestamp < limit) {
                iter.remove();
            }
        }
    }
}
