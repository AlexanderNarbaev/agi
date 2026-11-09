package io.matrix.cognitive;

import io.matrix.contract.Correlation;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskReport;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import io.matrix.contract.bridge.CognitiveTaskResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — chain A judges the peer's answer, and the peer's confidence is not the judge.
 *
 * <p><b>What these tests hold.</b> A remote report cannot make a proposition supported
 * by itself. It is supported when chain A's <i>own</i> records support it, and even then
 * only when the report is independent of what chain A supplied to the peer — the same
 * information coming back is not a second observation of it. A peer that declares a high
 * confidence and cites nothing local leaves the proposition unresolved, which is the
 * honest answer, not a failed one.</p>
 */
class RemoteReportAssessorTest {

    /** The instant the fixtures claim. */
    private static final String AT = "2026-11-09T12:00:00Z";

    /** The request chain A sent. */
    private static CognitiveTaskRequest request() {
        return new CognitiveTaskRequest(BridgeProtocol.VERSION, "tsk-1", "req-1",
                new Correlation("corr-1", "ep-parent", "ep-1", "req-1"), "ep-parent",
                "chain-a-epistemic-core", "brain.cognitive-inference", "v1",
                CognitiveTaskRequest.Kind.COGNITIVE_INFERENCE, "is the lamp lit", Map.of(),
                List.of(), "cognitive-task-response/v1",
                CognitiveTaskRequest.DEFAULT_BUDGET_MILLIS,
                CognitiveTaskRequest.DEFAULT_DEADLINE_MILLIS, 4096,
                CognitiveTaskRequest.MAX_REMOTE_HOPS, Provenance.of("chain-a", AT), "hash-1",
                null);
    }

    /** The peer's response. */
    private static CognitiveTaskResponse response(CognitiveTaskResponse.Status status) {
        return new CognitiveTaskResponse("tsk-1", "req-1", request().correlation(), "hash-1",
                "chain-b-brain-runtime", "brain.cognitive-inference", "v1", status,
                "the lamp is lit", 0.95, CognitiveTaskResponse.ConfidenceBasis.DECLARED,
                List.of(), null, null, List.of(), List.of(), List.of(),
                List.of("BIR"), Provenance.of("chain-b", AT), "the capability ran", 5L,
                "trace-1", null, AT);
    }

    /** The peer's report. */
    private static CognitiveTaskReport report(CognitiveTaskReport.Relation relation,
                                              CognitiveTaskReport.ConfidenceBasis basis,
                                              List<String> derivedFrom) {
        return new CognitiveTaskReport(BridgeProtocol.VERSION, "tsk-1", "req-1",
                "the lamp is lit", null, relation, 0.95, basis, derivedFrom,
                "chain-b-brain-runtime", "brain.cognitive-inference", "v1", List.of(),
                Provenance.of("chain-b", AT), AT);
    }

    /** Evidence that knows nothing. */
    private static RemoteReportAssessor.LocalEvidence evidence(int supporting,
                                                               int contradicting) {
        return new RemoteReportAssessor.LocalEvidence() {
            @Override
            public int supporting(String proposition) {
                return supporting;
            }

            @Override
            public int contradicting(String proposition) {
                return contradicting;
            }
        };
    }

    @Test
    @DisplayName("a report that restates what chain A supplied is qualified, not supported")
    void aReportThatRestatesWhatChainASuppliedIsQualified() {
        // The peer declares no lineage: it says nothing about where the proposition came
        // from. The proposition itself is the thing chain A supplied, and that alone
        // makes the report the same information coming back.
        RemoteReportAssessor.LocalEvidence evidence = new RemoteReportAssessor.LocalEvidence() {
            @Override
            public int supporting(String proposition) {
                return 1;
            }

            @Override
            public int contradicting(String proposition) {
                return 0;
            }

            @Override
            public List<String> propositionsOf(List<String> recordIds) {
                return List.of("the lamp is lit");
            }
        };
        RemoteReportAssessor assessor = new RemoteReportAssessor(evidence);

        RemoteReportAssessor.Verdict verdict = assessor.assess(request(),
                response(CognitiveTaskResponse.Status.COMPLETED),
                report(CognitiveTaskReport.Relation.SUPPORTS,
                        CognitiveTaskReport.ConfidenceBasis.DECLARED, List.of()),
                List.of("clm-supplied"));

        assertEquals(RemoteReportAssessor.Disposition.QUALIFIED, verdict.disposition(),
                "a result must not independently validate itself");
        assertTrue(verdict.reason().contains("states the proposition"), verdict.reason());
        assertFalse(verdict.independent(),
                "a report that restates what was supplied is not independent");
    }

    @Test
    @DisplayName("a response that answers a different request is invalid")
    void aResponseToAnotherRequestIsInvalid() {
        RemoteReportAssessor assessor = new RemoteReportAssessor(evidence(3, 0));

        CognitiveTaskResponse wrong = new CognitiveTaskResponse("tsk-9", "req-9",
                request().correlation(), "hash-1", "chain-b", "brain.cognitive-inference", "v1",
                CognitiveTaskResponse.Status.COMPLETED, "something", 0.9,
                CognitiveTaskResponse.ConfidenceBasis.DECLARED, List.of(), null, null,
                List.of(), List.of(), List.of(), List.of("BIR"), Provenance.of("chain-b", AT),
                "the capability ran", 5L, "trace-9", null, AT);

        RemoteReportAssessor.Verdict verdict = assessor.assess(request(), wrong,
                report(CognitiveTaskReport.Relation.SUPPORTS,
                        CognitiveTaskReport.ConfidenceBasis.DECLARED, List.of()), List.of());

        assertEquals(RemoteReportAssessor.Disposition.INVALID, verdict.disposition());
        assertTrue(verdict.reason().contains("different request"), verdict.reason());
    }

    @Test
    @DisplayName("a response without provenance cannot even be written")
    void aResponseWithNoProvenanceCannotBeWritten() {
        // The contract refuses it first: a response that carries no provenance never
        // reaches the assessor at all. The assessor's own check stays as a second line
        // of defence, but the record itself is not constructible.
        assertThrows(IllegalArgumentException.class, () -> new CognitiveTaskResponse("tsk-1",
                "req-1", request().correlation(), "hash-1", "chain-b",
                "brain.cognitive-inference", "v1", CognitiveTaskResponse.Status.COMPLETED,
                "the lamp is lit", 0.9, CognitiveTaskResponse.ConfidenceBasis.DECLARED,
                List.of(), null, null, List.of(), List.of(), List.of(), List.of("BIR"), null,
                "the capability ran", 5L, "trace-1", null, AT));
    }

    @Test
    @DisplayName("a completed report with no local evidence is unresolved, never supported")
    void aReportWithoutLocalEvidenceIsUnresolved() {
        RemoteReportAssessor assessor = new RemoteReportAssessor(evidence(0, 0));

        RemoteReportAssessor.Verdict verdict = assessor.assess(request(),
                response(CognitiveTaskResponse.Status.COMPLETED),
                report(CognitiveTaskReport.Relation.SUPPORTS,
                        CognitiveTaskReport.ConfidenceBasis.DECLARED, List.of()), List.of());

        assertEquals(RemoteReportAssessor.Disposition.UNRESOLVED, verdict.disposition(),
                "a peer's own word never makes a proposition supported here");
    }

    @Test
    @DisplayName("chain A's own supporting records and an independent report make it supported")
    void localEvidenceAndAnIndependentReportSupport() {
        RemoteReportAssessor assessor = new RemoteReportAssessor(evidence(2, 0));

        RemoteReportAssessor.Verdict verdict = assessor.assess(request(),
                response(CognitiveTaskResponse.Status.COMPLETED),
                report(CognitiveTaskReport.Relation.SUPPORTS,
                        CognitiveTaskReport.ConfidenceBasis.INDEPENDENT, List.of()), List.of());

        assertEquals(RemoteReportAssessor.Disposition.SUPPORTED, verdict.disposition());
        assertTrue(verdict.independent());
    }

    @Test
    @DisplayName("a report that rests on what chain A supplied is qualified, not supported")
    void aReturningReportIsQualified() {
        RemoteReportAssessor assessor = new RemoteReportAssessor(evidence(2, 0));

        RemoteReportAssessor.Verdict verdict = assessor.assess(request(),
                response(CognitiveTaskResponse.Status.COMPLETED),
                report(CognitiveTaskReport.Relation.SUPPORTS,
                        CognitiveTaskReport.ConfidenceBasis.DECLARED,
                        List.of("clm-chain-a-supplied-1")),
                List.of("clm-chain-a-supplied-1", "clm-chain-a-supplied-2"));

        assertEquals(RemoteReportAssessor.Disposition.QUALIFIED, verdict.disposition(),
                "the same information coming back is not an independent observation");
        assertFalse(verdict.independent());
        assertEquals(List.of("clm-chain-a-supplied-1"), verdict.returning());
    }

    @Test
    @DisplayName("chain A's own contradicting records reject the proposition")
    void localContradictionRejects() {
        RemoteReportAssessor assessor = new RemoteReportAssessor(evidence(1, 2));

        RemoteReportAssessor.Verdict verdict = assessor.assess(request(),
                response(CognitiveTaskResponse.Status.COMPLETED),
                report(CognitiveTaskReport.Relation.SUPPORTS,
                        CognitiveTaskReport.ConfidenceBasis.INDEPENDENT, List.of()), List.of());

        assertEquals(RemoteReportAssessor.Disposition.REJECTED, verdict.disposition());
        assertTrue(verdict.reason().contains("contradicts"), verdict.reason());
    }

    @Test
    @DisplayName("a peer's declared confidence is not independent evidence")
    void aDeclaredConfidenceIsNotEvidence() {
        RemoteReportAssessor assessor = new RemoteReportAssessor(evidence(0, 0));

        RemoteReportAssessor.Verdict verdict = assessor.assess(request(),
                response(CognitiveTaskResponse.Status.COMPLETED),
                report(CognitiveTaskReport.Relation.SUPPORTS,
                        CognitiveTaskReport.ConfidenceBasis.DECLARED, List.of()), List.of());

        assertEquals(RemoteReportAssessor.Disposition.UNRESOLVED, verdict.disposition());
        assertTrue(verdict.reason().contains("not independent evidence"), verdict.reason());
    }
}
