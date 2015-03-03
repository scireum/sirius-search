/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.util;

import sirius.kernel.commons.Values;
import sirius.kernel.di.std.Register;
import sirius.search.Entity;
import sirius.search.EntityDescriptor;
import sirius.search.Index;
import sirius.web.health.console.Command;

import javax.annotation.Nonnull;

/**
 * Provides <tt>es</tt> as console command to query, update or delete entities.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2015/02
 */
@Register
public class ESCommand implements Command {

    @Override
    public void execute(Output output, String... params) throws Exception {
        Values values = Values.of(params);
        if (values.at(0).equalsIgnoreCase("query")) {
            query(output, values);
        } else if (values.at(0).equalsIgnoreCase("update")) {
            update(output, values);
        } else if (values.at(0).equalsIgnoreCase("delete")) {
            delete(output, values);
        } else {
            output.apply("Unknown command: %s", values.at(0));
            output.line("Use: query <type> <filter>");
            output.line(" or update <type> <filter> <field> <value> or del");
            output.line(" or delete <type> <filter>");
        }
    }

    private void query(Output output, Values values) {
        Class<? extends Entity> type = UpdateMappingCommand.findTypeOrReportError(output, values.at(1).asString());
        if (type != null) {
            output.line("Results (limited at 500):");
            output.separator();
            int rows = 0;
            for (Entity e : Index.select(type).query(values.at(2).asString()).limit(500).queryList()) {
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
            int rows = 0;
            for (Entity e : Index.select(type).query(values.at(2).asString()).limit(500).queryList()) {
                ed.getProperty(values.at(3).asString()).readFromSource(e, values.at(4).get());
                Index.update(e);
                rows++;
            }
            output.separator();
            output.apply("%s rows affected", rows);
            output.blankLine();
        }
    }

    private void delete(Output output, Values values) {
        Class<? extends Entity> type = UpdateMappingCommand.findTypeOrReportError(output, values.at(1).asString());
        if (type != null) {
            int rows = 0;
            for (Entity e : Index.select(type).query(values.at(2).asString()).queryList()) {
                Index.delete(e);
                rows++;
            }
            output.apply("%s rows affected", rows);
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
