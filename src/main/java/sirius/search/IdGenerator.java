/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Provides a simple sequence generator using the optimistic locking of ElasticSearch.
 * <p>
 * Note that this method is not intended for high frequency use as at least two requests against ES are necessary.
 * </p>
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/11
 */
@Register(classes = IdGenerator.class)
public class IdGenerator {

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Returns the next globally unique number for the given sequence.
     * <p>
     * If the sequence doesn't exist, it will be created and 1 will be returned.
     * </p>
     *
     * @param sequence the sequence to use
     * @return a globally unique number (within this sequence)
     */
    public int getNextId(String sequence) {
        try {
            if (lock.tryLock(10, TimeUnit.SECONDS)) {
                try {
                    int retries = 5;
                    while (retries-- > 0) {
                        try {
                            Sequence seq = Index.find(Sequence.class, sequence);
                            if (seq == null) {
                                seq = new Sequence();
                                seq.setId(sequence);
                                seq.setNext(1);
                                seq = Index.tryUpdate(seq);
                            }
                            int result = seq.getNext();
                            seq.setNext(result + 1);
                            Index.tryUpdate(seq);
                            return result;
                        } catch (OptimisticLockException e) {
                            Exceptions.ignore(e);
                        }
                    }
                    throw Exceptions.handle()
                                    .to(Index.LOG)
                                    .withSystemErrorMessage(
                                            "Unable to generate a unique ID for sequence: %s after 5 attempts",
                                            sequence)
                                    .handle();
                } finally {
                    lock.unlock();
                }
            } else {
                throw Exceptions.handle()
                                .to(Index.LOG)
                                .withSystemErrorMessage(
                                        "Unable to to lock critical section of ID generator for sequence: %s",
                                        sequence)
                                .handle();
            }
        } catch (HandledException e) {
            throw e;
        } catch (Throwable e) {
            throw Exceptions.handle()
                            .to(Index.LOG)
                            .error(e)
                            .withSystemErrorMessage("Unable to generate a unique ID for sequence: %s - %s (%s)",
                                                    sequence)
                            .handle();
        }
    }
}
