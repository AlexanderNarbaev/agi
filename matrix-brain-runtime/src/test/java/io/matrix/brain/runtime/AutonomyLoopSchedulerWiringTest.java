package io.matrix.brain.runtime;

import io.matrix.autonomy.AutonomyEngine;
import io.matrix.brain.BrainCycle;
import io.matrix.lifecycle.ImpulseScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W47 — the gateway's autonomy loop must give the engine a REAL scheduler.
 *
 * <p>This is the defect site. {@code AutonomyLoop} used to build the engine
 * with a null scheduler, so the engine's curiosity and integrity cycles threw a
 * NullPointerException when they fired their drives, and a
 * ScheduledExecutorService cancels a task that throws — silently and
 * permanently. The gateway therefore had one curiosity cycle, at t=120s, and
 * then none; and no integrity cycle ever, because it fires the drive before
 * the validation.
 *
 * <p>The scheduled cycles are package-private in {@code io.matrix.autonomy}, so
 * they are invoked reflectively here — the same pattern the repository already
 * uses to reach across a module boundary.
 */
class AutonomyLoopSchedulerWiringTest {

    /** A brain that records what it was asked and never fails. */
    private static final class RecordingBrain implements BrainCycle {
        private final List<String> inputs = new ArrayList<>();

        @Override
        public CycleResult cycle(String input) {
            inputs.add(input);
            return new CycleResult(true, "THINK", "a quiet reflection",
                    0.1, 1, 0.2, 0.9, inputs.size(), 1L);
        }

        @Override
        public void close() { }
    }

    @Test
    void theLoopGivesTheEngineARealScheduler() {
        AutonomyLoop loop = new AutonomyLoop(new RecordingBrain());
        try {
            ImpulseScheduler scheduler = loop.engine().scheduler();
            assertThat(scheduler)
                    .as("the engine must be built with a real scheduler, not with null")
                    .isNotNull();
        } finally {
            loop.close();
        }
    }

    @Test
    void theGatewayCuriosityCycleDoesNotDie() throws Exception {
        RecordingBrain brain = new RecordingBrain();
        AutonomyLoop loop = new AutonomyLoop(brain);
        try {
            invokeCycle(loop.engine(), "curiosityCycle");
            assertThat(loop.engine().getCycleCount())
                    .as("the curiosity cycle must complete and count itself")
                    .isEqualTo(1L);
            assertThat(brain.inputs)
                    .as("the curiosity cycle must reach the reflection")
                    .isNotEmpty();
        } finally {
            loop.close();
        }
    }

    @Test
    void theGatewayIntegrityCycleReachesTheValidation() throws Exception {
        RecordingBrain brain = new RecordingBrain();
        AutonomyLoop loop = new AutonomyLoop(brain);
        try {
            invokeCycle(loop.engine(), "integrityCycle");
            assertThat(brain.inputs)
                    .as("the integrity cycle must reach the validation")
                    .contains("Validate current state.");
        } finally {
            loop.close();
        }
    }

    @Test
    void theLoopStartsAndStopsWithoutTouchingANullScheduler() {
        AutonomyLoop loop = new AutonomyLoop(new RecordingBrain());
        try {
            loop.start();
            assertThat(loop.engine().isRunning()).isTrue();
        } finally {
            loop.close();
        }
        assertThat(loop.engine().isRunning()).isFalse();
    }

    /** Invoke one of the engine's package-private scheduled cycles. */
    private static void invokeCycle(AutonomyEngine engine, String name) throws Exception {
        Method cycle = AutonomyEngine.class.getDeclaredMethod(name);
        cycle.setAccessible(true);
        try {
            cycle.invoke(engine);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }
}
