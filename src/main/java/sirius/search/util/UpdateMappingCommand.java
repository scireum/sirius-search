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
import sirius.kernel.di.std.Register;
import sirius.search.Entity;
import sirius.search.EntityDescriptor;
import sirius.search.Index;
import sirius.web.health.console.Command;

/**
 * Creates or re-creates the mapping for a given entity.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2014/07
 */
@Register
public class UpdateMappingCommand implements Command {
    @Override
    public void execute(Output output, String... params) throws Exception {
        if (Value.indexOf(0, params).isEmptyString()) {
            output.line("Usage: updateMapping <type> <force (y/N)>");
            output.line("If force is set to Yes, data loss might be possible!");
        } else {
            Class<? extends Entity> type = Index.getType(params[0]);
            if (type == null) {
                output.line("Unknown type: " + params[0]);
                Monoflop mf = Monoflop.create();
                for(String name : Index.getSchema().getTypeNames()) {
                    if (name.toLowerCase().contains(params[0].toLowerCase())) {
                        if (mf.firstCall()) {
                            output.line("Did you mean one of those: ");
                        }
                        output.line(" * "+name);
                    }
                }
            } else {
                EntityDescriptor ed = Index.getDescriptor(type);
                try {
                    Index.addMapping(Index.getIndexPrefix() + ed.getIndex(),
                                     type,
                                     Value.indexOf(1, params).asString().toLowerCase().equals("y"));
                    output.line("Mapping was updated...");
                } catch (Exception e) {
                    output.line(e.getMessage());
                    output.line("If necessary use the force option but be aware that there might be data loss!");
                }
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
