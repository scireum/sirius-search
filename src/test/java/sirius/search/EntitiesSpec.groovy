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

class EntitiesSpec extends BaseSpecification {

    @Part
    private static IndexAccess index

    def "cascase delete works"() {
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

    def "cascase delete works for a EntityRefList"() {
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

    def "test including and excluding from _all"() {
        when:
        def stringProp = new StringPropertiesEntity()
        stringProp.setSoloStringIncluded("soloStringIncluded")
        stringProp.setSoloStringExcluded("soloStringExcluded")
        stringProp.getStringListIncluded().addAll(Lists.newArrayList("stringListIncludedElement1", "stringListIncludedElement2"))
        stringProp.getStringListExcluded().addAll(Lists.newArrayList("stringListExcludedElement1", "stringListExcludedElement2"))
        index.create(stringProp)
        index.blockThreadForUpdate()
        then:
        List<StringPropertiesEntity> resultList = index.select(StringPropertiesEntity.class).queryList()
        resultList.size() == 1
        StringPropertiesEntity result = resultList.get(0)
        result.getSoloStringIncluded() == "soloStringIncluded"
        result.getSoloStringExcluded() == "soloStringExcluded"
        result.getStringListIncluded().size() == 2
        result.getStringListIncluded().get(0) == "stringListIncludedElement1"
        result.getStringListIncluded().get(1) == "stringListIncludedElement2"
        result.getStringListExcluded().size() == 2
        result.getStringListExcluded().get(0) == "stringListExcludedElement1"
        result.getStringListExcluded().get(1) == "stringListExcludedElement2"
        index.select(StringPropertiesEntity.class).query("soloStringIncluded").count() == 1
        index.select(StringPropertiesEntity.class).query("soloStringExcluded").count() == 0
        index.select(StringPropertiesEntity.class).query("stringListIncludedElement1").count() == 1
        index.select(StringPropertiesEntity.class).query("stringListIncludedElement2").count() == 1
        index.select(StringPropertiesEntity.class).query("stringListExcludedElement1").count() == 0
        index.select(StringPropertiesEntity.class).query("stringListExcludedElement2").count() == 0
    }

    def "test list of objects property"() {
        when:
        index.select(EntityWithListOfNestedObjects.class).delete()
        waitForCompletion()
        def entityWithListOfObjects = new EntityWithListOfNestedObjects()
        def nested1 = new NestedObject()
        def nested2 = new NestedObject()
        nested1.setBoolVar(false)
        nested1.setStringVar("test")
        nested1.setNumberVar(42)
        nested2.setBoolVar(true)
        nested2.setStringVar("test - 2")
        nested2.setNumberVar(0)
        entityWithListOfObjects.getNestedObjects().add(nested1)
        entityWithListOfObjects.getNestedObjects().add(nested2)
        index.create(entityWithListOfObjects)
        index.blockThreadForUpdate()
        then:
        List<EntityWithListOfNestedObjects> resultList = index.select(EntityWithListOfNestedObjects.class).queryList()
        resultList.size() == 1
        resultList.get(0).getNestedObjects().size() == 2
        resultList.get(0).getNestedObjects().get(0).getBoolVar() == false
        resultList.get(0).getNestedObjects().get(0).getNumberVar() == 42
        resultList.get(0).getNestedObjects().get(0).getStringVar() == "test"
        resultList.get(0).getNestedObjects().get(1).getBoolVar() == true
        resultList.get(0).getNestedObjects().get(1).getNumberVar() == 0
        resultList.get(0).getNestedObjects().get(1).getStringVar() == "test - 2"
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
