package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.DriveSignal;
import io.matrix.cognitive.neuromodulation.RegulatoryState;
import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W45.0 — the drives survive a restart.
 *
 * <p>ADR-W44-01 lists the drives among the cognitive state the cognitive store persists, and
 * until W45 they were the one item on that list that was not persisted: the regulatory state
 * lived in the in-memory adapter, so every restart reset every drive to neutral and the
 * document and the implementation disagreed.</p>
 *
 * <p>These tests drive a real kernel over a real file, close the file, open it again, and
 * check that the state is the same — and that the provenance of every change is there.</p>
 */
class CognitiveRegulatoryPersistenceTest {

    /** The instant every record in this test carries. */
    private static final String INSTANT = "2026-11-03T00:00:00Z";

    @TempDir
    Path directory;

    private Path database;
    private KnowledgeWorkbench workbench;
    private CognitiveStore store;
    private final AtomicInteger counter = new AtomicInteger();

    @BeforeEach
    void setUp() {
        database = directory.resolve("cognitive.sqlite");
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
    @DisplayName("the regulatory state is persisted and restored after a real restart")
    void theRegulatoryStateSurvivesARestart() {
        // A cycle that finds a contradiction raises the uncertainty drive.
        for (int i = 0; i < 8; i++) {
            kernel().process(signal("power=" + (i % 2) + ",switch=" + ((i / 2) % 2)));
        }

        RegulatoryState before = store.regulatoryState(CognitiveKernel.WORKSPACE_ID);
        assertThat(before).withFailMessage("the state must be persisted").isNotNull();
        assertThat(before.held()).isNotEmpty();
        long version = store.regulatoryVersion(CognitiveKernel.WORKSPACE_ID);
        assertThat(version).isPositive();

        // The application restarts: the files are closed and opened again.
        store.close();
        workbench.close();
        store = new CognitiveStore(database);
        workbench = KnowledgeWorkbench.at(database.toString());

        // The restored state is the same state.
        CognitiveKernel restarted = kernel();
        RegulatoryState restored = restarted.restoreRegulatoryState();
        assertThat(restored).isNotNull();
        assertThat(restored.held()).isEqualTo(before.held());
        assertThat(restored.level(DriveSignal.Dimension.UNCERTAINTY))
                .isEqualTo(before.level(DriveSignal.Dimension.UNCERTAINTY));
        assertThat(restored.level(DriveSignal.Dimension.NOVELTY))
                .isEqualTo(before.level(DriveSignal.Dimension.NOVELTY));
        assertThat(store.regulatoryVersion(CognitiveKernel.WORKSPACE_ID)).isEqualTo(version);
    }

    @Test
    @DisplayName("the provenance of every regulatory change is recorded")
    void theProvenanceIsRecorded() {
        kernel().process(signal("power=0,switch=0"));

        List<CognitiveStore.RegulatoryEventRecord> events =
                store.regulatoryEvents(CognitiveKernel.WORKSPACE_ID);
        assertThat(events).isNotEmpty();
        for (CognitiveStore.RegulatoryEventRecord event : events) {
            assertThat(event.id()).isNotBlank();
            assertThat(event.kind()).isNotBlank();
            assertThat(event.source()).isNotBlank();
            assertThat(event.episodeId()).isNotBlank();
        }
        // The event id carries the episode, so a replay of the same episode is detectable.
        assertThat(events.get(0).id()).startsWith(events.get(0).episodeId());
    }

    @Test
    @DisplayName("a repeated event is refused: a drive must not move twice for one event")
    void aRepeatedEventIsRefused() {
        CognitiveStore.RegulatoryEventRecord event = new CognitiveStore.RegulatoryEventRecord(
                "ep-x-reg-0", CognitiveKernel.WORKSPACE_ID, "ep-x", "NOVEL_INPUT", 1.0, "test",
                INSTANT);

        store.recordRegulatoryState(CognitiveKernel.WORKSPACE_ID, RegulatoryState.neutral(),
                INSTANT, List.of(event));

        assertThatThrownBy(() -> store.recordRegulatoryState(CognitiveKernel.WORKSPACE_ID,
                RegulatoryState.neutral(), INSTANT, List.of(event)))
                .isInstanceOf(CognitiveConflictException.class)
                .hasMessageContaining("already been applied");
    }

    @Test
    @DisplayName("the version of the regulatory state increases with every write")
    void theVersionIncreases() {
        long first = store.recordRegulatoryState(CognitiveKernel.WORKSPACE_ID,
                RegulatoryState.neutral(), INSTANT, List.of());
        long second = store.recordRegulatoryState(CognitiveKernel.WORKSPACE_ID,
                RegulatoryState.neutral(), INSTANT, List.of());
        assertThat(second).isEqualTo(first + 1);
        assertThat(store.regulatoryVersion(CognitiveKernel.WORKSPACE_ID)).isEqualTo(second);
    }

    @Test
    @DisplayName("a cycle persists the state it produced, and says so in the trace")
    void aCyclePersistsTheStateItProduced() {
        CognitiveOutcome outcome = kernel().process(signal("power=0,switch=0"));

        assertThat(store.regulatoryState(CognitiveKernel.WORKSPACE_ID)).isNotNull();
        assertThat(outcome.trace().phase("REGULATE")).isNotEmpty();
        assertThat(outcome.trace().phase("REGULATE").get(0).detail())
                .contains("the drives now read");
    }

    @Test
    @DisplayName("the cost ledger is PROCESS-LOCAL: a measurement never survives a restart")
    void theCostLedgerIsProcessLocal() {
        // The ledger holds the measured cost of every method in an in-memory map, and the
        // service builds a NEW ledger when it is built. A measurement therefore does not
        // survive a restart, and the selector never compares a cost measured on one machine
        // with a cost measured on another.
        io.matrix.cognitive.algorithms.CostLedger ledger =
                new io.matrix.cognitive.algorithms.CostLedger();
        assertThat(ledger.measuredMethodIds()).isEmpty();
        assertThat(ledger.measuredNanos(MicroWorldReferenceMethod.ID))
                .isEqualTo(io.matrix.cognitive.algorithms.CostLedger.UNMEASURED);

        ledger.record(MicroWorldReferenceMethod.ID, 1_000_000L, true);
        assertThat(ledger.measuredMethodIds()).containsExactly(MicroWorldReferenceMethod.ID);

        // A restart means a new ledger: the measurement is gone, and the store holds no
        // cost table to restore it from.
        io.matrix.cognitive.algorithms.CostLedger afterRestart =
                new io.matrix.cognitive.algorithms.CostLedger();
        assertThat(afterRestart.measuredMethodIds()).isEmpty();
        assertThat(store.regulatoryState(CognitiveKernel.WORKSPACE_ID)).isNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CognitiveKernel kernel() {
        LampEnvironment environment = new LampEnvironment();
        return new CognitiveKernel(workbench, store,
                new MicroWorldSession.DefaultFactory(environment),
                environment,
                new KernelSelector(new AlgorithmRegistry()
                        .register(new MicroWorldReferenceMethod())
                        .register(new MicroWorldSettledModelMethod())),
                new KernelRegulator(),
                () -> INSTANT);
    }

    private CognitiveSignal signal(String payload) {
        return CognitiveSignal.structured("sig-" + counter.incrementAndGet(), payload, "the test",
                counter.incrementAndGet());
    }
}
