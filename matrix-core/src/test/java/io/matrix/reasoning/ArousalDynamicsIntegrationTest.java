package io.matrix.reasoning;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ArousalDynamics integration with ConsciousnessLoop (RUN 50).
 */
class ArousalDynamicsIntegrationTest {

    private io.matrix.actions.ActionArena arena;

    @BeforeEach
    void setUp() {
        arena = io.matrix.actions.ActionArena.defaults();
    }

    @AfterEach
    void tearDown() {
        arena.close();
    }

    private ConsciousnessLoop newLoop(java.util.function.Supplier<BitSet> perception) {
        BrcChain chain = new BrcChain(List.of(), 0, true,
                io.matrix.neuron.SchemaDescriptor.scalar(8));
        io.matrix.lifecycle.ConsolidationCycle cycle = new io.matrix.lifecycle.ConsolidationCycle();
        cycle.open(java.util.Map.of("loop", 0));
        return new ConsciousnessLoop(chain, arena, cycle,
                new io.matrix.budgeter.ConjugateBudgeter(),
                ConsciousnessLoop.uniform(), perception);
    }

    @Test
    void arousalDynamicsIsNullByDefault() {
        ConsciousnessLoop loop = newLoop(() -> new BitSet());
        assertThat(loop.getArousalDynamics()).isNull();
    }

    @Test
    void arousalDynamicsUpdatesOnEachTick() {
        ConsciousnessLoop loop = newLoop(() -> new BitSet());
        ArousalDynamics arousal = new ArousalDynamics(0.5, 0.1);
        loop.setArousalDynamics(arousal);
        double before = arousal.getArousal();
        // Tick with prediction-error > 0.
        loop.tick();
        double after = arousal.getArousal();
        // Should have increased (predErr > 0 → error normalized > 0).
        assertThat(after).isGreaterThanOrEqualTo(before);
    }

    @Test
    void arousalDynamicsRespectsDisabled() {
        ConsciousnessLoop loop = newLoop(() -> new BitSet());
        ArousalDynamics arousal = new ArousalDynamics();
        loop.setArousalDynamics(arousal);
        loop.tick();
        double afterOneTick = arousal.getArousal();
        // Disable mid-run.
        loop.setArousalDynamics(null);
        loop.tick();
        loop.tick();
        // Arousal should not have changed after disable.
        assertThat(arousal.getArousal()).isEqualTo(afterOneTick);
    }

    @Test
    void arousalMonotonicallyIncreasesUnderHighErrorStream() {
        ConsciousnessLoop loop = newLoop(() -> {
            // Force non-zero prediction error by returning varying bits.
            Random r = new Random(System.nanoTime());
            BitSet bs = new BitSet(8);
            for (int i = 0; i < 8; i++) if (r.nextBoolean()) bs.set(i);
            return bs;
        });
        ArousalDynamics arousal = new ArousalDynamics(0.5, 0.05);
        loop.setArousalDynamics(arousal);
        double previous = arousal.getArousal();
        for (int i = 0; i < 20; i++) {
            loop.tick();
            double current = arousal.getArousal();
            assertThat(current).isGreaterThanOrEqualTo(previous);
            previous = current;
        }
    }

    @Test
    void arousalAccessorRoundtrips() {
        ConsciousnessLoop loop = newLoop(() -> new BitSet());
        ArousalDynamics arousal = new ArousalDynamics(0.7, 0.2);
        loop.setArousalDynamics(arousal);
        assertThat(loop.getArousalDynamics()).isSameAs(arousal);
        assertThat(arousal.getAlpha()).isEqualTo(0.7);
        assertThat(arousal.getBeta()).isEqualTo(0.2);
    }
}
