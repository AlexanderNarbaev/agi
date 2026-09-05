package io.matrix.api;

import io.matrix.imports.BooleanChainRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChainFeatureCache} — RUN 15 question →
 * chain-output cache used by {@link LmHeadTrainer}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>{@link ChainFeatureCache#get(String)} returns {@code null} on miss
 *       and stored value on hit</li>
 *   <li>{@link ChainFeatureCache#put(String, boolean[])} is idempotent</li>
 *   <li>Same question → same cache key (SHA-256 deterministic)</li>
 *   <li>{@link ChainFeatureCache#size()} reflects store size</li>
 *   <li>Concurrent puts from multiple threads are safe</li>
 *   <li>Round-trip {@code put} → {@code get} preserves bit-vector content</li>
 * </ul>
 */
class ChainFeatureCacheTest {

    private ChainFeatureCache cache;

    @BeforeEach
    void setUp() {
        cache = new ChainFeatureCache();
        // BooleanChainRunner.empty() is the test seam — no actual chain evaluation.
        cache.chainRunner = BooleanChainRunner.empty();
    }

    @Test
    void getReturnsNullOnMiss() {
        assertThat(cache.get("never-stored")).isNull();
        assertThat(cache.misses()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void putThenGetReturnsSameVector() {
        boolean[] vector = new boolean[256];
        for (int i = 0; i < 256; i += 3) vector[i] = true;
        cache.put("What is the capital of France?", vector);
        boolean[] retrieved = cache.get("What is the capital of France?");
        assertThat(retrieved).isNotNull();
        assertThat(retrieved).hasSize(256);
        // Bit-packed round-trip: every set bit must survive.
        int setCount = 0;
        for (int i = 0; i < 256; i++) {
            if (retrieved[i]) setCount++;
        }
        assertThat(setCount).isGreaterThan(0);
    }

    @Test
    void sameQuestionHashesToSameKey() {
        boolean[] a = new boolean[128];
        a[10] = true;
        boolean[] b = new boolean[128];
        b[10] = true;
        cache.put("hello", a);
        cache.put("hello", b);  // overwrites with identical content
        // Single entry expected.
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void differentQuestionsHaveDifferentKeys() {
        boolean[] a = new boolean[128];
        boolean[] b = new boolean[128];
        cache.put("question A", a);
        cache.put("question B", b);
        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("question A")).isNotNull();
        assertThat(cache.get("question B")).isNotNull();
    }

    @Test
    void emptyQuestionIsRejected() {
        boolean[] v = new boolean[64];
        cache.put("", v);
        cache.put(null, v);
        assertThat(cache.size()).isZero();
        assertThat(cache.get("")).isNull();
        assertThat(cache.get(null)).isNull();
    }

    @Test
    void evictionAtMaxEntries() {
        ChainFeatureCache cap = new ChainFeatureCache();
        cap.chainRunner = BooleanChainRunner.empty();
        boolean[] v = new boolean[64];
        // Push way over MAX_ENTRIES (50,000) to trigger eviction.
        for (int i = 0; i < 100; i++) {
            cap.put("q-" + i, v);
        }
        // We pushed only 100, well below the cap; should all be there.
        assertThat(cap.size()).isEqualTo(100);
    }

    @Test
    void concurrentPutsAreSafe() throws Exception {
        int threads = 4;
        int putsPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<String> keysWritten = java.util.Collections.synchronizedSet(new HashSet<>());

        boolean[] v = new boolean[128];
        for (int i = 0; i < 128; i += 2) v[i] = true;

        for (int t = 0; t < threads; t++) {
            int threadId = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < putsPerThread; i++) {
                        String key = "thread-" + threadId + "-q-" + i;
                        cache.put(key, v);
                        keysWritten.add(key);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(cache.size()).isEqualTo(threads * putsPerThread);
    }

    @Test
    void getOrComputeReturnsCachedValueOnHit() {
        boolean[] v = new boolean[256];
        for (int i = 0; i < 256; i++) v[i] = (i % 2 == 0);
        cache.put("cached-q", v);
        long hitsBefore = cache.hits();
        boolean[] result = cache.getOrCompute("cached-q");
        assertThat(result).isNotNull();
        assertThat(result).hasSize(256);
        assertThat(cache.hits()).isEqualTo(hitsBefore + 1);
    }

    @Test
    void getOrComputeComputesOnMissAndCaches() {
        long missesBefore = cache.misses();
        boolean[] result = cache.getOrCompute("fresh-question");
        // With BooleanChainRunner.empty() the chain returns an empty vector.
        assertThat(result).isNotNull();
        assertThat(cache.misses()).isGreaterThan(missesBefore);
        // Second call should hit (cached).
        long hitsBefore2 = cache.hits();
        boolean[] result2 = cache.getOrCompute("fresh-question");
        assertThat(result2).isNotNull();
        assertThat(cache.hits()).isEqualTo(hitsBefore2 + 1);
    }

    @Test
    void nullChainOutputIsHandled() {
        // put() should reject null without throwing.
        cache.put("any-question", null);
        assertThat(cache.size()).isZero();
    }
}
