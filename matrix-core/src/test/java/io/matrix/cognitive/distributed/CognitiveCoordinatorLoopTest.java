package io.matrix.cognitive.distributed;

import io.matrix.bridge.BridgeAudit;
import io.matrix.bridge.BridgeService;
import io.matrix.bridge.TaskPublisher;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.DispatchIntent;
import io.matrix.cognitive.RemoteReportAssessor;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskReport;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import io.matrix.contract.bridge.CognitiveTaskResponse;
import io.matrix.contract.bridge.HmacBridgeSigner;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — the closed loop: the peer's report reaches chain A's epistemic model, and the
 * verdict stays chain A's own.
 *
 * <p><b>What this holds.</b> A remote report does not stop at the dispatch record. It
 * enters chain A's epistemic store through the same governed crossing that admits
 * chain B's knowledge messages, and it arrives as a <i>candidate</i> with the relation
 * the peer stated and the confidence chain A has itself — which is none. The dispatch
 * record says what happened to the report, and the verdict says what chain A concluded
 * about the proposition; the two are separate facts and they are recorded separately.</p>
 *
 * <p><b>What it does not claim.</b> A report that was admitted is not a proposition that
 * was established. The verdict here is UNRESOLVED, because a peer's declared confidence
 * settles nothing in chain A's own evidence.</p>
 */
class CognitiveCoordinatorLoopTest {

    /** The workspace the records are written into. */
    private static final String WORKSPACE = "bridged";

    /** The instant every fixture claims. */
    private static final String AT = "2026-11-09T12:00:00Z";

    /** The shared secret of the two sides. */
    private static final byte[] SECRET =
            "the-bridge-secret-of-the-tests".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    private KnowledgeWorkbench workbench;
    private CognitiveStore store;
    private HmacBridgeSigner peerSigner;

    @BeforeEach
    void openTheStores() throws Exception {
        workbench = KnowledgeWorkbench.at(directory.resolve("knowledge.sqlite").toString());
        store = new CognitiveStore(directory.resolve("cognitive.sqlite"));
        peerSigner = new HmacBridgeSigner(SECRET);
    }

    @AfterEach
    void closeTheStores() {
        store.close();
        workbench.close();
    }

    /** Chain A's own bridge receiver — the only way a report may enter the model. */
    private BridgeService bridge() {
        return new BridgeService(workbench, peerSigner,
                EnumSet.allOf(BridgeCapability.class),
                io.matrix.contract.bridge.BridgeBudget.standard(),
                new io.matrix.contract.bridge.InMemoryBridgeIdempotency(),
                (BridgeAudit) record -> {
                },
                WORKSPACE, () -> AT);
    }

    /** The peer's signed report, as it arrives. */
    private BridgeEnvelope peerReport(String requestId, CognitiveTaskReport.Relation relation) {
        CognitiveTaskReport payload = new CognitiveTaskReport(BridgeProtocol.VERSION,
                "tsk-" + requestId, requestId, "the lamp is lit", null, relation, 0.9,
                CognitiveTaskReport.ConfidenceBasis.DECLARED, List.of(), "chain-b",
                "brain.cognitive-inference", "v1", List.of("the lamp is dark"),
                Provenance.of("chain-b", AT), AT);
        BridgeEnvelope envelope = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                requestId, AT, "chain-b", BridgeCapability.INFERENCE_RESPONSE,
                new BridgePayloadCodec().encode(BridgePayload.ofTaskReport(payload,
                        Provenance.of("chain-b", AT))), null);
        return envelope.withSignature(envelope.sign(peerSigner));
    }

    /** The peer's structured result. */
    private CognitiveTaskResponse peerResponse(String requestId) {
        return new CognitiveTaskResponse("tsk-" + requestId, requestId,
                new io.matrix.contract.Correlation("corr-" + requestId, "ep-parent",
                        "ep-" + requestId, requestId),
                "hash-" + requestId,
                "chain-b", "brain.cognitive-inference", "v1",
                CognitiveTaskResponse.Status.COMPLETED, "the lamp is lit", 0.9,
                CognitiveTaskResponse.ConfidenceBasis.DECLARED, List.of(), null, null,
                List.of(), List.of(), List.of(), List.of("BIR"),
                Provenance.of("chain-b", AT), "the capability ran", 5L, "trace-1", null, AT);
    }

    /** A transport that answers with a signed report. */
    private TaskPublisher.Dispatch answering(CognitiveTaskReport.Relation relation) {
        return new TaskPublisher.Dispatch("req-tsk-1", BridgeDecision.Status.ACCEPTED,
                "the message satisfies the contract",
                java.util.Optional.of(peerResponse("req-tsk-1")),
                java.util.Optional.empty(), true, false,
                java.util.Optional.of(peerReport("req-tsk-1", relation)));
    }

    @Test
    @DisplayName("the peer's report reaches chain A's epistemic store as a candidate")
    void theReportReachesTheEpistemicStore() {
        CognitiveCoordinator coordinator = new CognitiveCoordinator(store,
                (request, sentAt) -> answering(CognitiveTaskReport.Relation.SUPPORTS),
                new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                WORKSPACE, "chain-a", () -> AT)
                .withReportSink(envelope -> bridge().accept(envelope));

        coordinator.arrange(new CognitiveCoordinator.TaskSpec("tsk-1",
                "brain.cognitive-inference", "is the lamp lit", "the lamp is lit",
                5000L, 5000L, List.of()));

        List<io.matrix.knowledge.Claim> claims =
                workbench.storeForValidation().claims(WORKSPACE);
        assertEquals(1, claims.size(), "the report must be a record in the model");
        assertEquals(ClaimStatus.CANDIDATE, claims.get(0).status(),
                "a remote report is a candidate, whatever it says");
        assertEquals(0.0, claims.get(0).confidence(),
                "the peer's confidence is not this process's confidence");
    }

    @Test
    @DisplayName("the dispatch record says what happened to the report")
    void theDispatchRecordSaysWhatHappened() {
        CognitiveCoordinator coordinator = new CognitiveCoordinator(store,
                (request, sentAt) -> answering(CognitiveTaskReport.Relation.SUPPORTS),
                new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                WORKSPACE, "chain-a", () -> AT)
                .withReportSink(envelope -> bridge().accept(envelope));

        coordinator.arrange(new CognitiveCoordinator.TaskSpec("tsk-1",
                "brain.cognitive-inference", "is the lamp lit", "the lamp is lit",
                5000L, 5000L, List.of()));

        DispatchIntent dispatch = store.findDispatch("req-tsk-1").orElseThrow();
        assertNotNull(dispatch.reportDisposition(),
                "the admission of the report must be recorded beside the dispatch");
        assertTrue(dispatch.reportDisposition().startsWith("ACCEPTED"),
                dispatch.reportDisposition());
    }

    @Test
    @DisplayName("the verdict is chain A's own, and it does not follow the admission")
    void theVerdictDoesNotFollowTheAdmission() {
        CognitiveCoordinator coordinator = new CognitiveCoordinator(store,
                (request, sentAt) -> answering(CognitiveTaskReport.Relation.SUPPORTS),
                new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                WORKSPACE, "chain-a", () -> AT)
                .withReportSink(envelope -> bridge().accept(envelope));

        CognitiveCoordinator.Outcome outcome = coordinator.arrange(
                new CognitiveCoordinator.TaskSpec("tsk-1", "brain.cognitive-inference",
                        "is the lamp lit", "the lamp is lit", 5000L, 5000L, List.of()));

        assertEquals(RemoteReportAssessor.Disposition.UNRESOLVED,
                outcome.verdict().orElseThrow().disposition(),
                "an admitted report is not a supported proposition");
    }

    @Test
    @DisplayName("a report that contradicts is recorded as a contradiction")
    void aContradictionIsRecordedAsAContradiction() {
        CognitiveCoordinator coordinator = new CognitiveCoordinator(store,
                (request, sentAt) -> answering(CognitiveTaskReport.Relation.CONTRADICTS),
                new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                WORKSPACE, "chain-a", () -> AT)
                .withReportSink(envelope -> bridge().accept(envelope));

        coordinator.arrange(new CognitiveCoordinator.TaskSpec("tsk-1",
                "brain.cognitive-inference", "is the lamp lit", "the lamp is lit",
                5000L, 5000L, List.of()));

        List<io.matrix.knowledge.Evidence> evidence =
                workbench.storeForValidation().evidence(WORKSPACE);
        assertEquals(1, evidence.size());
        assertEquals(io.matrix.knowledge.EvidenceRelation.CONTRADICTS, evidence.get(0).relation(),
                "the relation the peer stated is the relation that is recorded");
    }

    /** The optional the fixtures use. */
    private static <T> java.util.Optional<T> optional(T value) {
        return java.util.Optional.ofNullable(value);
    }

    /** The request and the response the coordinator does not need here. */
    @SuppressWarnings("unused")
    private static CognitiveTaskRequest unusedRequest() {
        return null;
    }

    /** The response type the fixtures do not build. */
    @SuppressWarnings("unused")
    private static Class<?> responseType() {
        return CognitiveTaskResponse.class;
    }
}
