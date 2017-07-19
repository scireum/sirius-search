/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.controller;

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsResponse;
import org.elasticsearch.action.admin.indices.stats.IndexStats;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse;
import org.elasticsearch.cluster.health.ClusterIndexHealth;
import org.elasticsearch.cluster.health.ClusterShardHealth;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.search.IndexAccess;
import sirius.web.controller.BasicController;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Routed("/system/index")
    public void index(WebContext ctx) {
        ClusterHealthResponse clusterHealthResponse =
                index.getClient().admin().cluster().prepareHealth().get();
        ClusterStatsResponse clusterStatsResponse =
                index.getClient().admin().cluster().prepareClusterStats().get();
        NodesInfoResponse nodesInfoResponse = index.getClient().admin().cluster().prepareNodesInfo().get();
        NodesStatsResponse nodesStatsResponse =
                index.getClient().admin().cluster().prepareNodesStats().get();
        ClusterStateResponse clusterStateResponse =
                index.getClient().admin().cluster().prepareState().get();
        IndicesStatsResponse indicesStatsResponse =
                index.getClient().admin().indices().prepareStats().all().get();


        String clusterState = getClusterState(clusterStateResponse);

        Map<String, String> nodeStatsMap = getNodesStats(nodesStatsResponse);
        Map<String, String> nodeInfoMap = getNodesInfos(nodesInfoResponse);


        Map<String, String> indexMetaData = getIndicesMetadata(clusterStateResponse);
        Map<String, String> indexStatusMap = getIndicesStatus(indicesStatsResponse);

        Map<String, Map<Integer, List<ShardRouting>>> indexShardRoutingsMap =
                getIndexShardRoutings(clusterHealthResponse, clusterStateResponse);

        NumberFormatter nf = new NumberFormatter();

        ctx.respondWith()
           .template("view/index-health.html",
                     clusterHealthResponse,
                     clusterStatsResponse,
                     nodesInfoResponse,
                     nodesStatsResponse,
                     indicesStatsResponse,

                     clusterState,

                     indexShardRoutingsMap,
                     nodeStatsMap,
                     indexMetaData,
                     indexStatusMap,
                     nodeInfoMap,

                     nf);
    }

    public static class NumberFormatter {
        private DecimalFormat formatter;
        public NumberFormatter() {
            formatter = new DecimalFormat();
            formatter.setGroupingUsed(true);
            formatter.setMaximumFractionDigits(2);
        }
        public String formatLong(long number) {
            return formatter.format(number);
        }
    }

    private Map<String, String> getNodesInfos(NodesInfoResponse nodesInfoResponse) {
        Map<String, String> nodeInfoMap = new HashMap<>();
        for (NodeInfo nodeInfo : nodesInfoResponse.getNodes()) {
            try (XContentBuilder builder = XContentFactory.jsonBuilder()){
                builder.humanReadable(true).prettyPrint();
                builder.startObject();
                nodeInfo.getSettings().toXContent(builder, ToXContent.EMPTY_PARAMS);
                nodeInfo.getOs().toXContent(builder, ToXContent.EMPTY_PARAMS);
                nodeInfo.getProcess().toXContent(builder, ToXContent.EMPTY_PARAMS);
                nodeInfo.getJvm().toXContent(builder, ToXContent.EMPTY_PARAMS);
                nodeInfo.getThreadPool().toXContent(builder, ToXContent.EMPTY_PARAMS);
                nodeInfo.getTransport().toXContent(builder, ToXContent.EMPTY_PARAMS);
                nodeInfo.getPlugins().toXContent(builder, ToXContent.EMPTY_PARAMS);
                nodeInfo.getIngest().toXContent(builder, ToXContent.EMPTY_PARAMS);
                builder.endObject();

                nodeInfoMap.put(nodeInfo.getNode().getName(), builder.string());
            } catch (IOException e) {
                Exceptions.handle(e);
            }
        }
        return nodeInfoMap;
    }

    private String getClusterState(ClusterStateResponse clusterStateResponse) {
        String state = "";
        try (XContentBuilder clusterStateBuilder = XContentFactory.jsonBuilder()){
            clusterStateBuilder.humanReadable(true).prettyPrint().startObject();
            clusterStateResponse.getState().toXContent(clusterStateBuilder, ToXContent.EMPTY_PARAMS);
            clusterStateBuilder.endObject();
            state = clusterStateBuilder.string();
        } catch (IOException e) {
            Exceptions.handle(e);
        }
        return state;
    }

    private Map<String, String> getNodesStats(NodesStatsResponse nodesStatsResponse) {
        Map<String, String> nodeStatsMap = new HashMap<>();
        for (NodeStats stat : nodesStatsResponse.getNodes()) {
            try (XContentBuilder nodesStatsBuilder = XContentFactory.jsonBuilder()) {
                nodesStatsBuilder.humanReadable(true).prettyPrint().startObject();
                stat.toXContent(nodesStatsBuilder, ToXContent.EMPTY_PARAMS);
                nodesStatsBuilder.endObject();
                nodeStatsMap.put(stat.getNode().getName(), nodesStatsBuilder.string());
            } catch (IOException e) {
                Exceptions.handle(e);
            }
        }
        return nodeStatsMap;
    }

    private Map<String, String> getIndicesMetadata(ClusterStateResponse clusterStateResponse) {
        Map<String, String> metaDataMap = new HashMap<>();
        for (ObjectObjectCursor<String, IndexMetaData> metadata : clusterStateResponse.getState()
                                                                                      .getMetaData()
                                                                                      .getIndices()) {
            try (XContentBuilder metaDataBuilder = XContentFactory.jsonBuilder()) {
                metaDataBuilder.humanReadable(true).prettyPrint().startObject();
                clusterStateResponse.getState()
                                    .getMetaData()
                                    .index(metadata.key)
                                    .toXContent(metaDataBuilder, ToXContent.EMPTY_PARAMS);
                metaDataBuilder.endObject();

                metaDataMap.put(metadata.key, metaDataBuilder.string());
            } catch (IOException e) {
                Exceptions.handle(e);
            }
        }
        return metaDataMap;
    }

    private Map<String, String> getIndicesStatus(IndicesStatsResponse indicesStatsResponse) {
        Map<String, String> indexStatusMap = new HashMap<>();
        for (Map.Entry<String, IndexStats> indexStat : indicesStatsResponse.getIndices().entrySet()) {
            try (XContentBuilder indexStatsBuilder = XContentFactory.jsonBuilder()){
                indexStatsBuilder.humanReadable(true).prettyPrint().startObject();
                indicesStatsResponse.getIndex(indexStat.getKey())
                                    .getTotal()
                                    .toXContent(indexStatsBuilder, ToXContent.EMPTY_PARAMS);
                indexStatsBuilder.endObject();
                indexStatusMap.put(indexStat.getKey(), indexStatsBuilder.string());
            } catch (IOException e) {
                Exceptions.handle(e);
            }
        }
        return indexStatusMap;
    }

    private Map<String, Map<Integer, List<ShardRouting>>> getIndexShardRoutings(ClusterHealthResponse clusterHealthResponse,
                                                                                ClusterStateResponse clusterStateResponse) {
        Map<String, Map<Integer, List<ShardRouting>>> indexShardRoutings = new HashMap<>();
        try (XContentBuilder xContentBuilder = XContentFactory.jsonBuilder()){
            for (Map.Entry<String, ClusterIndexHealth> indexHealth : clusterHealthResponse.getIndices().entrySet()) {

                String indexName = indexHealth.getValue().getIndex();
                indexShardRoutings.put(indexName, getShardRoutings(clusterStateResponse, indexHealth));
            }
        } catch (IOException e) {
            Exceptions.handle(e);
        }
        return indexShardRoutings;
    }


    private Map<Integer, List<ShardRouting>> getShardRoutings(ClusterStateResponse clusterStateResponse, Map.Entry<String,
            ClusterIndexHealth> index) {
        Map<Integer, List<ShardRouting>> shardRoutings = new HashMap<>();
        for (Map.Entry<Integer, ClusterShardHealth> shard : index.getValue().getShards().entrySet()) {

            List<ShardRouting> shardRoutingList = clusterStateResponse.getState()
                                                                      .getRoutingTable()
                                                                      .index(index.getValue().getIndex())
                                                                      .getShards()
                                                                      .get(shard.getKey())
                                                                      .getShards();
            shardRoutings.put(shard.getKey(), shardRoutingList);
        }
        return shardRoutings;
    }
}
