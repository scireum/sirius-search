/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.search.SearchHit;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;

/**
 * Used to re-index all document from one index to another.
 * <p>
 * The main reason for this would be mapping changes.
 */
class ReIndexTask implements Runnable {

    private Schema schema;
    private final String newPrefix;
    private int counter;
    private BulkRequestBuilder bulk;

    @Part
    private static IndexAccess index;

    ReIndexTask(Schema schema, String newPrefix) {
        this.schema = schema;
        this.newPrefix = newPrefix;
    }

    @Override
    public void run() {
        IndexAccess.LOG.INFO("Creating Mappings: " + newPrefix);
        for (String result : schema.createMappings(newPrefix)) {
            IndexAccess.LOG.INFO(result);
        }
        IndexAccess.LOG.INFO("Re-Indexing to " + newPrefix);
        executeAndReCreateBulk();
        try {
            for (EntityDescriptor ed : schema.descriptorTable.values()) {
                IndexAccess.LOG.INFO("Re-Indexing: " + newPrefix + ed.getIndex() + "." + ed.getType());
                reIndexEntitiesOfDescriptor(ed);
            }
        } finally {
            executeAndReCreateBulk();
            IndexAccess.LOG.INFO("Re-Index is COMPLETED! You may now start breathing again...");
        }
    }

    private void reIndexEntitiesOfDescriptor(EntityDescriptor ed) {
        try {
            SearchRequestBuilder srb =
                    index.getClient().prepareSearch(index.getIndexName(ed.getIndex())).setTypes(ed.getType());
            srb.setSearchType(SearchType.SCAN);
            // Limit to 10 per shard
            srb.setSize(10);
            srb.setScroll(org.elasticsearch.common.unit.TimeValue.timeValueSeconds(5 * 60));
            SearchResponse searchResponse = srb.execute().actionGet();
            while (TaskContext.get().isActive()) {
                Watch w = Watch.start();
                searchResponse = index.getClient()
                                      .prepareSearchScroll(searchResponse.getScrollId())
                                      .setScroll(org.elasticsearch.common.unit.TimeValue.timeValueSeconds(5 * 60))
                                      .execute()
                                      .actionGet();
                for (SearchHit hit : searchResponse.getHits()) {
                    bulk.add(index.getClient()
                                  .prepareIndex(newPrefix + ed.getIndex(), ed.getType())
                                  .setId(hit.getId())
                                  .setSource(hit.source())
                                  .request());
                    counter++;
                    if (counter > 1000) {
                        executeAndReCreateBulk();
                    }
                }
                //Break condition: No hits are returned
                if (searchResponse.getHits().hits().length == 0) {
                    return;
                }
            }
        } catch (Throwable t) {
            throw Exceptions.handle(IndexAccess.LOG, t);
        }
    }

    private void executeAndReCreateBulk() {
        if (counter > 0) {
            IndexAccess.LOG.INFO("Executing bulk...");
            BulkResponse res = bulk.execute().actionGet();
            if (res.hasFailures()) {
                for (BulkItemResponse itemRes : res.getItems()) {
                    if (itemRes.isFailed()) {
                        IndexAccess.LOG.SEVERE("Re-Indexing failed: " + itemRes.getFailureMessage());
                    }
                }
            }
        }
        bulk = index.getClient().prepareBulk();
        counter = 0;
    }
}