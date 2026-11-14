package io.matrix.cognitive.algorithms;

import io.matrix.cognitive.CognitiveStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W52 — the profile of the system's own methods outlives the process.
 *
 * <p><b>The defect this pins.</b> The ledger of measured costs lived in the heap. Every
 * restart threw it away, and the selector — which is supposed to weigh the historical
 * measured performance of a method — fell back to the DECLARED complexity of each method:
 * a person's estimate, in the place where a measurement belonged. The system forgot what it
 * had learned about its own methods at every restart.</p>
 *
 * <p><b>What is asserted here.</b> The measurement is written by the single writer of the
 * cognitive database, it is read back after the store is closed and opened again, and the
 * ledger built on the reopened store reports it — including the outcomes, which are what
 * the mandate asks the selector to weigh besides the cost.</p>
 */
class MethodProfileDurabilityTest {

    /** The workspace the measurements belong to. */
    private static final String WORKSPACE = "microworld";

    /** The instant the measurements are stamped with. */
    private static final String AT = "2026-11-09T10:00:00Z";

    @Test
    @DisplayName("the profile of the methods is read back after the store is reopened")
    void theProfileIsReadBackAfterTheStoreIsReopened(@TempDir Path directory) {
        Path database = directory.resolve("cognitive.sqlite");

        try (CognitiveStore store = new CognitiveStore(database)) {
            CostLedger ledger = new CostLedger(store, WORKSPACE);
            ledger.record("micro-world-reference", 1_000L, true);
            ledger.record("micro-world-reference", 3_000L, true);
            ledger.record("micro-world-reference", 2_000L, false);
            assertTrue(ledger.durable(), "the ledger of a running system is durable");
        }

        try (CognitiveStore store = new CognitiveStore(database)) {
            CostLedger ledger = new CostLedger(store, WORKSPACE);

            assertEquals(2_000L, ledger.measuredNanos("micro-world-reference"),
                    "the mean of 1000, 3000 and 2000 nanoseconds is 2000");
            CostLedger.Measurement profile =
                    ledger.measurement("micro-world-reference");
            assertNotNull(profile, "the profile of the method is read back");
            assertEquals(3, profile.calls());
            assertEquals(2, profile.successes());
            assertEquals(1, profile.failures());
        }
    }

    @Test
    @DisplayName("a method that was never measured is still unmeasured after a restart")
    void aMethodThatWasNeverMeasuredIsStillUnmeasured(@TempDir Path directory) {
        Path database = directory.resolve("cognitive.sqlite");

        try (CognitiveStore store = new CognitiveStore(database)) {
            CostLedger ledger = new CostLedger(store, WORKSPACE);
            ledger.record("micro-world-reference", 1_000L, true);
        }

        try (CognitiveStore store = new CognitiveStore(database)) {
            CostLedger ledger = new CostLedger(store, WORKSPACE);
            assertEquals(CostLedger.UNMEASURED, ledger.measuredNanos("micro-world-settled-model"),
                    "a method nobody ran has no measurement, and the selector must not "
                            + "invent one");
        }
    }

    @Test
    @DisplayName("the measurement of one workspace does not leak into another")
    void theMeasurementOfOneWorkspaceDoesNotLeakIntoAnother(@TempDir Path directory) {
        Path database = directory.resolve("cognitive.sqlite");

        try (CognitiveStore store = new CognitiveStore(database)) {
            CostLedger ledger = new CostLedger(store, "the-first-workspace");
            ledger.record("micro-world-reference", 1_000L, true);
        }

        try (CognitiveStore store = new CognitiveStore(database)) {
            CostLedger other = new CostLedger(store, "the-second-workspace");
            assertEquals(CostLedger.UNMEASURED, other.measuredNanos("micro-world-reference"),
                    "the profile of a workspace is the profile of that workspace");
        }
    }

    @Test
    @DisplayName("the selector weighs the restored measurement, and the decision says so")
    void theSelectorWeighesTheRestoredMeasurement(@TempDir Path directory) {
        Path database = directory.resolve("cognitive.sqlite");
        AlgorithmRegistry registry = new AlgorithmRegistry()
                .register(new IntegerArithmeticMethod())
                .register(new DijkstraShortestPathMethod());

        try (CognitiveStore store = new CognitiveStore(database)) {
            CostLedger ledger = new CostLedger(store, WORKSPACE);
            // What the previous process measured: the integer method was slow and failed
            // once. The restored profile must carry exactly that.
            ledger.record(IntegerArithmeticMethod.ID, 5_000L, true);
            ledger.record(IntegerArithmeticMethod.ID, 5_000L, false);
        }

        try (CognitiveStore store = new CognitiveStore(database)) {
            CostLedger ledger = new CostLedger(store, WORKSPACE);
            AlgorithmSelector selector = new AlgorithmSelector(registry, ledger);

            AlgorithmDecision decision = selector.decide(new ProblemRequest(
                    AlgorithmFamilies.EXACT_ARITHMETIC,
                    new IntegerArithmeticMethod.Input("2+2"),
                    Set.of(Preconditions.WELL_FORMED_EXPRESSION), ComputeBudget.unbounded()));

            AlgorithmDecision.Candidate measured = null;
            for (AlgorithmDecision.Candidate candidate : decision.candidates()) {
                if (IntegerArithmeticMethod.ID.equals(candidate.algorithmId())) {
                    measured = candidate;
                }
            }
            assertNotNull(measured, "the measured method is among the candidates");
            assertTrue(measured.verdict().contains("measured"),
                    "the decision names the MEASUREMENT, not the declared complexity: "
                            + measured.verdict());
            assertTrue(measured.verdict().contains("2 call(s)"),
                    "the decision names the outcomes as well: " + measured.verdict());
            assertTrue(measured.verdict().contains("1 not completed"),
                    "the decision names the calls that returned nothing as well: "
                            + measured.verdict());
        }
    }

    @Test
    @DisplayName("a process-local ledger still works, and says that it is process-local")
    void aProcessLocalLedgerStillWorks() {
        CostLedger ledger = new CostLedger();

        ledger.record("micro-world-reference", 1_000L, true);

        assertFalse(ledger.durable());
        assertEquals(1_000L, ledger.measuredNanos("micro-world-reference"));
    }
}
