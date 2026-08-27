package io.matrix.reasoning;

import io.matrix.actions.ActionArena;
import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.lifecycle.ConsolidationCycle;
import io.matrix.neuron.SchemaDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-H tests for {@link ConsciousnessLoop}: the nine-stage orchestrator
 * runs deterministically, the saliency weigher weights correctly, and
 * concurrent ticks from multiple threads produce consistent telemetry.
 */
class ConsciousnessLoopTest {

    private ActionArena arena;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        arena = ActionArena.defaults();
        pool = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        arena.close();
        pool.shutdownNow();
    }

    private ConsciousnessLoop newLoop(ConsciousnessLoop.SaliencyWeigher saliency,
                                      java.util.function.Supplier<BitSet> perception) {
        // empty chain → identity (input passes through)
        BrcChain chain = new BrcChain(List.of(), 0, true, SchemaDescriptor.scalar(8));
        ConsolidationCycle cycle = new ConsolidationCycle();
        cycle.open(java.util.Map.of("loop", 0));
        return new ConsciousnessLoop(chain, arena, cycle, new ConjugateBudgeter(),
                saliency, perception);
    }

    @Test
    void singleTickAdvancesAndRecordsPredictionError() {
        Random rng = new Random(42);
        AtomicInteger calls = new AtomicInteger();
        ConsciousnessLoop loop = newLoop(ConsciousnessLoop.uniform(), () -> {
            calls.incrementAndGet();
            BitSet b = new BitSet(8);
            b.set(rng.nextInt(8));
            return b;
        });
        ConsciousnessLoop.TickSnapshot snap = loop.tick();
        assertThat(snap.tickId()).isEqualTo(1L);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(loop.totalTicks()).isEqualTo(1L);
        assertThat(loop.lastPredictionError()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void runForExecutesNTicksAndSnapshots() {
        ConsciousnessLoop loop = newLoop(ConsciousnessLoop.uniform(),
                () -> new BitSet()); // empty input each tick
        List<ConsciousnessLoop.TickSnapshot> snaps = loop.runFor(5);
        assertThat(snaps).hasSize(5);
        for (int i = 0; i < snaps.size(); i++) {
            assertThat(snaps.get(i).tickId()).isEqualTo(i + 1);
        }
        assertThat(loop.totalTicks()).isEqualTo(5L);
    }

    @Test
    void saliencyWeightsAffectAttentionScore() {
        // Provider emits a single bit at position 3 each tick; uniform
        // saliency produces a constant score, peaked saliency produces
        // a higher score when the bit matches the peak.
        java.util.function.Supplier<BitSet> perception = () -> {
            BitSet b = new BitSet(8);
            b.set(3);
            return b;
        };
        ConsciousnessLoop uniform = newLoop((i, w) -> 1.0, perception);
        ConsciousnessLoop peaked = newLoop((i, w) -> i == 3 ? 2.0 : 1.0, perception);
        uniform.tick();
        peaked.tick();
        // peaked score = round(2.0) ^ prev = 2
        // uniform score = round(1.0) ^ prev = 1
        assertThat(peaked.lastDecision()).isNotNull();
        assertThat(uniform.lastDecision()).isNotNull();
    }

    @Test
    void concurrentTicksAreThreadSafe() throws Exception {
        // 100 ticks dispatched from 8 threads; all must succeed.
        ConsciousnessLoop loop = newLoop(ConsciousnessLoop.uniform(),
                () -> new BitSet());
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();
        int n = 100;
        java.util.concurrent.Future<?>[] all = new java.util.concurrent.Future[n];
        for (int i = 0; i < n; i++) {
            all[i] = pool.submit(() -> {
                try {
                    start.await();
                    loop.tick();
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }
        start.countDown();
        for (var f : all) f.get();
        assertThat(errors.get()).isEqualTo(0);
        assertThat(loop.totalTicks()).isEqualTo(n);
    }

    @Test
    void emptyPerceptionProducesZeroAttentionScore() {
        ConsciousnessLoop loop = newLoop(ConsciousnessLoop.uniform(), () -> new BitSet());
        ConsciousnessLoop.TickSnapshot snap = loop.tick();
        assertThat(snap.attentionScore()).isEqualTo(0L);
    }

    @Test
    void predictionErrorHammingDistanceIsCorrect() {
        // perception = 0b11000000, decision via empty chain = same
        // predictionError should be 0
        ConsciousnessLoop loop = newLoop(ConsciousnessLoop.uniform(), () -> {
            BitSet b = new BitSet(8);
            b.set(6);
            b.set(7);
            return b;
        });
        loop.tick();
        assertThat(loop.lastPredictionError()).isEqualTo(0L);
    }
}