/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.locks

import sirius.kernel.BaseSpecification
import sirius.kernel.di.Injector
import sirius.kernel.health.HandledException
import sirius.search.IndexAccess
import sirius.search.OptimisticLockException
import spock.lang.Shared

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class LockManagerSpec extends BaseSpecification {

    @Shared
    def Lock LOCK = Lock.named("L");
    def CriticalSection SECTION_TEST = LOCK.forSection("TEST");
    def CriticalSection SECTION_XXX = LOCK.forSection("XXX");

    def "lock succeeds on available lock"() {
        given:
        def lm = new LockManager();
        lm.index = Mock(IndexAccess);
        when:
        def result = lm.tryLock(SECTION_TEST, 1, TimeUnit.MILLISECONDS);
        then:
        1 * lm.index.find(LockInfo.class, "L") >> null;
        2 * lm.index.tryUpdate(_) >> new LockInfo();
        result == true
    }

    def "tryLock fails on already acquired lock"() {
        given:
        def lm = new LockManager();
        def li = new LockInfo();
        li.setLockedSince(LocalDateTime.now());
        lm.index = Mock(IndexAccess);
        when:
        def result = lm.tryLock(SECTION_TEST, 1, TimeUnit.MILLISECONDS);
        then:
        1 * lm.index.find(LockInfo.class, "L") >> li;
        0 * lm.index.tryUpdate(_) >> li;
        result == false
    }

    def "tryLock fails on contended lock"() {
        given:
        def lm = new LockManager();
        lm.index = Mock(IndexAccess);
        when:
        def result = lm.tryLock(SECTION_TEST, 1, TimeUnit.MILLISECONDS);
        then:
        1 * lm.index.find(LockInfo.class, "L") >> null;
        1 * lm.index.tryUpdate(_) >> { LockInfo l ->
            l.id == "L"
            l.currentOwnerSection == "TEST"
            throw new OptimisticLockException(null, l);
        }
        result == false
    }

    def "tryLock succeeds with in-memory index"() {
        given:
        def lm = Injector.context().getPart(LockManager.class);
        when:
        def result = lm.tryLock(SECTION_TEST, 1, TimeUnit.MILLISECONDS);
        if (result) {
            lm.unlock(SECTION_TEST);
        }
        then:
        result == true
    }

    def "tryLock fails on duplicate lock with in-memory index"() {
        given:
        def lm = Injector.context().getPart(LockManager.class);
        when:
        def result = lm.tryLock(SECTION_TEST, 1, TimeUnit.MILLISECONDS);
        def result1 = null;
        if (result) {
            try {
                result1 = lm.tryLock(SECTION_XXX, 1, TimeUnit.MILLISECONDS);
            } finally {
                lm.unlock(SECTION_TEST);
            }
        }
        then:
        result == true
        result1 == false
    }

    def "unlock fails with in-memory index when using wrong section"() {
        given:
        def lm = Injector.context().getPart(LockManager.class);
        when:
        def result = lm.tryLock(SECTION_TEST, 1, TimeUnit.MILLISECONDS);
        if (result) {
            try {
                lm.unlock(SECTION_XXX)
            } finally {
                lm.unlock(SECTION_TEST);
            }
        }
        then:
        thrown(HandledException)
    }

}
