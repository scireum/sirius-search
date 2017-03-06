/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search

import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part
import sirius.search.constraints.FieldEqual
import sirius.search.constraints.NearSpan

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
        entities.findAll {x -> x.version == -1L}.collect().size()
    }

}