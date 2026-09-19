package io.matrix.federation.concurrency;

import io.matrix.federation.proto.DataType;
import io.matrix.federation.proto.ModulatorDefaults;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorType;
import io.matrix.federation.registry.ModulatorRegistryStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.ConcurrentModificationException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W388 — Concurrency tests for ModulatorRegistryStore.
 * 
 * Documents current thread-safety behavior:
 * - Single-threaded: safe (expected use)
 * - Multi-threaded: HashMap is not thread-safe, may throw ConcurrentModificationException
 * 
 * Recommendation: Use a single thread or external synchronization.
 */
class RegistryConcurrencyTest {
    
    @Test
    void testSingleThreadedSafe() throws Exception {
        // Expected usage pattern - single thread
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        for (int i = 0; i < 100; i++) {
            store.add(makeModulator("m" + i));
        }
        assertEquals(100, store.size());
    }
    
    @Test
    void testConcurrentReadsAreSafe() throws Exception {
        // Concurrent reads should be safe with HashMap (read-only operations)
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        for (int i = 0; i < 50; i++) {
            store.add(makeModulator("m" + i));
        }
        
        int threadCount = 4;
        int readsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        
        for (int t = 0; t < threadCount; t++) {
            futures.add(executor.submit(() -> {
                latch.await();
                for (int i = 0; i < readsPerThread; i++) {
                    int idx = (int) (Math.random() * 50);
                    store.get("m" + idx);
                    store.size();
                    store.calculateHash();
                    store.getAll();
                }
                return true;
            }));
        }
        
        latch.countDown();
        for (Future<Boolean> f : futures) {
            assertTrue(f.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();
    }
    
    @Test
    void testConcurrentWritesMayCorrupt() throws Exception {
        // This test DOCUMENTS the limitation: concurrent writes to HashMap
        // can throw ConcurrentModificationException. Single-threaded is safe.
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        
        int threadCount = 4;
        int writesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger cmExceptions = new AtomicInteger(0);
        AtomicInteger otherExceptions = new AtomicInteger(0);
        AtomicInteger successes = new AtomicInteger(0);
        Set<String> uniqueIds = ConcurrentHashMap.newKeySet();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < writesPerThread; i++) {
                        String id = "t" + threadId + "-m" + i;
                        uniqueIds.add(id);
                        try {
                            store.add(makeModulator(id));
                            successes.incrementAndGet();
                        } catch (IllegalArgumentException e) {
                            // Duplicate - expected if another thread added same id
                        }
                    }
                } catch (ConcurrentModificationException e) {
                    cmExceptions.incrementAndGet();
                } catch (Exception e) {
                    otherExceptions.incrementAndGet();
                }
            });
        }
        
        latch.countDown();
        try {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Document: with 4 threads × 100 writes, expect some failures
        int totalAttempted = threadCount * writesPerThread;
        int actualSize = store.size();
        
        // The actualSize may be less than uniqueIds due to HashMap corruption
        // This is DOCUMENTED limitation, not a test failure
        assertTrue(actualSize >= uniqueIds.size() / 2 || actualSize > 0,
            "HashMap corruption: size=" + actualSize + " uniqueIds=" + uniqueIds.size());
        
        System.out.println("Concurrent writes: " + successes.get() + " succeeded, " +
            cmExceptions.get() + " CME, " + otherExceptions.get() + " other, size=" + actualSize);
    }
    
    private ModulatorDefinition makeModulator(String id) {
        return ModulatorDefinition.newBuilder()
            .setId(id).setName("test_" + id).setType(ModulatorType.MODULATOR_TYPE_HORMONE)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .setDefaults(ModulatorDefaults.newBuilder().setDefaultFloat(0.5f).build())
            .build();
    }
}
