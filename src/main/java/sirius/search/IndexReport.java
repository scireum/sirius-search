/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.metrics.MetricProvider;
import sirius.kernel.health.metrics.MetricState;
import sirius.kernel.health.metrics.MetricsCollector;

/**
 * Provides usage metrics for elasticsearch and the query system.
 */
@Register
public class IndexReport implements MetricProvider {

    @Part
    private IndexAccess index;

    @Override
    public void gather(MetricsCollector collector) {
        if (!index.isReady()) {
            return;
        }
        ClusterHealthResponse res = index.getClient().admin().cluster().prepareHealth().execute().actionGet();
        collector.metric("ES-Nodes", res.getNumberOfNodes(), null, asMetricState(res.getStatus()));
        collector.metric("ES-InitializingShards",
                         res.getInitializingShards(),
                         null,
                         res.getInitializingShards() > 0 ? MetricState.YELLOW : MetricState.GRAY);
        collector.metric("ES-RelocatingShards",
                         res.getRelocatingShards(),
                         null,
                         res.getRelocatingShards() > 0 ? MetricState.YELLOW : MetricState.GRAY);
        collector.metric("ES-UnassignedShards",
                         res.getUnassignedShards(),
                         null,
                         res.getUnassignedShards() > 0 ? MetricState.RED : MetricState.GRAY);
        collector.metric("index-delay-line", "ES-DelayLine", IndexAccess.oneSecondDelayLine.size(), null);
        collector.differentialMetric("index-blocks", "index-blocks", "ES-DelayBlocks", index.blocks.getCount(), "/min");
        collector.differentialMetric("index-delays", "index-delays", "ES-Delays", index.delays.getCount(), "/min");
        collector.differentialMetric("index-locking-errors",
                                     "index-locking-errors",
                                     "ES-OptimisticLock-Errors",
                                     index.optimisticLockErrors.getCount(),
                                     "/min");
        collector.metric("index-queryDuration", "ES-QueryDuration", index.queryDuration.getAndClear(), "ms");
        collector.differentialMetric("index-queries",
                                     "index-queries",
                                     "ES-Queries",
                                     index.queryDuration.getCount(),
                                     "/min");
    }

    private MetricState asMetricState(ClusterHealthStatus status) {
        if (status == ClusterHealthStatus.GREEN) {
            return MetricState.GRAY;
        } else if (status == ClusterHealthStatus.YELLOW) {
            return MetricState.YELLOW;
        } else {
            return MetricState.RED;
        }
    }
}
