/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search

import org.elasticsearch.search.aggregations.bucket.nested.InternalNested
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part
import sirius.search.aggregation.bucket.Filter
import sirius.search.aggregation.bucket.Nested
import sirius.search.aggregation.bucket.Term
import sirius.search.aggregation.metrics.Max
import sirius.search.aggregation.metrics.Min

class AggregationsSpec extends BaseSpecification {

    @Part
    private static IndexAccess index

    def "test nested, min, max, filter and term aggregations"() {
        when:
        index.select(NestedObjectsListEntity.class).delete()
        def entityWithListOfObjects = new NestedObjectsListEntity()
        def nested1 = new POJO()
        def nested2 = new POJO()
        def nested3 = new POJO()
        nested1.setBoolVar(false)
        nested1.setStringVar("feature 1")
        nested1.setNumberVar(42)
        nested2.setBoolVar(true)
        nested2.setStringVar("feature 2")
        nested2.setNumberVar(-1)
        nested3.setBoolVar(true)
        nested3.setStringVar("feature 3")
        nested3.setNumberVar(5)
        entityWithListOfObjects.getNestedObjects().add(nested1)
        entityWithListOfObjects.getNestedObjects().add(nested2)
        entityWithListOfObjects.getNestedObjects().add(nested3)
        index.create(entityWithListOfObjects)
        index.blockThreadForUpdate()
        then:
        ResultList<NestedObjectsListEntity> resultList = index.select(NestedObjectsListEntity.class).limit(0)
                .addAggregation(Nested.on("nested1", NestedObjectsListEntity.NESTED_OBJECTS)
                .addSubAggregation(Filter.on(NestedObjectsListEntity.NESTED_OBJECTS + "." + POJO.BOOL_VAR, "filtered").withValue("true")
                .addSubAggregation(Term.on(NestedObjectsListEntity.NESTED_OBJECTS + "." + POJO.STRING_VAR, "terms"))))
                .addAggregation(Nested.on("nested2", NestedObjectsListEntity.NESTED_OBJECTS)
                .addSubAggregation(Max.on(NestedObjectsListEntity.NESTED_OBJECTS + "." + POJO.NUMBER_VAR, "max"))
                .addSubAggregation(Min.on(NestedObjectsListEntity.NESTED_OBJECTS + "." + POJO.NUMBER_VAR, "min"))).queryResultList()

        for (Terms.Bucket bucket : ((Terms) ((org.elasticsearch.search.aggregations.bucket.filter.Filter) ((InternalNested) resultList
                .getAggregations().get("nested1")).getAggregations().get("filtered")).getAggregations().get("terms")).getBuckets()) {
            (bucket.getKeyAsString() == "feature 2" || bucket.getKeyAsString() == "feature 3") && bucket.getKeyAsString() != "feature 1"
        }

        ((org.elasticsearch.search.aggregations.metrics.max.Max) ((InternalNested) resultList
                .getAggregations().get("nested2")).getAggregations().get("max")).getValue() == 42

        ((org.elasticsearch.search.aggregations.metrics.min.Min) ((InternalNested) resultList
                .getAggregations().get("nested2")).getAggregations().get("min")).getValue() == -1
    }
}
