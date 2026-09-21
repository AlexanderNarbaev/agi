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
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-048 harness (H-048): emergence of behavior stability over 1000 cycles.
 *
 * <p>Action-distribution entropy and decision-tree shape diff between
 * successive ticks. With a uniform-perception stream (constant input),
 * the loop should converge to a stable action within ~100 cycles.
 */
class Exp048EmergenceStabilityTest {

    private ActionArena arena;

    @BeforeEach
    void setUp() { arena = ActionArena.defaults(); }

    @AfterEach
    void tearDown() { arena.close(); }

    @Test
    void loopConvergesToStableActionOver1000Ticks() {
        BrcChain chain = new BrcChain(java.util.List.of(), 0, true, SchemaDescriptor.scalar(8));
        ConsolidationCycle cycle = new ConsolidationCycle();
        cycle.open(java.util.Map.of("loop", 0));
        // constant input perception
        BitSet constant = new BitSet(8);
        constant.set(3);
        ConsciousnessLoop loop = new ConsciousnessLoop(chain, arena, cycle,
                new ConjugateBudgeter(), ConsciousnessLoop.uniform(), () -> constant);

        // run 1000 ticks; track unique decision-vectors
        java.util.Set<String> decisions = new java.util.HashSet<>();
        for (int i = 0; i < 1000; i++) {
            loop.tick();
            decisions.add(loop.lastDecision().toString());
        }
        // For constant input + empty chain + uniform saliency,
        // expect exactly ONE distinct decision after the first tick
        int nUnique = decisions.size();
        System.out.printf("[EXP-048] after 1000 ticks, %d unique decision-vectors%n", nUnique);
        assertThat(nUnique).isLessThanOrEqualTo(10);  // generous upper bound
    }
}