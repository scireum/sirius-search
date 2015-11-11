/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search

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
        child.getParent().setValue(parent)
        Index.create(child);
        Index.blockThreadForUpdate();
        when:
        Index.delete(parent);
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
        child.getParent().setValue(parent)
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

    def "reject on delete works"() {
        given:
        def parent = new ParentEntity();
        parent.setName("Test");
        Index.create(parent);
        def child = new RejectChildEntity();
        child.getParent().setValue(parent)
        Index.create(child);
        Index.blockThreadForUpdate();
        when:
        Index.delete(parent);
        then:
        thrown(HandledException)
    }

    def "updating a ref field works"() {
        given:
        def parent = new ParentEntity();
        parent.setName("Test");
        Index.create(parent);
        def child = new SetNullChildEntity();
        child.getParent().setValue(parent)
        Index.create(child);
        Index.blockThreadForUpdate();
        when:
        parent.setName("Test1")
        Index.update(parent);
        waitForCompletion();
        then:
        Index.refreshIfPossible(child).getParentName() == "Test1"
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
