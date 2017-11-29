/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.util;

import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.console.Command;
import sirius.search.Entity;
import sirius.search.EntityDescriptor;
import sirius.search.IndexAccess;

/**
 * Creates or re-creates the mapping for a given entity.
 */
@Register
public class UpdateMappingCommand implements Command {

    @Part
    private static IndexAccess index;

    @Override
    public void execute(Output output, String... params) throws Exception {
        if (Value.indexOf(0, params).isEmptyString()) {
            output.line("Usage: updateMapping <type>");
        } else {
            Class<? extends Entity> type = findTypeOrReportError(output, params[0]);
            if (type != null) {
                EntityDescriptor ed = index.getDescriptor(type);
                try {
                    index.addMapping(index.getIndexName(ed.getIndex()), type);
                    output.line("Mapping was updated...");
                } catch (Exception e) {
                    output.line(e.getMessage());
                    output.line("If necessary use the force option but be aware that there might be data loss!");
                }
            }
        }
    }

    protected static Class<? extends Entity> findTypeOrReportError(Output output, String typeName) {
        Class<? extends Entity> type = index.getType(typeName);
        if (type == null) {
            reportUnknownType(output, typeName);
        }
        return type;
    }

    private static void reportUnknownType(Output output, String typeName) {
        output.line("Unknown type: " + typeName);
        Monoflop mf = Monoflop.create();
        for (String name : index.getSchema().getTypeNames()) {
            if (name.toLowerCase().contains(typeName.toLowerCase())) {
                if (mf.firstCall()) {
                    output.line("Did you mean one of those: ");
                }
                output.line(" * " + name);
            }
        }
    }

    @Override
    public String getName() {
        return "updateMapping";
    }

    @Override
    public String getDescription() {
        return "Tries to update the mapping of the given type";
    }
}
