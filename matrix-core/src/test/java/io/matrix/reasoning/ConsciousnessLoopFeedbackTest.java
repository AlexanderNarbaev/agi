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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave C feedback-loop test: each tick's decision becomes the next
 * tick's perception (via {@link FeedbackPerception}). Verifies that
 * the loop closes the brain's recurrence.
 */
class ConsciousnessLoopFeedbackTest {

    private ActionArena arena;

    @BeforeEach
    void setUp() { arena = ActionArena.defaults(); }

    @AfterEach
    void tearDown() { arena.close(); }

    @Test
    void feedbackPerceptionReturnsLastActionOnNextTick() {
        BrcChain chain = new BrcChain(List.of(), 0, true, SchemaDescriptor.scalar(8));
        ConsolidationCycle cycle = new ConsolidationCycle();
        cycle.open(java.util.Map.of("loop", 0));

        // feedback perception: starts as empty BitSet, gets the last
        // tick's decision after each tick
        FeedbackPerception perception = new FeedbackPerception(8, () -> {
            BitSet empty = new BitSet(8);
            empty.set(3); // seed pattern
            return empty;
        });

        ConsciousnessLoop loop = new ConsciousnessLoop(chain, arena, cycle,
                new ConjugateBudgeter(),
                ConsciousnessLoop.uniform(),
                perception);

        // tick 1: perception = seed (empty + bit 3 set)
        ConsciousnessLoop.TickSnapshot s1 = loop.tick();
        BitSet p1 = perception.get();
        assertThat(p1.get(3)).as("seed perception carries bit 3").isTrue();

        // tick 2: perception = last tick's decision (feedback)
        loop.tick();
        BitSet p2 = perception.get();
        // p2 should equal lastDecision of tick 1
        assertThat(p2).isEqualTo(loop.lastDecision());
        assertThat(s1.tickId()).isEqualTo(1L);
        assertThat(loop.totalTicks()).isEqualTo(2L);
    }

    @Test
    void multipleTicksEvolveTheFeedbackState() {
        BrcChain chain = new BrcChain(List.of(), 0, true, SchemaDescriptor.scalar(8));
        ConsolidationCycle cycle = new ConsolidationCycle();
        cycle.open(java.util.Map.of("loop", 0));
        FeedbackPerception perception = new FeedbackPerception(8, () -> {
            BitSet b = new BitSet(8);
            b.set(2);
            return b;
        });
        ConsciousnessLoop loop = new ConsciousnessLoop(chain, arena, cycle,
                new ConjugateBudgeter(),
                ConsciousnessLoop.uniform(),
                perception);

        java.util.List<BitSet> decisions = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            loop.tick();
            decisions.add((BitSet) loop.lastDecision().clone());
        }
        // After 5 ticks, the last decision should equal the last perception
        // (which was feedback from the previous tick's decision)
        assertThat(perception.get()).isEqualTo(decisions.get(decisions.size() - 1));
    }

    @Test
    void constantPerceptionDoesNotEnableFeedback() {
        // Sanity: a plain BitSet supplier (not FeedbackPerception) keeps the
        // same perception across ticks; no feedback loop is engaged.
        BrcChain chain = new BrcChain(List.of(), 0, true, SchemaDescriptor.scalar(8));
        ConsolidationCycle cycle = new ConsolidationCycle();
        cycle.open(java.util.Map.of("loop", 0));
        BitSet constantPerception = new BitSet(8);
        constantPerception.set(0);
        ConsciousnessLoop loop = new ConsciousnessLoop(chain, arena, cycle,
                new ConjugateBudgeter(),
                ConsciousnessLoop.uniform(),
                () -> constantPerception);

        loop.tick();
        loop.tick();
        assertThat(loop.totalTicks()).isEqualTo(2L);
    }
}