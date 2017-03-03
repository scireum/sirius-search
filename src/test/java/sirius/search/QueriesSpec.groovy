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
import sirius.kernel.di.std.Part
import sirius.search.constraints.And
import sirius.search.constraints.FieldEqual
import sirius.search.constraints.NearSpan
import sirius.search.constraints.Or

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


}
