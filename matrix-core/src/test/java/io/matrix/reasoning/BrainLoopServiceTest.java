package io.matrix.reasoning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BrainLoopService} — the production wiring of
 * {@link ConsciousnessLoop} used by {@code OpenAIChatResource#chatCompletions}.
 *
 * <p>These tests cover:
 * <ul>
 *   <li>Tick advances the loop counter and produces a {@link BrainLoopService.Trace}</li>
 *   <li>The trace contains all nine canonical phase names</li>
 *   <li>{@link BrainLoopService#tick(BitSet)} is deterministic for the
 *       same input (same attention score / prediction error)</li>
 *   <li>Concurrent ticks from multiple threads are safe</li>
 *   <li>The trace header value is non-empty and parseable</li>
 *   <li>{@link BrainLoopService#lastTrace()} reflects the most recent tick</li>
 * </ul>
 */
class BrainLoopServiceTest {

    private BrainLoopService service;

    @BeforeEach
    void setUp() {
        service = new BrainLoopService();
    }

    @Test
    void tickAdvancesCounterAndProducesTrace() {
        BrainLoopService.Trace trace = service.tick(new BitSet(8));
        assertThat(trace).isNotNull();
        assertThat(trace.tickId()).isEqualTo(1L);
        assertThat(service.totalTicks()).isEqualTo(1L);
    }

    @Test
    void traceContainsAllNineCanonicalPhases() {
        BrainLoopService.Trace trace = service.tick(new BitSet());
        // Exactly nine "->"-separated phase names.
        String[] parts = trace.phasePath().split("->");
        assertThat(parts).hasSize(BrainLoopService.PHASES.length);
        assertThat(parts).containsExactly(
                "perception", "attention", "deliberation", "gate", "action",
                "consolidation", "subconscious", "prediction-error", "attention-update");
    }

    @Test
    void tickIsDeterministicForSameInput() {
        BitSet input = new BitSet(8);
        input.set(0);
        input.set(3);
        BrainLoopService.Trace a = service.tick(input);
        // Fresh service to start from a clean lastAttended state.
        BrainLoopService fresh = new BrainLoopService();
        BrainLoopService.Trace b = fresh.tick(input);
        assertThat(a.attentionScore()).isEqualTo(b.attentionScore());
        assertThat(a.predictionError()).isEqualTo(b.predictionError());
        assertThat(a.actionsSubmitted()).isEqualTo(b.actionsSubmitted());
    }

    @Test
    void differentInputsProduceDifferentAttentionScores() {
        BitSet sparse = new BitSet(64);
        sparse.set(0);
        BitSet dense = new BitSet(64);
        for (int i = 0; i < 32; i++) dense.set(i);

        BrainLoopService.Trace a = new BrainLoopService().tick(sparse);
        BrainLoopService.Trace b = new BrainLoopService().tick(dense);
        // Attention score is bit-count weighted by saliency. Sparse (1 bit)
        // < dense (32 bits) under uniform saliency.
        assertThat(a.attentionScore()).isLessThan(b.attentionScore());
    }

    @Test
    void tickIdIsMonotonicAcrossSequentialCalls() {
        BrainLoopService svc = new BrainLoopService();
        BrainLoopService.Trace t1 = svc.tick(new BitSet());
        BrainLoopService.Trace t2 = svc.tick(new BitSet());
        BrainLoopService.Trace t3 = svc.tick(new BitSet());
        assertThat(t1.tickId()).isEqualTo(1L);
        assertThat(t2.tickId()).isEqualTo(2L);
        assertThat(t3.tickId()).isEqualTo(3L);
        assertThat(svc.totalTicks()).isEqualTo(3L);
    }

    @Test
    void lastTraceReflectsMostRecentTick() {
        BrainLoopService svc = new BrainLoopService();
        svc.tick(new BitSet());
        long firstTickId = svc.lastTrace().tickId();
        svc.tick(new BitSet());
        long secondTickId = svc.lastTrace().tickId();
        assertThat(secondTickId).isGreaterThan(firstTickId);
    }

    @Test
    void headerValueIsParseableAndContainsAllFields() {
        BrainLoopService.Trace trace = service.tick(new BitSet());
        String header = trace.headerValue();
        // Expect key=value pairs joined by spaces.
        assertThat(header).startsWith("tick=");
        assertThat(header).contains("phases=");
        assertThat(header).contains("attention=");
        assertThat(header).contains("predErr=");
        assertThat(header).contains("actions=");
    }

    @Test
    void concurrentTicksAreSafe() throws Exception {
        int threads = 8;
        int ticksPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();
        Set<Long> tickIds = java.util.Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < ticksPerThread; j++) {
                        BrainLoopService.Trace t = service.tick(new BitSet());
                        tickIds.add(t.tickId());
                    }
                } catch (Throwable th) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(errors.get()).isZero();
        // total ticks = threads * ticksPerThread
        assertThat(service.totalTicks()).isEqualTo((long) threads * ticksPerThread);
        // Every tick id is unique across all threads.
        assertThat(tickIds).hasSize(threads * ticksPerThread);
    }

    @Test
    void nullInputRejected() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class, () -> service.tick(null)))
                .isNotNull();
    }
}
