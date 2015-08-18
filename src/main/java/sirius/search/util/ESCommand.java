/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.util;

import sirius.kernel.commons.Exec;
import sirius.kernel.commons.Values;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.search.Entity;
import sirius.search.EntityDescriptor;
import sirius.search.Index;
import sirius.web.health.console.Command;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides <tt>es</tt> as console command to query, update or delete entities.
 */
@Register
public class ESCommand implements Command {

    @Override
    public void execute(Output output, String... params) throws Exception {
        Values values = Values.of(params);
        if ("query".equalsIgnoreCase(values.at(0).asString())) {
            query(output, values);
        } else if ("update".equalsIgnoreCase(values.at(0).asString())) {
            update(output, values);
        } else if ("delete".equalsIgnoreCase(values.at(0).asString())) {
            delete(output, values);
        } else if ("unbalance".equalsIgnoreCase(values.at(0).asString())) {
            unbalance(output, values);
        } else if ("balance".equalsIgnoreCase(values.at(0).asString())) {
            balance(output, values);
        } else {
            output.apply("Unknown command: %s", values.at(0));
            output.line("Use: query <type> <filter>");
            output.line(" or update <type> <filter> <field> <value>");
            output.line(" or delete <type> <filter>");
        }
    }

    private void query(Output output, Values values) {
        Class<? extends Entity> type = UpdateMappingCommand.findTypeOrReportError(output, values.at(1).asString());
        if (type != null) {
            output.line("Results (limited at 500):");
            output.separator();
            int rows = 0;
            for (Entity e : Index.select(type).deliberatelyUnrouted().query(values.at(2).asString()).limit(500).queryList()) {
                output.line(e.toDebugString());
                rows++;
            }
            output.separator();
            output.apply("%s rows affected", rows);
            output.blankLine();
        }
    }

    private void update(Output output, Values values) {
        Class<? extends Entity> type = UpdateMappingCommand.findTypeOrReportError(output, values.at(1).asString());
        if (type != null) {
            EntityDescriptor ed = Index.getDescriptor(type);
            AtomicInteger rows = new AtomicInteger();
            Index.select(type).deliberatelyUnrouted().query(values.at(2).asString()).limit(500).iterate(e -> {
                ed.getProperty(values.at(3).asString()).readFromSource(e, values.at(4).get());
                Index.update(e);
                rows.incrementAndGet();
                return true;
            });
            output.separator();
            output.apply("%s rows affected", rows.get());
            output.blankLine();
        }
    }

    private void delete(Output output, Values values) {
        Class<? extends Entity> type = UpdateMappingCommand.findTypeOrReportError(output, values.at(1).asString());
        if (type != null) {
            AtomicInteger rows = new AtomicInteger();
            Index.select(type).deliberatelyUnrouted().query(values.at(2).asString()).iterate(e -> {
                Index.delete(e);
                rows.incrementAndGet();
                return true;
            });
            output.apply("%s rows affected", rows.get());
            output.blankLine();
        }
    }

    private void unbalance(Output output, Values values) {
        try {
            output.line("Disabling automatic allocation.");
            Exec.exec(
                    "curl -XPUT localhost:9200/_cluster/settings -d '{\"transient\":{\"cluster.routing.allocation.enable\": \"none\"}}");
            output.line("Disabled automatic allocation.");
        } catch (Exec.ExecException e) {
            output.line("Failed to disable automatic allocation.");
            Exceptions.handle(e);
        } finally {
            output.blankLine();
        }
    }

    private void balance(Output output, Values values) {
        try {
            output.line("Enabling automatic allocation.");
            Exec.exec(
                    "curl -XPUT localhost:9200/_cluster/settings -d '{\"transient\":{\"cluster.routing.allocation.enable\": \"all\"}}");
            output.line("Enabled automatic allocation.");
        } catch (Exec.ExecException e) {
            output.line("Failed to enable automatic allocation.");
            Exceptions.handle(e);
        } finally {
            output.blankLine();
        }
    }

    @Override
    public String getDescription() {
        return "Executes Queries against Elasticsearch (use with caution!)";
    }

    @Nonnull
    @Override
    public String getName() {
        return "es";
    }
}
