package io.matrix.bridge;

import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskReport;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.EvidenceRelation;
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
 * W53 — the remote report reaches the epistemic store as a candidate, with the relation
 * the peer stated.
 *
 * <p><b>The defect this closes.</b> The research track on cooperative reasoning found
 * that the sink wrote the evidence relation as {@code SUPPORTS} unconditionally. A remote
 * report that <i>denied</i> an established belief could therefore only ever arrive as
 * support for a new candidate, and never reached the refutation path — the epistemic
 * model could not be told "no" by the peer, only "yes". The report now carries the
 * relation, and these tests say what each one produces.</p>
 *
 * <p><b>What is unchanged.</b> The report is still a candidate. A peer that is confident,
 * that is right, and that contradicts everything chain A believed still cannot promote
 * itself: the store's promotion policy, not the peer's confidence, is what holds that
 * line.</p>
 */
class TaskReportSinkTest {

    /** The workspace the records are written into. */
    private static final String WORKSPACE = "bridged";

    /** The instant every fixture claims. */
    private static final String AT = "2026-11-09T12:00:00Z";

    /** The shared secret of the tests. */
    private static final byte[] SECRET =
            "the-bridge-secret-of-the-tests".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    private KnowledgeWorkbench workbench;
    private io.matrix.contract.bridge.HmacBridgeSigner signer;

    @BeforeEach
    void openTheStore() {
        workbench = KnowledgeWorkbench.at(directory.resolve("knowledge.sqlite").toString());
        signer = new io.matrix.contract.bridge.HmacBridgeSigner(SECRET);
    }

    @AfterEach
    void closeTheStore() {
        workbench.close();
    }

    private BridgeService service() {
        return new BridgeService(workbench, signer, EnumSet.allOf(BridgeCapability.class),
                io.matrix.contract.bridge.BridgeBudget.standard(),
                new io.matrix.contract.bridge.InMemoryBridgeIdempotency(), audit(),
                WORKSPACE, () -> AT);
    }

    /**
     * The audit trail of the test: every decision is recorded, and the trail is kept in
     * memory because these tests are about what reaches the epistemic store.
     */
    private BridgeAudit audit() {
        return record -> {
        };
    }

    private BridgeEnvelope report(String requestId, CognitiveTaskReport.Relation relation,
                                  List<String> derivedFrom) {
        CognitiveTaskReport payload = new CognitiveTaskReport(BridgeProtocol.VERSION,
                "tsk-" + requestId, requestId, "the lamp is lit", null, relation, 0.9,
                CognitiveTaskReport.ConfidenceBasis.DECLARED, derivedFrom, "chain-b",
                "brain.cognitive-inference", "v1", List.of("the lamp is dark"),
                Provenance.of("chain-b", AT), AT);
        BridgeEnvelope envelope = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                requestId, AT, "chain-b", BridgeCapability.INFERENCE_RESPONSE,
                new BridgePayloadCodec().encode(BridgePayload.ofTaskReport(payload,
                        Provenance.of("chain-b", AT))), null);
        return envelope.withSignature(envelope.sign(signer));
    }

    @Test
    @DisplayName("a report that contradicts arrives as a candidate with a contradicting link")
    void aContradictionArrivesAsAContradiction() {
        BridgeService service = service();

        BridgeDecision decision = service.accept(report("req-c",
                CognitiveTaskReport.Relation.CONTRADICTS, List.of()));

        assertTrue(decision.accepted());
        List<io.matrix.knowledge.Claim> claims =
                workbench.storeForValidation().claims(WORKSPACE);
        assertEquals(1, claims.size());
        assertEquals(ClaimStatus.CANDIDATE, claims.get(0).status(),
                "a report is a candidate, whatever it says");
        assertEquals(0.0, claims.get(0).confidence(),
                "a peer's confidence is not this process's confidence");
        List<io.matrix.knowledge.Evidence> evidence =
                workbench.storeForValidation().evidence(WORKSPACE);
        assertEquals(1, evidence.size());
        assertEquals(EvidenceRelation.CONTRADICTS, evidence.get(0).relation(),
                "the relation the peer stated is the relation that is recorded");
    }

    @Test
    @DisplayName("a report that supports arrives with a supporting link")
    void aSupportArrivesAsSupport() {
        BridgeService service = service();

        service.accept(report("req-s", CognitiveTaskReport.Relation.SUPPORTS, List.of()));

        List<io.matrix.knowledge.Evidence> evidence =
                workbench.storeForValidation().evidence(WORKSPACE);
        assertEquals(1, evidence.size());
        assertEquals(EvidenceRelation.SUPPORTS, evidence.get(0).relation());
    }

    @Test
    @DisplayName("a report that asserts no relation is contextual, never support")
    void anIndependentReportIsNeverSupport() {
        BridgeService service = service();

        service.accept(report("req-i", CognitiveTaskReport.Relation.INDEPENDENT, List.of()));

        List<io.matrix.knowledge.Evidence> evidence =
                workbench.storeForValidation().evidence(WORKSPACE);
        assertEquals(1, evidence.size());
        assertEquals(EvidenceRelation.CONTEXTUAL, evidence.get(0).relation(),
                "the sink must not invent a support relation");
    }

    @Test
    @DisplayName("the lineage the peer declared stays visible in the claim")
    void theLineageStaysVisible() {
        BridgeService service = service();

        service.accept(report("req-l", CognitiveTaskReport.Relation.SUPPORTS,
                List.of("clm-supplied-7", "clm-supplied-8")));

        List<io.matrix.knowledge.Claim> claims =
                workbench.storeForValidation().claims(WORKSPACE);
        assertEquals(1, claims.size());
        String notes = claims.get(0).extractionMethod();
        assertNotNull(notes);
        assertTrue(notes.contains("clm-supplied-7"), notes);
        assertTrue(notes.contains("clm-supplied-8"), notes);
    }
}
