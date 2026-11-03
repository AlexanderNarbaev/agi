package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.WorldInput;
import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W45.0 — the cycle budget is a deadline, not a declaration.
 *
 * <p>The W44 wave carried a 250 ms budget in a record and never compared the elapsed time with
 * it. These tests give the kernel a clock it controls, so the budget can be made to expire
 * deterministically — no sleeping, no flakiness — and then check that the cycle ends in an
 * explicit, persisted terminal state rather than running on silently.</p>
 *
 * <p>The clock is MONOTONIC and it is injected. It never reaches a decision about what is
 * true: it is read only to decide whether the work has taken too long.</p>
 */
class CognitiveCycleBudgetTest {

    /** The instant every record in this test carries. */
    private static final String INSTANT = "2026-11-03T00:00:00Z";

    @TempDir
    Path directory;

    private KnowledgeWorkbench workbench;
    private CognitiveStore store;

    @BeforeEach
    void setUp() {
        Path database = directory.resolve("cognitive.sqlite");
        workbench = KnowledgeWorkbench.at(database.toString());
        store = new CognitiveStore(database);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
        if (workbench != null) {
            workbench.close();
        }
    }

    @Test
    @DisplayName("a cycle that finishes inside its budget completes normally")
    void aCycleThatFinishesInsideItsBudgetCompletes() {
        AtomicInteger counter = new AtomicInteger();
        CognitiveKernel kernel = kernel(new FixedClock(0L), new LampEnvironment(), counter);

        CognitiveOutcome outcome = kernel.process(signal(counter));

        assertThat(outcome.status()).isEqualTo(CognitiveOutcome.Status.COMPLETED);
        assertThat(outcome.trace().phase("TIMEOUT")).isEmpty();
    }

    @Test
    @DisplayName("a cycle that runs out of budget ends in an explicit, persisted terminal state")
    void aCycleThatRunsOutOfBudgetEndsExplicitly() {
        AtomicInteger counter = new AtomicInteger();
        // The clock jumps a whole second the moment the cycle starts, so the very first
        // boundary check finds the budget exhausted.
        CognitiveKernel kernel = kernel(new FixedClock(1_000_000_000L), new LampEnvironment(),
                counter);

        CognitiveOutcome outcome = kernel.process(signal(counter));

        assertThat(outcome.status()).isEqualTo(CognitiveOutcome.Status.RESOURCE_LIMITED);
        assertThat(outcome.answer()).contains("ran out of its budget");
        assertThat(outcome.trace().phase("TIMEOUT")).isNotEmpty();
        assertThat(outcome.trace().phase("TIMEOUT").get(0).detail())
                .contains("experiment-executed=false")
                .contains("observation-committed=false");
        assertThat(outcome.notes())
                .anyMatch(note -> note.contains("an experiment was executed: false"))
                .anyMatch(note -> note.contains("an observation was committed: false"))
                .anyMatch(note -> note.contains("the procedure state changed: false"));
    }

    @Test
    @DisplayName("the timeout is persisted: the episode ends and the trace survives")
    void theTimeoutIsPersisted() {
        AtomicInteger counter = new AtomicInteger();
        CognitiveKernel kernel = kernel(new FixedClock(1_000_000_000L), new LampEnvironment(),
                counter);

        CognitiveOutcome outcome = kernel.process(signal(counter));

        List<LearningTrace.Step> stored = store.events(outcome.episodeId());
        assertThat(stored).isNotEmpty();
        assertThat(stored.stream().map(LearningTrace.Step::phase).toList()).contains("TIMEOUT");
        // The episode is CLOSED: a budget that expires does not leave the episode open.
        assertThat(store.episodes(CognitiveKernel.WORKSPACE_ID))
                .contains(outcome.episodeId());
    }

    @Test
    @DisplayName("work that had already happened is not rolled back when the budget expires")
    void workAlreadyDoneIsNotRolledBack() {
        AtomicInteger counter = new AtomicInteger();
        // The clock expires only AFTER the world has been probed, so the probe — a side effect
        // on the world — has already happened when the deadline is noticed.
        ProbeAwareClock clock = new ProbeAwareClock();
        CognitiveKernel kernel = kernel(clock, new CountingEnvironment(clock), counter);

        CognitiveOutcome outcome = kernel.process(signal(counter));

        assertThat(outcome.status()).isEqualTo(CognitiveOutcome.Status.RESOURCE_LIMITED);
        assertThat(outcome.trace().phase("TIMEOUT")).isNotEmpty();
        assertThat(outcome.trace().phase("TIMEOUT").get(0).detail())
                .contains("experiment-executed=true");
        assertThat(outcome.notes())
                .anyMatch(note -> note.contains("an experiment was executed: true"))
                .anyMatch(note -> note.contains("the side effects that had already happened "
                        + "are NOT rolled back"));
        assertThat(clock.probes().get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("the deadline reports the time that is left, and it never goes negative")
    void theDeadlineReportsWhatIsLeft() {
        CycleDeadline deadline = new CycleDeadline(250L, new FixedClock(0L));
        assertThat(deadline.remainingMillis()).isEqualTo(250L);
        assertThat(deadline.expired()).isFalse();
        deadline.check("a phase");

        CycleDeadline exhausted = new CycleDeadline(250L, new FixedClock(1_000_000_000L));
        assertThat(exhausted.expired()).isTrue();
        assertThat(exhausted.remainingMillis()).isZero();
        assertThatThrownByTimeout(() -> exhausted.check("a phase"));
    }

    private static void assertThatThrownByTimeout(Runnable action) {
        try {
            action.run();
        } catch (CycleTimeoutException expected) {
            assertThat(expected.budgetMillis()).isEqualTo(250L);
            assertThat(expected.phase()).isEqualTo("a phase");
            return;
        }
        throw new AssertionError("the deadline did not expire");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CognitiveKernel kernel(LongSupplier clock, MicroWorldEnvironment environment,
                                   AtomicInteger counter) {
        return new CognitiveKernel(workbench, store,
                new MicroWorldSession.DefaultFactory(environment),
                environment,
                new KernelSelector(new AlgorithmRegistry()
                        .register(new MicroWorldReferenceMethod())
                        .register(new MicroWorldSettledModelMethod())),
                new KernelRegulator(),
                () -> INSTANT,
                clock);
    }

    private CognitiveSignal signal(AtomicInteger counter) {
        return CognitiveSignal.structured("sig-" + counter.incrementAndGet(),
                "power=0,switch=0", "the test", counter.incrementAndGet());
    }

    /**
     * A clock that advances by a fixed step on every read.
     *
     * <p>A clock that reported a constant would measure an elapsed time of zero forever, so
     * the budget could never expire. The step is what makes the expiry deterministic.</p>
     */
    private static final class FixedClock implements LongSupplier {

        private final long step;
        private long calls;

        FixedClock(long stepNanosPerRead) {
            this.step = stepNanosPerRead;
        }

        @Override
        public long getAsLong() {
            calls++;
            return step * calls;
        }
    }

    /** A clock that expires the moment the world has been probed. */
    private static final class ProbeAwareClock implements LongSupplier {

        private final AtomicInteger probes = new AtomicInteger();

        @Override
        public long getAsLong() {
            return probes.get() > 0 ? 1_000_000_000L : 0L;
        }

        AtomicInteger probes() {
            return probes;
        }
    }

    /** A world that counts its probes, so the clock can expire after one. */
    private static final class CountingEnvironment implements MicroWorldEnvironment {

        private final ProbeAwareClock clock;
        private final LampEnvironment delegate = new LampEnvironment();

        CountingEnvironment(ProbeAwareClock clock) {
            this.clock = clock;
        }

        @Override
        public Observation probe(WorldInput input) {
            clock.probes().incrementAndGet();
            return delegate.probe(input);
        }

        @Override
        public Set<String> variables() {
            return delegate.variables();
        }

        @Override
        public String outcomeVariable() {
            return delegate.outcomeVariable();
        }
    }
}
