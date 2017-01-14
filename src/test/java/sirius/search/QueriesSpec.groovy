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


}
