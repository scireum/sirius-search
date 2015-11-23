/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.util;

import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import sirius.kernel.commons.Values;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.search.Entity;
import sirius.search.EntityDescriptor;
import sirius.search.Index;
import sirius.search.IndexAccess;
import sirius.web.health.console.Command;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides <tt>es</tt> as console command to query, update or delete entities.
 */
@Register
public class ESCommand implements Command {

    @Part
    private IndexAccess index;

    @Override
    public void execute(Output output, String... params) throws Exception {
        Values values = Values.of(params);
        if ("query".equalsIgnoreCase(values.at(0).asString())) {
            query(output, values);
        } else if ("update".equalsIgnoreCase(values.at(0).asString())) {
            update(output, values);
        } else if ("delete".equalsIgnoreCase(values.at(0).asString())) {
            delete(output, values);
        } else if ("resave".equalsIgnoreCase(values.at(0).asString())) {
            resave(output, values);
        } else if ("unbalance".equalsIgnoreCase(values.at(0).asString())) {
            unbalance(output, values);
        } else if ("balance".equalsIgnoreCase(values.at(0).asString())) {
            balance(output, values);
        } else {
            output.apply("Unknown command: %s", values.at(0));
            output.line("Use: query <type> <filter>");
            output.line(" or update <type> <filter> <field> <value> (Limit 500)");
            output.line(" or delete <type> <filter>");
            output.line(" or resave <type> <filter>");
            output.line(" or unbalance");
            output.line(" or balance");
        }
    }

    private void query(Output output, Values values) {
        Class<? extends Entity> type = UpdateMappingCommand.findTypeOrReportError(output, values.at(1).asString());
        if (type != null) {
            output.line("Results (limited at 500):");
            output.separator();
            int rows = 0;
            for (Entity e : index.select(type)
                                 .deliberatelyUnrouted()
                                 .query(values.at(2).asString())
                                 .limit(500)
                                 .queryList()) {
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
            EntityDescriptor ed = index.getDescriptor(type);
            AtomicInteger rows = new AtomicInteger();
            String filterText = values.at(2).asString();
            if ("-".equals(filterText)) {
                filterText = null;
            }
            index.select(type).deliberatelyUnrouted().query(filterText).limit(500).iterateAll(e -> {
                ed.getProperty(values.at(3).asString()).readFromSource(e, values.at(4).get());
                index.update(e);
                rows.incrementAndGet();
            });
            output.separator();
            output.apply("%s rows affected", rows.get());
            output.blankLine();
        }
    }

    private void resave(Output output, Values values) {
        Class<? extends Entity> type = UpdateMappingCommand.findTypeOrReportError(output, values.at(1).asString());
        if (type != null) {
            EntityDescriptor ed = index.getDescriptor(type);
            AtomicInteger rows = new AtomicInteger();
            index.select(type).deliberatelyUnrouted().query(values.at(2).asString()).iterateAll(e -> {
                index.update(e);
                rows.incrementAndGet();
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
            index.select(type).deliberatelyUnrouted().query(values.at(2).asString()).iterateAll(e -> {
                index.delete(e);
                rows.incrementAndGet();
            });
            output.apply("%s rows affected", rows.get());
            output.blankLine();
        }
    }

    private void unbalance(Output output, Values values) {
        output.line("Disabling automatic allocation.");

        changeAllocationMode("none");

        output.line("Disabled automatic allocation.");
        output.blankLine();
    }

    private void changeAllocationMode(String mode) {
        ClusterUpdateSettingsRequest request = new ClusterUpdateSettingsRequest();
        Settings settings = ImmutableSettings.settingsBuilder().put("cluster.routing.allocation.enable", mode).build();
        request.transientSettings(settings);
        index.getClient().admin().cluster().updateSettings(request);
    }

    private void balance(Output output, Values values) {
        output.line("Enabling automatic allocation.");

        changeAllocationMode("all");

        output.line("Enabled automatic allocation.");
        output.blankLine();
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
