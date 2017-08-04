/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search

import com.google.common.collect.Lists
import sirius.kernel.BaseSpecification
import sirius.kernel.async.Tasks
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException
import sirius.search.entities.*

class EntitiesSpec extends BaseSpecification {

    @Part
    private static IndexAccess index

    def "cascade delete works"() {
        given:
        def parent = new ParentEntity()
        parent.setName("Test")
        index.create(parent)
        def child = new CascadingChildEntity()
        child.getParent().setValue(parent)
        index.create(child)
        index.blockThreadForUpdate()
        when:
        index.delete(parent)
        and:
        waitForCompletion()
        then:
        index.refreshOrNull(child) == null

    }

    def "cascade delete works for a EntityRefList"() {
        given:
        def parent1 = new ParentEntity()
        def parent2 = new ParentEntity()
        parent1.setName("Test1")
        parent2.setName("Test2")
        index.create(parent1)
        index.create(parent2)
        def child = new CascadeManyChildEntity()
        child.getParents().addValue(parent1)
        child.getParents().addValue(parent2)
        index.create(child)
        index.blockThreadForUpdate()
        when:
        index.delete(parent1)
        and:
        waitForCompletion()
        then:
        index.refreshOrNull(child) == null

    }

    def "set null on delete works"() {
        given:
        def parent = new ParentEntity()
        parent.setName("Test")
        index.create(parent)
        def child = new SetNullChildEntity()
        child.getParent().setValue(parent)
        index.create(child)
        index.blockThreadForUpdate()
        when:
        index.delete(parent)
        waitForCompletion()
        child = index.refreshOrFail(child)
        then:
        !child.getParent().isFilled()
        and:
        child.getParentName() == null
    }

    def "set null on delete works for a EntityRefList"() {
        given:
        def parent1 = new ParentEntity()
        def parent2 = new ParentEntity()
        parent1.setName("Test1")
        parent2.setName("Test2")
        index.create(parent1)
        index.create(parent2)
        def child = new SetNullManyChildEntity()
        child.getParents().addValue(parent1)
        child.getParents().addValue(parent2)
        index.create(child)
        index.blockThreadForUpdate()
        when:
        index.delete(parent1)
        waitForCompletion()
        child = index.refreshOrFail(child)
        then:
        child.getParents().getIds().size() == 1
        and:
        child.getParents().getValues().size() == 1
    }

    def "reject on delete works"() {
        given:
        def parent = new ParentEntity()
        parent.setName("Test")
        index.create(parent)
        def child = new RejectChildEntity()
        child.getParent().setValue(parent)
        index.create(child)
        index.blockThreadForUpdate()
        when:
        index.delete(parent)
        then:
        thrown(HandledException)
    }

    def "updating a ref field works"() {
        given:
        def parent = new ParentEntity()
        parent.setName("Test")
        index.create(parent)
        def child = new SetNullChildEntity()
        child.getParent().setValue(parent)
        index.create(child)
        index.blockThreadForUpdate()
        when:
        parent.setName("Test1")
        index.update(parent)
        waitForCompletion()
        then:
        index.refreshIfPossible(child).getParentName() == "Test1"
    }

    def "test including/excluding from _all"() {
        when:
        def entity = new IncludeExcludeEntity()
        entity.setStringIncluded("stringIncluded")
        entity.setStringExcluded("stringExcluded")
        entity.getStringListIncluded().addAll(Lists.newArrayList("stringListIncludedElement1", "stringListIncludedElement2"))
        entity.getStringListExcluded().addAll(Lists.newArrayList("stringListExcludedElement1", "stringListExcludedElement2"))
        entity.getStringMapIncluded().put("stringMapIncludedKey", "stringMapIncludedValue")
        entity.getStringMapExcluded().put("stringMapExcludedKey", "stringMapExcludedValue")
        entity.setIntIncluded(44)
        entity.setIntExcluded(43)
        index.create(entity)
        index.blockThreadForUpdate()
        then:
        index.select(IncludeExcludeEntity.class).query("stringIncluded").count() == 1
        index.select(IncludeExcludeEntity.class).query("stringExcluded").count() == 0
        index.select(IncludeExcludeEntity.class).query("stringListIncludedElement1").count() == 1
        index.select(IncludeExcludeEntity.class).query("stringListIncludedElement2").count() == 1
        index.select(IncludeExcludeEntity.class).query("stringListExcludedElement1").count() == 0
        index.select(IncludeExcludeEntity.class).query("stringListExcludedElement2").count() == 0
        index.select(IncludeExcludeEntity.class).query("stringMapIncludedKey").count() == 1
        index.select(IncludeExcludeEntity.class).query("stringMapIncludedValue").count() == 1
        index.select(IncludeExcludeEntity.class).query("stringMapExcludedKey").count() == 0
        index.select(IncludeExcludeEntity.class).query("stringMapExcludedValue").count() == 0
        index.select(IncludeExcludeEntity.class).query("44").count() == 1
        index.select(IncludeExcludeEntity.class).query("43").count() == 0
    }

    @Part
    private static Tasks tasks

    /**
     * Blocks until all async index updates (cascades, ref field updates ..) have been handled
     */
    def waitForCompletion() {
        index.blockThreadForUpdate()
        def exec = tasks.findExecutor(IndexAccess.ASYNC_CATEGORY_INDEX_INTEGRITY)
        while (exec.activeCount > 0 && exec.queue.size() > 0) {
            Thread.sleep(500)
        }
        index.blockThreadForUpdate()
    }
}
