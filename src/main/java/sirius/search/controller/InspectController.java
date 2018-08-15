/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.controller;

import org.elasticsearch.common.Strings;
import sirius.kernel.di.PartCollection;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.search.Entity;
import sirius.search.IndexAccess;
import sirius.web.controller.BasicController;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides a page for easily inspecting all json fields of a stored entity.
 */
@Register(classes = Controller.class)
public class InspectController extends BasicController {

    /**
     * Describes the permission required to inspect on ElasticSearch entities.
     */
    public static final String PERMISSION_SYSTEM_INSPECT = "permission-system-inspect";

    @Part
    private IndexAccess index;

    @Parts(Entity.class)
    protected static PartCollection<Entity> entities;

    @Permission(PERMISSION_SYSTEM_INSPECT)
    @Routed("/system/index/inspect")
    public void inspect(WebContext ctx) {
        String id = ctx.get("id").asString();
        String className = ctx.get("class").asString();
        Class<? extends Entity> clazz = null;
        try {
            clazz = Class.forName(className).asSubclass(Entity.class);
        } catch (Exception t) {
            Exceptions.ignore(t);
        }
        Optional<? extends Entity> entity = getEntity(id, clazz);

        String entityId = entity.map(Entity::getId).orElse("");
        String simpleClassName = entity.map(e -> e.getClass().getSimpleName()).orElse("");
        String fullClassName = entity.map(e -> e.getClass().getName()).orElse("");
        String entitySource = entity.map(Entity::toFormattedString).orElse("");

        ctx.respondWith()
           .template("/templates/index-inspect.html.pasta",
                     entities.getParts()
                             .stream()
                             .map(Object::getClass)
                             .sorted(Comparator.comparing(Class::getSimpleName))
                             .collect(Collectors.toList()),
                     entityId,
                     simpleClassName,
                     fullClassName,
                     entitySource);
    }

    private Optional<? extends Entity> getEntity(String id, Class<? extends Entity> clazz) {
        if (Strings.isEmpty(id)) {
            return Optional.empty();
        }
        if (clazz == null) {
            return Optional.empty();
        }
        return index.select(clazz).deliberatelyUnrouted().eq(Entity.ID, id).first();
    }
}
