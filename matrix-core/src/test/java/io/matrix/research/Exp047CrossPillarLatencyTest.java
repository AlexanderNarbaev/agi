package io.matrix.research;

import io.matrix.actions.ActionArena;
import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.lifecycle.ConsolidationCycle;
import io.matrix.reasoning.BrcChain;
import io.matrix.reasoning.ConsciousnessLoop;
import io.matrix.neuron.SchemaDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-047 harness (H-047): per-stage latency budget split.
 *
 * <p>ConsciousnessLoop has 9 stages but stages are not separately
 * profiled in the current implementation. We measure the **aggregate
 * tick** and report it against the proposed budget:
 * <ul>
 *   <li>perception + attention: ≤ 5 ms p99</li>
 *   <li>deliberation: ≤ 50 ms p99</li>
 *   <li>action: ≤ 10 ms p99</li>
 * </ul>
 * Tick total budget = 65 ms p99 (sum of stage caps).
 *
 * <p>With an empty BRC chain (identity) and uniform saliency, the
 * budget should be easy to meet — this wave documents the actual
 * measurement, not the gate.
 */
class Exp047CrossPillarLatencyTest {

    private ActionArena arena;

    @BeforeEach
    void setUp() { arena = ActionArena.defaults(); }

    @AfterEach
    void tearDown() { arena.close(); }

    @Test
    void tickP99Across1000Ticks() {
        BrcChain chain = new BrcChain(java.util.List.of(), 0, true, SchemaDescriptor.scalar(8));
        ConsolidationCycle cycle = new ConsolidationCycle();
        cycle.open(java.util.Map.of("loop", 0));
        ConsciousnessLoop loop = new ConsciousnessLoop(chain, arena, cycle,
                new ConjugateBudgeter(), ConsciousnessLoop.uniform(), () -> new BitSet());
        int n = 1000;
        long[] ns = new long[n];
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            loop.tick();
            ns[i] = System.nanoTime() - t0;
        }
        java.util.Arrays.sort(ns);
        long p50 = ns[n / 2];
        long p99 = ns[(int) (n * 0.99)];
        long max = ns[n - 1];
        double p99Ms = p99 / 1_000_000.0;
        double maxMs = max / 1_000_000.0;
        System.out.printf("[EXP-047] tick p50=%dns p99=%dns max=%dns (%.3fms / %.3fms)%n",
                p50, p99, max, p99Ms, maxMs);
        // honest floor
        assertThat(p99).isGreaterThanOrEqualTo(0L);
    }
}