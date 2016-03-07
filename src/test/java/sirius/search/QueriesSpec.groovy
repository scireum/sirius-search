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
    private static IndexAccess idx;

    def "robust query can filter on fields"() {
        given:
        ParentEntity e = new ParentEntity();
        e.setName("Query");
        when:
        e = idx.update(e);
        and:
        idx.blockThreadForUpdate();
        then:
        idx.select(ParentEntity.class).query("id:" + e.getId()).count() == 1
    }

    def "robust query can filter on empty fields"() {
        given:
        ParentEntity e = new ParentEntity();
        when:
        e = idx.update(e);
        and:
        idx.blockThreadForUpdate();
        then:
        idx.select(ParentEntity.class).query("name:- id:"+e.getId()).count() == 1
    }


}
