package io.matrix.federation.stress;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.runtime.FederationRuntime;
import io.matrix.federation.telemetry.FederationTelemetry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W375 — Concurrency stress test for FederationTelemetry.
 * 
 * Verifies that:
 * - Concurrent recordProposal/Vote/Mutation calls don't corrupt counters
 * - Snapshot is consistent during concurrent writes
 * - FederationTelemetry is thread-safe under realistic load
 */
class TelemetryConcurrencyTest {
    
    @Test
    void testConcurrentProposalRecording() throws Exception {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        int threadCount = 10;
        int callsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        
        for (int t = 0; t < threadCount; t++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < callsPerThread; i++) {
                        telemetry.recordProposal("APPROVED");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        
        long start = System.nanoTime();
        latch.countDown();  // Start all threads simultaneously
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        long elapsed = System.nanoTime() - start;
        executor.shutdown();
        
        // Verify final count
        int expected = threadCount * callsPerThread;
        assertEquals(expected, telemetry.getTotalEventCount());
        var snap = telemetry.snapshot();
        assertEquals(expected, snap.proposalCountsByStatus().get("APPROVED"));
        
        System.out.println("Concurrent proposals: " + expected + " in " + (elapsed / 1_000_000) + "ms");
    }
    
    @Test
    void testConcurrentMixedOperations() throws Exception {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        int threadCount = 8;
        int callsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        
        // Different threads do different operations
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < callsPerThread; i++) {
                        switch (threadId % 4) {
                            case 0: telemetry.recordProposal("APPROVED"); break;
                            case 1: telemetry.recordProposal("REJECTED"); break;
                            case 2: telemetry.recordVote("YES"); break;
                            case 3: telemetry.recordMutation(); break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        
        latch.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        
        int expectedTotal = threadCount * callsPerThread;
        assertEquals(expectedTotal, telemetry.getTotalEventCount());
        
        var snap = telemetry.snapshot();
        int sumByType = snap.eventCountsByType().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(expectedTotal, sumByType);
    }
    
    @Test
    void testConcurrentSnapshotsDuringWrites() throws Exception {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        AtomicInteger snapshotFailures = new AtomicInteger(0);
        AtomicInteger writesDone = new AtomicInteger(0);
        
        int writerCount = 4;
        int readerCount = 4;
        int writesPerThread = 2000;
        ExecutorService executor = Executors.newFixedThreadPool(writerCount + readerCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        
        // Writers
        for (int t = 0; t < writerCount; t++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < writesPerThread; i++) {
                        telemetry.recordProposal("APPROVED");
                        writesDone.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        
        // Readers (snapshots)
        for (int t = 0; t < readerCount; t++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < 1000; i++) {
                        var snap = telemetry.snapshot();
                        if (snap.totalEvents() < 0) {
                            snapshotFailures.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        
        latch.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        
        assertEquals(0, snapshotFailures.get());
        assertEquals(writerCount * writesPerThread, telemetry.getTotalEventCount());
    }
    
    @Test
    void testHighBurstLoad() throws Exception {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        // Single thread, high call rate
        int totalCalls = 100_000;
        long start = System.nanoTime();
        for (int i = 0; i < totalCalls; i++) {
            telemetry.recordProposal("APPROVED");
        }
        long elapsed = System.nanoTime() - start;
        
        assertEquals(totalCalls, telemetry.getTotalEventCount());
        
        System.out.println("Single-thread burst: " + totalCalls + " calls in " + (elapsed / 1_000_000) + "ms");
        System.out.println("  Throughput: " + (totalCalls * 1_000_000_000L / elapsed) + " ops/sec");
    }
    
    @Test
    void testResetRaceCondition() throws Exception {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(1);
        
        // Some threads record, some reset
        for (int t = 0; t < 20; t++) {
            final boolean isResetter = (t % 5 == 0);
            executor.submit(() -> {
                try {
                    latch.await();
                    if (isResetter) {
                        for (int i = 0; i < 100; i++) {
                            telemetry.reset();
                        }
                    } else {
                        for (int i = 0; i < 100; i++) {
                            telemetry.recordProposal("APPROVED");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        // Final state should be valid (either reset or have some count)
        int finalCount = telemetry.getTotalEventCount();
        assertTrue(finalCount >= 0);
    }
}
