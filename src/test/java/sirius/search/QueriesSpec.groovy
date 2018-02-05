/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search

import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder
import sirius.kernel.BaseSpecification
import sirius.kernel.annotations.SetupOnce
import sirius.kernel.di.std.Part
import sirius.search.constraints.And
import sirius.search.constraints.FieldEqual
import sirius.search.constraints.NearSpan
import sirius.search.constraints.Or
import sirius.search.entities.CustomAnalyzerPropertyEntity
import sirius.search.entities.ParentEntity
import sirius.search.entities.QueryEntity
import sirius.web.controller.Page

class QueriesSpec extends BaseSpecification {

    @Part
    private static IndexAccess index

    def "robust query can filter on fields"() {
        given:
        ParentEntity e = new ParentEntity()
        e.setName("Query")
        when:
        e = index.update(e)
        and:
        index.blockThreadForUpdate()
        then:
        index.select(ParentEntity.class).query("id:" + e.getId()).count() == 1
    }

    def "robust query can filter on empty fields"() {
        given:
        ParentEntity e = new ParentEntity()
        when:
        e = index.update(e)
        and:
        index.blockThreadForUpdate()
        then:
        index.select(ParentEntity.class).query("name:- id:" + e.getId()).count() == 1
        index.select(ParentEntity.class).query("-name:- id:" + e.getId()).count() == 0
    }
    
    def "robust query can produce OR query"() {
        given:
        ParentEntity e1 = new ParentEntity()
        e1.setName("one")
        ParentEntity e2 = new ParentEntity()
        e2.setName("two")
        when:
        e1 = index.update(e1)
        e2 = index.update(e2)
        and:
        index.blockThreadForUpdate()
        then:
        index.select(ParentEntity.class).query("one OR two").count() == 2
    }

    def "robust query can produce complex nested query"() {
        given:
        ParentEntity e1 = new ParentEntity()
        e1.setName("entity one")
        ParentEntity e2 = new ParentEntity()
        e2.setName("entity two")
        ParentEntity e3 = new ParentEntity()
        e3.setName("entity")
        when:
        e1 = index.update(e1)
        e2 = index.update(e2)
        e3 = index.update(e3)
        and:
        index.blockThreadForUpdate()
        then:
        index.select(ParentEntity.class).query("(entity AND one) OR (entity AND two)").count() == 2
    }

    def "custom analyzer are created at startup and work"() {
        given:
        CustomAnalyzerPropertyEntity e = new CustomAnalyzerPropertyEntity()
        e.setPrefixesContent("thisisalongword")
        when:
        e = index.update(e)
        and:
        index.blockThreadForUpdate()
        then:
        index.select(CustomAnalyzerPropertyEntity).eq(CustomAnalyzerPropertyEntity.PREFIXES_CONTENT, "this").count() == 1
        index.select(CustomAnalyzerPropertyEntity).eq(CustomAnalyzerPropertyEntity.PREFIXES_CONTENT, "thisi").count() == 1
        index.select(CustomAnalyzerPropertyEntity).eq(CustomAnalyzerPropertyEntity.PREFIXES_CONTENT, "thisis").count() == 1
        index.select(CustomAnalyzerPropertyEntity).eq(CustomAnalyzerPropertyEntity.PREFIXES_CONTENT, "thisisword").count() == 0
    }

    def "near span query uses applied slop value"() {
        given:
        QueryEntity e = new QueryEntity()
        e.setContent("value1 value2 value3 value4 value5")
        when:
        index.update(e)
        and:
        index.blockThreadForUpdate()
        then:
        index.select(QueryEntity.class).where(NearSpan.of(
                FieldEqual.on(QueryEntity.CONTENT, "value1"),
                FieldEqual.on(QueryEntity.CONTENT, "value3")).slop(3))
                .queryList().get(0).getId() == e.getId()

        index.select(QueryEntity.class).where(NearSpan.of(
                FieldEqual.on(QueryEntity.CONTENT, "value1"),
                FieldEqual.on(QueryEntity.CONTENT, "value5")).slop(2))
                .queryList().isEmpty()

    }

    def "near span query uses the set in-order value"() {
        index.select(QueryEntity.class).where(NearSpan.of(
                FieldEqual.on(QueryEntity.CONTENT, "value3"),
                FieldEqual.on(QueryEntity.CONTENT, "value1")).slop(3).inOrder())
                .queryList().isEmpty()

        !index.select(QueryEntity.class).where(NearSpan.of(
                FieldEqual.on(QueryEntity.CONTENT, "value3"),
                FieldEqual.on(QueryEntity.CONTENT, "value1")).slop(3))
                .queryList().isEmpty()
    }

    def "bulk update works"() {
        given:
        index.select(QueryEntity.class).delete()
        List<QueryEntity> entities = new ArrayList<>()
        for (int i = 0; i < 500; i++) {
            QueryEntity e = new QueryEntity()
            e.setContent("bulk")
            entities.add(e)
        }
        when:
        entities = index.updateBulk(entities)
        and:
        index.blockThreadForUpdate(4)
        then:
        index.select(QueryEntity.class).eq(QueryEntity.CONTENT, "bulk").count() == 500
        and:
        noExceptionThrown()
    }

    def queryPageSetup() {
        index.select(QueryEntity.class).delete()
        List<QueryEntity> entities = new ArrayList<>()
        for (int i = 0; i < 500; i++) {
            QueryEntity e = new QueryEntity()
            e.setContent("querypage")
            entities.add(e)
        }
        index.updateBulk(entities)
        index.blockThreadForUpdate(4)
    }

    @SetupOnce("queryPageSetup")
    def "queryPage counts number of items correctly"() {
        when:
        Page<QueryEntity> page
        and:
        page = index.select(QueryEntity.class).eq(QueryEntity.CONTENT, "querypage").withPageSize(pageSize).page(start).queryPage()
        then:
        page.getItems().size() == count
        page.hasMore() == hasMore
        page.getTotal() == total
        where:
        pageSize | start | count | total | hasMore
        0        | 1     | 0     | 500   | true
        25       | 1     | 25    | 500   | true
        500      | 1     | 500   | 500   | false
        25       | 480   | 21    | 500   | false
        200      | 301   | 200   | 500   | false
        200      | 300   | 200   | 500   | true
        25       | 500   | 1     | 500   | false
    }

    def "queryPage counts number of items correctly, even if failed"() {
        when:
        Page<QueryEntity> page
        and:
        page = index.select(QueryEntity.class).page(0).fail().queryPage()
        then:
        page.getItems().size() == 0
        page.hasMore() == false
        page.getTotal() == 0
    }

    def "bulk update combined with version conflict"() {
        given:
        List<QueryEntity> entities = new ArrayList<>()
        QueryEntity e = new QueryEntity()
        QueryEntity e2 = new QueryEntity()
        entities.add(e)
        entities.add(e2)
        when:
        entities = index.updateBulk(entities)
        and:
        index.blockThreadForUpdate()
        and:
        e = index.select(QueryEntity.class).eq(QueryEntity.ID, entities.get(0).id).queryFirst()
        e2 = index.select(QueryEntity.class).eq(QueryEntity.ID, entities.get(0).id).queryFirst()
        index.update(e)
        and:
        index.blockThreadForUpdate()
        and:
        entities.clear()
        entities.add(e2)
        index.updateBulk(entities)
        then:
        entities.findAll { x -> x.version == -1L }.collect().size() == 1
    }

    def "fine grained scoring works"() {
        given:
        QueryEntity e = new QueryEntity()
        e.setRanking(500L)
        e.setContent("value1 value2")
        QueryEntity e2 = new QueryEntity()
        e2.setRanking(1000L)
        e2.setContent("value1")
        when:
        index.update(e)
        index.update(e2)
        and:
        index.blockThreadForUpdate()
        then:
        index.select(QueryEntity.class).addScoreFunction(new FieldValueFactorFunctionBuilder(QueryEntity.RANKING)
                .factor(1)
                .modifier(FieldValueFactorFunction.Modifier.NONE))
                .where(Or.on(FieldEqual.on(QueryEntity.RANKING, 500L), FieldEqual.on(QueryEntity.RANKING, 1000L)))
                .queryList().size() == 2

        index.select(QueryEntity.class).addScoreFunction(new FieldValueFactorFunctionBuilder(QueryEntity.RANKING)
                .factor(1)
                .modifier(FieldValueFactorFunction.Modifier.NONE))
                .where(And.on(Or.on(FieldEqual.on(QueryEntity.CONTENT, "value1"), FieldEqual.on(QueryEntity.CONTENT, "value2")), Or.on(FieldEqual.on(QueryEntity.RANKING, 500L), FieldEqual.on(QueryEntity.RANKING, 1000L))))
                .queryList().get(0).getRanking() == 1000L

        index.select(QueryEntity.class)
                .where(And.on(Or.on(FieldEqual.on(QueryEntity.CONTENT, "value1"), FieldEqual.on(QueryEntity.CONTENT, "value2")), Or.on(FieldEqual.on(QueryEntity.RANKING, 500L), FieldEqual.on(QueryEntity.RANKING, 1000L))))
                .queryList().get(0).getRanking() == 500L


    }

    def "forced query fails if tokenizer only produces empty tokens"() {
        setup:
        QueryEntity e = new QueryEntity()
        e.setContent("forcedQuery")
        index.update(e)
        index.blockThreadForUpdate()
        when:
        Query qry = index.select(QueryEntity.class)
        qry.eq(QueryEntity.CONTENT, "forcedQuery").query("§#%", Query.DEFAULT_FIELD, Query.&defaultTokenizer, false, true)
        then:
        qry.forceFail
        and:
        !qry.exists()
    }

}
