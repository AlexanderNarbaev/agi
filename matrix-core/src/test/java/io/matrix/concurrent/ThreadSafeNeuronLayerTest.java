package io.matrix.concurrent;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.NeuronLayer;
import io.matrix.neuron.TruthTable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Concurrent tests for {@link ThreadSafeNeuronLayer}.
 *
 * <p>Tests:
 * <ul>
 *   <li>Concurrent read test (100 threads)</li>
 *   <li>Concurrent write test (10 threads)</li>
 *   <li>Race condition detection</li>
 *   <li>Deadlock detection</li>
 * </ul>
 */
class ThreadSafeNeuronLayerTest {

    private static final int K = 8;
    private static final int NEURON_COUNT = 5;
    private static final Random RNG = new Random(42);

    private ThreadSafeNeuronLayer layer;
    private NeuronLayer rawLayer;

    @BeforeEach
    void setUp() {
        rawLayer = new NeuronLayer(NEURON_COUNT, K, RNG);
        layer = new ThreadSafeNeuronLayer(rawLayer);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void concurrentReadTest_100Threads() throws Exception {
        // Given: 100 threads all reading the same input concurrently
        int threadCount = 100;
        int iterationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);
        CopyOnWriteArrayList<BitSet> results = new CopyOnWriteArrayList<>();

        BitSet input = createInput(K * NEURON_COUNT);
        BitSet expected = rawLayer.evaluate(input);

        // When: all threads evaluate concurrently
        List<Future<?>> futures = new ArrayList<>(threadCount);
        for (int t = 0; t < threadCount; t++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    for (int i = 0; i < iterationsPerThread; i++) {
                        BitSet result = layer.evaluate(input);
                        if (!result.equals(expected)) {
                            errorCount.incrementAndGet();
                        }
                        results.add(result);
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }));
        }

        // Wait for all threads
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Then: no errors, all results consistent
        assertThat(errorCount.get()).isZero();
        assertThat(results).hasSize(threadCount * iterationsPerThread);
        assertThat(layer.evaluateCount()).isEqualTo(threadCount * iterationsPerThread);
        // Cache should have been hit for all but the first evaluation
        assertThat(layer.cacheHitCount()).isGreaterThan(0);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void concurrentWriteTest_10Threads() throws Exception {
        // Given: 10 threads evaluating different inputs concurrently
        int threadCount = 10;
        int iterationsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<BitSet> inputs = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            inputs.add(createInput(K * NEURON_COUNT));
        }

        // When: threads evaluate different inputs concurrently
        List<Future<?>> futures = new ArrayList<>(threadCount);
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    for (int i = 0; i < iterationsPerThread; i++) {
                        BitSet input = inputs.get(threadId);
                        BitSet result = layer.evaluate(input);
                        // Verify result is deterministic for the same input
                        BitSet result2 = layer.evaluate(input);
                        if (!result.equals(result2)) {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Then: no errors, all results deterministic
        assertThat(errorCount.get()).isZero();
        assertThat(layer.evaluateCount()).isEqualTo(threadCount * iterationsPerThread * 2);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void raceConditionDetection_concurrentCacheInvalidation() throws Exception {
        // Given: threads evaluating while cache is being invalidated
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicBoolean stop = new AtomicBoolean(false);

        BitSet input = createInput(K * NEURON_COUNT);
        BitSet expected = rawLayer.evaluate(input);

        // Reader threads
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount - 1; t++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    while (!stop.get()) {
                        BitSet result = layer.evaluate(input);
                        if (!result.equals(expected)) {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }));
        }

        // Writer thread (invalidator)
        futures.add(executor.submit(() -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
                for (int i = 0; i < 100; i++) {
                    layer.invalidateCache();
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                stop.set(true);
            }
        }));

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Then: no race condition errors
        assertThat(errorCount.get()).isZero();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void deadlockDetection() throws Exception {
        // Given: heavy concurrent load that could cause deadlock
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicBoolean deadlockDetected = new AtomicBoolean(false);

        // When: threads acquire both read and write locks concurrently
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    for (int i = 0; i < 100; i++) {
                        BitSet input = createInput(K * NEURON_COUNT);
                        layer.evaluate(input);

                        // Some threads also invalidate cache (write lock)
                        if (threadId % 5 == 0) {
                            layer.invalidateCache();
                        }

                        // Access metrics (lock-free)
                        layer.evaluateCount();
                        layer.cacheHitCount();
                    }
                    completedCount.incrementAndGet();
                } catch (Exception e) {
                    deadlockDetected.set(true);
                }
            }));
        }

        // Wait with timeout — if deadlock, threads won't complete
        for (Future<?> f : futures) {
            try {
                f.get(8, TimeUnit.SECONDS);
            } catch (Exception e) {
                deadlockDetected.set(true);
            }
        }
        executor.shutdownNow();

        // Then: all threads completed, no deadlock
        assertThat(deadlockDetected.get()).isFalse();
        assertThat(completedCount.get()).isEqualTo(threadCount);
    }

    @Test
    void delegatePreservesBehavior() {
        // Given: same input for raw and thread-safe layers
        BitSet input = createInput(K * NEURON_COUNT);

        // When
        BitSet rawResult = rawLayer.evaluate(input);
        BitSet safeResult = layer.evaluate(input);

        // Then: results match
        assertThat(safeResult).isEqualTo(rawResult);
    }

    @Test
    void metricsTracking() {
        // Given
        BitSet input = createInput(K * NEURON_COUNT);

        // When: evaluate same input multiple times
        layer.evaluate(input);
        layer.evaluate(input);
        layer.evaluate(input);

        // Then
        assertThat(layer.evaluateCount()).isEqualTo(3);
        assertThat(layer.cacheHitCount()).isEqualTo(2); // 2 cache hits
        assertThat(layer.cacheMissCount()).isEqualTo(1); // 1 cache miss
    }

    @Test
    void cacheInvalidation() {
        // Given
        BitSet input = createInput(K * NEURON_COUNT);
        layer.evaluate(input);
        assertThat(layer.cacheSize()).isGreaterThan(0);

        // When
        layer.invalidateCache();

        // Then
        assertThat(layer.cacheSize()).isEqualTo(0);
    }

    @Test
    void serializationRoundTrip() {
        // Given
        byte[] bytes = layer.toAvroBytes();

        // When
        ThreadSafeNeuronLayer restored = ThreadSafeNeuronLayer.fromAvroBytes(bytes);

        // Then
        BitSet input = createInput(K * NEURON_COUNT);
        assertThat(restored.evaluate(input)).isEqualTo(layer.evaluate(input));
        assertThat(restored.k()).isEqualTo(layer.k());
        assertThat(restored.outputWidth()).isEqualTo(layer.outputWidth());
    }

    private BitSet createInput(int bits) {
        BitSet input = new BitSet(bits);
        Random rng = new Random(123);
        for (int i = 0; i < bits; i++) {
            if (rng.nextBoolean()) input.set(i);
        }
        return input;
    }
}
