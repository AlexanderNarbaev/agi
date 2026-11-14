package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.CostLedger;
import io.matrix.cognitive.algorithms.MethodOutcome;
import io.matrix.cognitive.algorithms.MethodReliability;
import io.matrix.cognitive.algorithms.MethodVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — the sink that writes a remote verdict into the profile of methods.
 *
 * <p><b>What these tests hold.</b> A verdict is only useful if it lands in the stratum it
 * was observed in: a rate pooled over the capabilities, the families and the versions would
 * answer a different question from the one the profile asks. The tests therefore hold the
 * stratum, and they hold that the profile survives a restart — a verdict that is only in
 * the heap is an anecdote.</p>
 */
class LedgerVerdictSinkTest {

    /** The workspace of the fixtures. */
    private static final String WORKSPACE = "bridged";

    /** The capability the peer ran. */
    private static final String CAPABILITY = "brain.cognitive-inference";

    /** The family of the task it answered. */
    private static final String FAMILY = "COGNITIVE_INFERENCE";

    /** The version of the capability that ran. */
    private static final String VERSION = "v1";

    @Test
    @DisplayName("a verdict lands in the stratum of the capability, the family and the version")
    void theVerdictLandsInItsStratum(@TempDir Path directory) {
        try (CognitiveStore store = new CognitiveStore(directory.resolve("cognitive.sqlite"))) {
            CostLedger ledger = new CostLedger(store, WORKSPACE);
            LedgerVerdictSink sink = new LedgerVerdictSink(ledger);

            sink.record(CAPABILITY, FAMILY, VERSION, MethodOutcome.VALIDATED);

            List<MethodVerdict> verdicts = store.verdicts(WORKSPACE);
            assertEquals(1, verdicts.size(), "one verdict, one row");
            MethodVerdict verdict = verdicts.get(0);
            assertEquals(FAMILY, verdict.family(),
                    "the verdict belongs to the family of the task it answered");
            assertEquals(VERSION, verdict.capabilityVersion(),
                    "and to the version of the capability that ran");
            assertEquals(1, verdict.validated(), "the trial is counted");
            assertEquals(1, verdict.decisions(), "a validated result is a decided trial");
        }
    }

    @Test
    @DisplayName("a supported but not independent report is a trial that is not a validated one")
    void aQualifiedReportIsATrialThatIsNotValidated(@TempDir Path directory) {
        try (CognitiveStore store = new CognitiveStore(directory.resolve("cognitive.sqlite"))) {
            CostLedger ledger = new CostLedger(store, WORKSPACE);
            LedgerVerdictSink sink = new LedgerVerdictSink(ledger);

            sink.record(CAPABILITY, FAMILY, VERSION, MethodOutcome.QUALIFIED);

            MethodVerdict verdict = store.verdicts(WORKSPACE).get(0);
            assertEquals(1, verdict.qualified(),
                    "the report was supported but not independent");
            assertEquals(1, verdict.decisions(),
                    "it is a decided trial: the verifier decided something about it");
            assertEquals(0, verdict.validated(),
                    "and it is not a validated result, so it is not in the numerator");
        }
    }

    @Test
    @DisplayName("the verdict survives a restart, and the profile reads it back")
    void theVerdictSurvivesARestart(@TempDir Path directory) {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            CostLedger ledger = new CostLedger(store, WORKSPACE);
            new LedgerVerdictSink(ledger).record(CAPABILITY, FAMILY, VERSION,
                    MethodOutcome.VALIDATED);
        }
        try (CognitiveStore store = new CognitiveStore(database)) {
            CostLedger restarted = new CostLedger(store, WORKSPACE);
            List<MethodVerdict> verdicts = store.verdicts(WORKSPACE);
            assertEquals(1, verdicts.size(),
                    "the verdict is on record, not only in the heap of the process that read it");
            assertEquals(FAMILY, verdicts.get(0).family(),
                    "and it keeps its stratum across the restart");
            MethodReliability reliability =
                    restarted.reliability(CAPABILITY, FAMILY, VERSION);
            assertEquals(MethodReliability.State.NOT_YET_USABLE, reliability.state(),
                    "one decided trial is not enough to act on, and the state says so");
            assertTrue(reliability.width() > 0.20,
                    "the interval of one trial is wide, and the width gate says so");
        }
    }
}
