package io.matrix.autonomy;

import io.matrix.brain.BrainCycle;
import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.ethics.EthicalFilter;
import io.matrix.lifecycle.ImpulseScheduler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W47 — the AutonomyEngine's scheduler contract and its scheduled cycles.
 *
 * <p>The defect these cover: the gateway built the engine with a NULL
 * scheduler, the engine's curiosity and integrity cycles fired their drives
 * through it unconditionally, and a ScheduledExecutorService CANCELS a task
 * that throws — silently and permanently. So the gateway's curiosity cycle ran
 * once, at t=120s, and then was dead for the life of the process; and the
 * integrity cycle never ran its validation at all, because it fires the drive
 * BEFORE the validation.
 */
class AutonomyEngineTest {

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

    /** A brain that always fails. */
    private static final class FailingBrain implements BrainCycle {
        @Override
        public CycleResult cycle(String input) {
            throw new IllegalStateException("the brain is down");
        }

        @Override
        public void close() { }
    }

    /** The scheduler construction the gateway path uses. */
    private static ImpulseScheduler realScheduler() {
        return new ImpulseScheduler(new ConjugateBudgeter(), new EthicalFilter());
    }

    @Test
    void theEngineRefusesANullScheduler() {
        // The contract is enforced at construction: a null scheduler is not a
        // degraded engine, it is an engine whose drives can never be expressed.
        assertThatThrownBy(() -> new AutonomyEngine(new RecordingBrain(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("scheduler");
    }

    @Test
    void theEngineExposesTheSchedulerItFiresThrough() {
        ImpulseScheduler scheduler = realScheduler();
        AutonomyEngine engine = new AutonomyEngine(new RecordingBrain(), scheduler);
        assertThat(engine.scheduler()).isSameAs(scheduler);
    }

    @Test
    void theCuriosityCycleFiresItsDriveAndSurvives() {
        ImpulseScheduler scheduler = realScheduler();
        AutonomyEngine engine = new AutonomyEngine(new RecordingBrain(), scheduler);

        long firesBefore = scheduler.totalFires();
        assertThatCode(engine::curiosityCycle).doesNotThrowAnyException();

        assertThat(scheduler.totalFires())
                .as("the curiosity drive must be expressed through the scheduler")
                .isGreaterThan(firesBefore);
        assertThat(engine.getCycleCount()).isEqualTo(1L);
    }

    @Test
    void theIntegrityCycleValidatesAndSurvives() {
        // The integrity cycle fires the drive BEFORE the validation, so with a
        // broken scheduler the validation never happens at all. The brain must
        // be asked to validate.
        ImpulseScheduler scheduler = realScheduler();
        RecordingBrain brain = new RecordingBrain();
        AutonomyEngine engine = new AutonomyEngine(brain, scheduler);

        assertThatCode(engine::integrityCycle).doesNotThrowAnyException();

        assertThat(brain.inputs)
                .as("the integrity cycle must reach the validation")
                .contains("Validate current state.");
        assertThat(engine.getCycleCount()).isEqualTo(1L);
    }

    @Test
    void theIdleCycleFiresItsDriveWhenTheReflectionAsksForIt() {
        ImpulseScheduler scheduler = realScheduler();
        AutonomyEngine engine = new AutonomyEngine(new BrainCycle() {
            @Override
            public CycleResult cycle(String input) {
                return new CycleResult(true, "GOAL: investigate the anomaly",
                        "something is worth investigating", 0.1, 1, 0.2, 0.9, 1L, 1L);
            }

            @Override
            public void close() { }
        }, scheduler);

        long firesBefore = scheduler.totalFires();
        assertThatCode(engine::idleCycle).doesNotThrowAnyException();

        assertThat(scheduler.totalFires())
                .as("a reflection that asks for it must fire the curiosity drive")
                .isGreaterThan(firesBefore);
    }

    @Test
    void aFailingBrainDoesNotKillTheScheduledCycle() {
        // An exception escaping a scheduleAtFixedRate task cancels that task for
        // good, silently. The cycle must therefore swallow the failure, log it
        // and let the next one run.
        AutonomyEngine engine = new AutonomyEngine(new FailingBrain(), realScheduler());

        assertThatCode(engine::curiosityCycle).doesNotThrowAnyException();
        assertThatCode(engine::integrityCycle).doesNotThrowAnyException();
        assertThatCode(engine::idleCycle).doesNotThrowAnyException();

        assertThat(engine.getCycleCount())
                .as("the cycles keep running after a failure")
                .isEqualTo(3L);
    }

    @Test
    void theCycleCountKeepsGrowingAcrossRepeatedCycles() {
        AutonomyEngine engine = new AutonomyEngine(new RecordingBrain(), realScheduler());
        engine.curiosityCycle();
        engine.curiosityCycle();
        engine.integrityCycle();
        assertThat(engine.getCycleCount()).isEqualTo(3L);
    }

    @Test
    void theIdleCycleCountsItsReflections() {
        // The reflection counter belongs to the idle cycle; the curiosity cycle
        // records its last reflection but does not count itself.
        RecordingBrain brain = new RecordingBrain();
        AutonomyEngine engine = new AutonomyEngine(brain, realScheduler());
        engine.idleCycle();
        engine.idleCycle();
        assertThat(engine.getTotalReflections()).isEqualTo(2L);
        assertThat(engine.getLastReflection()).isEqualTo("a quiet reflection");
    }
}
