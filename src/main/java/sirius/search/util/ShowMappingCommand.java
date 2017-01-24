/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.util;

import com.google.common.base.Charsets;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.console.Command;
import sirius.search.Entity;
import sirius.search.EntityDescriptor;
import sirius.search.IndexAccess;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * Displays the mapping stored in elasticsearch of the given types.
 */
@Register
public class ShowMappingCommand implements Command {

    @Part
    private IndexAccess index;

    @Override
    public void execute(Output output, String... params) throws Exception {
        String filter = Value.indexOf(0, params).asString().toLowerCase();
        for (String name : index.getSchema().getTypeNames()) {
            if (Strings.isEmpty(filter) || name.toLowerCase().contains(filter)) {
                Class<? extends Entity> type = index.getType(name);
                EntityDescriptor ed = index.getDescriptor(type);
                GetMappingsResponse res = index.getClient()
                                               .admin()
                                               .indices()
                                               .prepareGetMappings(index.getIndexName(ed.getIndex()))
                                               .setTypes(ed.getType())
                                               .execute()
                                               .actionGet();
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    output.blankLine();
                    output.line(name);
                    output.separator();
                    Iterator<ImmutableOpenMap<String, MappingMetaData>> outerIter = res.mappings().valuesIt();
                    while (outerIter.hasNext()) {
                        ImmutableOpenMap<String, MappingMetaData> c = outerIter.next();
                        Iterator<String> iter = c.keysIt();
                        while (iter.hasNext()) {
                            String property = iter.next();
                            MappingMetaData md = c.get(property);
                            output.line("routing: " + md.routing().toString());
                            for (Map.Entry<String, Object> e : md.getSourceAsMap().entrySet()) {
                                if (e.getValue() instanceof Map) {
                                    output.line(e.getKey());
                                    for (Map.Entry<?, ?> subEntry : ((Map<?, ?>) e.getValue()).entrySet()) {
                                        output.line("   " + subEntry.getKey() + ": " + subEntry.getValue());
                                    }
                                } else {
                                    output.line(e.getKey() + ": " + e.getValue());
                                }
                            }
                        }
                    }
                    output.line(new String(out.toByteArray(), Charsets.UTF_8));
                } catch (IOException e) {
                    output.line(Exceptions.handle(e).getMessage());
                }
            }
        }
    }

    @Override
    public String getName() {
        return "showMapping";
    }

    @Override
    public String getDescription() {
        return "Displays all mappings stored in ES for the given filter";
    }
}
