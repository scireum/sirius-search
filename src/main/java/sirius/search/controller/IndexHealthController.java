/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.controller;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsResponse;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.search.IndexAccess;
import sirius.web.controller.BasicController;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;

/**
 * Provides various health/state/performance informations about the cluster.
 */
@Register(classes = Controller.class)
public class IndexHealthController extends BasicController {

    /**
     * Describes the permission required to view the system state.
     */
    public static final String PERMISSION_SYSTEM_STATE = "permission-system-state";

    @Part
    private IndexAccess index;

    @Permission(PERMISSION_SYSTEM_STATE)
    @Routed("/system/index")
    public void index(WebContext ctx) {
        ClusterHealthResponse clusterHealthResponse = index.getClient().admin().cluster().prepareHealth().get();
        ClusterStatsResponse clusterStatsResponse = index.getClient().admin().cluster().prepareClusterStats().get();

        ctx.respondWith().template("view/index-health.html", clusterHealthResponse, clusterStatsResponse);
    }
}
