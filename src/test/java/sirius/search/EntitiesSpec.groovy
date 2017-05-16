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

    def "cascase delete works"() {
        given:
        def parent = new ParentEntity();
        parent.setName("Test");
        Index.create(parent);
        def child = new CascadingChildEntity();
        child.getParent().setValue(parent);
        Index.create(child);
        Index.blockThreadForUpdate();
        when:
        Index.delete(parent);
        and:
        waitForCompletion();
        then:
        Index.refreshOrNull(child) == null;

    }

    def "cascase delete works for a EntityRefList"() {
        given:
        def parent1 = new ParentEntity();
        def parent2 = new ParentEntity();
        parent1.setName("Test1");
        parent2.setName("Test2");
        Index.create(parent1);
        Index.create(parent2);
        def child = new CascadeManyChildEntity();
        child.getParents().addValue(parent1);
        child.getParents().addValue(parent2);
        Index.create(child);
        Index.blockThreadForUpdate();
        when:
        Index.delete(parent1);
        and:
        waitForCompletion();
        then:
        Index.refreshOrNull(child) == null;

    }

    def "set null on delete works"() {
        given:
        def parent = new ParentEntity();
        parent.setName("Test");
        Index.create(parent);
        def child = new SetNullChildEntity();
        child.getParent().setValue(parent);
        Index.create(child);
        Index.blockThreadForUpdate();
        when:
        Index.delete(parent);
        waitForCompletion();
        child = Index.refreshOrFail(child);
        then:
        !child.getParent().isFilled();
        and:
        child.getParentName() == null;
    }

    def "set null on delete works for a EntityRefList"() {
        given:
        def parent1 = new ParentEntity();
        def parent2 = new ParentEntity();
        parent1.setName("Test1");
        parent2.setName("Test2");
        Index.create(parent1);
        Index.create(parent2);
        def child = new SetNullManyChildEntity();
        child.getParents().addValue(parent1);
        child.getParents().addValue(parent2);
        Index.create(child);
        Index.blockThreadForUpdate();
        when:
        Index.delete(parent1);
        waitForCompletion();
        child = Index.refreshOrFail(child);
        then:
        child.getParents().getIds().size() == 1;
        and:
        child.getParents().getValues().size() == 1;
    }

    def "reject on delete works"() {
        given:
        def parent = new ParentEntity();
        parent.setName("Test");
        Index.create(parent);
        def child = new RejectChildEntity();
        child.getParent().setValue(parent);
        Index.create(child);
        Index.blockThreadForUpdate();
        when:
        Index.delete(parent);
        then:
        thrown(HandledException);
    }

    def "updating a ref field works"() {
        given:
        def parent = new ParentEntity();
        parent.setName("Test");
        Index.create(parent);
        def child = new SetNullChildEntity();
        child.getParent().setValue(parent);
        Index.create(child);
        Index.blockThreadForUpdate();
        when:
        parent.setName("Test1")
        Index.update(parent);
        waitForCompletion();
        then:
        Index.refreshIfPossible(child).getParentName() == "Test1"
    }

    def "abstract parent entity works"() {
        given:
        def entity = new ConcreteChildEntity();
        entity.setName("Test");
        entity.setSubname("Sub-Test");
        when:
        Index.create(entity);
        Index.blockThreadForUpdate();
        def refreshedEntity = Index.refreshOrFail(entity);
        def foundEntity = Index.find(AbstractParentEntity.class, entity.getId());
        def selectedEntity = Index.select(AbstractParentEntity.class).queryFirst();
        Index.delete(selectedEntity);
        Index.blockThreadForUpdate();
        def deletedEntity = Index.select(AbstractParentEntity.class).queryFirst();
        then:
        refreshedEntity.getName() == "Test"
        refreshedEntity.getSubname() == "Sub-Test"
        foundEntity != null
        foundEntity.getName() == "Test"
        foundEntity instanceof ConcreteChildEntity
        foundEntity.getSubname() == "Sub-Test"
        selectedEntity != null
        selectedEntity.getName() == "Test"
        selectedEntity instanceof ConcreteChildEntity
        selectedEntity.getSubname() == "Sub-Test"
        deletedEntity == null
    }

    def "test including and excluding from _all"() {
        when:
        def stringProp = new StringPropertiesEntity();
        stringProp.setSoloStringIncluded("soloStringIncluded");
        stringProp.setSoloStringExcluded("soloStringExcluded");
        stringProp.getStringListIncluded().addAll(Lists.newArrayList("stringListIncludedElement1", "stringListIncludedElement2"));
        stringProp.getStringListExcluded().addAll(Lists.newArrayList("stringListExcludedElement1", "stringListExcludedElement2"));
        Index.create(stringProp);
        Index.blockThreadForUpdate();
        then:
        List<StringPropertiesEntity> resultList = Index.select(StringPropertiesEntity.class).queryList();
        resultList.size() == 1;
        StringPropertiesEntity result = resultList.get(0);
        result.getSoloStringIncluded() == "soloStringIncluded";
        result.getSoloStringExcluded() == "soloStringExcluded";
        result.getStringListIncluded().size() == 2;
        result.getStringListIncluded().get(0) == "stringListIncludedElement1";
        result.getStringListIncluded().get(1) == "stringListIncludedElement2";
        result.getStringListExcluded().size() == 2;
        result.getStringListExcluded().get(0) == "stringListExcludedElement1";
        result.getStringListExcluded().get(1) == "stringListExcludedElement2";
        Index.select(StringPropertiesEntity.class).query("soloStringIncluded").count() == 1;
        Index.select(StringPropertiesEntity.class).query("soloStringExcluded").count() == 0;
        Index.select(StringPropertiesEntity.class).query("stringListIncludedElement1").count() == 1;
        Index.select(StringPropertiesEntity.class).query("stringListIncludedElement2").count() == 1;
        Index.select(StringPropertiesEntity.class).query("stringListExcludedElement1").count() == 0;
        Index.select(StringPropertiesEntity.class).query("stringListExcludedElement2").count() == 0;
    }

    @Part
    private static Tasks tasks;

    /**
     * Blocks until all async index updates (cascades, ref field updates ..) have been handled
     */
    def waitForCompletion() {
        Index.blockThreadForUpdate();
        def exec = tasks.findExecutor(Index.ASYNC_CATEGORY_INDEX_INTEGRITY);
        while (exec.activeCount > 0 && exec.queue.size() > 0) {
            Thread.sleep(500);
        }
        Index.blockThreadForUpdate();
    }

}
