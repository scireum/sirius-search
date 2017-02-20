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


}
