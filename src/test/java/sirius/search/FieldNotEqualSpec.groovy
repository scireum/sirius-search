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
import sirius.search.constraints.FieldNotEqual
import sirius.search.entities.QueryEntity

class FieldNotEqualSpec extends BaseSpecification {
    
    @Part
    private static IndexAccess index
    
    def "FieldNotEqual with null values finds filled entities"() {
        given:
        QueryEntity e = new QueryEntity()
        e.setContent("fieldnotequal")
        when:
        index.update(e)
        and:
        index.blockThreadForUpdate()
        Optional result = index.select(QueryEntity.class)
                .notEq(QueryEntity.CONTENT, null)
                .first()
        then:
        result.isPresent()
    }

    def "FieldNotEqual with null values and id field finds entities"() {
        given:
        QueryEntity e = new QueryEntity()
        e.setContent("fieldnotequal")
        when:
        index.update(e)
        and:
        index.blockThreadForUpdate()
        Optional result = index.select(QueryEntity.class)
                .notEq(QueryEntity.CONTENT, null)
                .first()
        then:
        result.isPresent()
    }

    def "FieldNotEqual with null values and ignoreNull finds empty content"() {
        given:
        QueryEntity e = new QueryEntity()
        e.setContent("")
        when:
        index.update(e)
        and:
        index.blockThreadForUpdate()
        Optional result = index.select(QueryEntity.class)
                .where(FieldNotEqual.on(QueryEntity.CONTENT, null).ignoreNull())
                .first()
        then:
        result.isPresent()
    }

    def "FieldNotEqual with null values and ignoreNull finds id"() {
        given:
        QueryEntity e = new QueryEntity()
        when:
        index.update(e)
        and:
        index.blockThreadForUpdate()
        Optional result = index.select(QueryEntity.class)
                .where(FieldNotEqual.on(QueryEntity.CONTENT, null).ignoreNull())
                .first()
        then:
        result.isPresent()
    }
}
