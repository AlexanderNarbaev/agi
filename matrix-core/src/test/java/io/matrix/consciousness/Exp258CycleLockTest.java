package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 258 — CycleLock EXP.
 *
 * <p>5 threads writing to a counter through CycleLock. Verify
 * no lost updates.
 */
@Tag("exp")
class Exp258CycleLockTest {

    @Test
    void fiveThreadsIncrementCounter() throws Exception {
        var lock = new CycleLock();
        var counter = new AtomicInteger(0);
        int threadCount = 5;
        int incrementsPerThread = 100;
        var ready = new CountDownLatch(threadCount);
        var start = new CountDownLatch(1);

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    ready.countDown();
                    start.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        lock.writeLock();
                        try {
                            counter.incrementAndGet();
                        } finally {
                            lock.writeUnlock();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }
        ready.await();
        start.countDown();
        for (var t : threads) t.join();
        assertThat(counter.get()).isEqualTo(threadCount * incrementsPerThread);
        System.out.printf("[LOCK-EXP] counter=%d (expected %d)%n",
                counter.get(), threadCount * incrementsPerThread);
    }
}
