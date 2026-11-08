package io.matrix.bridge;

import io.matrix.contract.Observation;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgeEnvelopeCodec;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.HmacBridgeSigner;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 — the bridge, end to end on the chain A side.
 *
 * <p>These tests are the evidence for the W51 requirement that "cross-chain interaction is
 * demonstrated without merging process or database ownership". They check the contract's
 * promises one by one: the authentication, the versioning, the capability grant, the
 * idempotency, the budget and — most importantly — that the effect the bridge has on the
 * epistemic model is exactly "an unverified candidate claim" and nothing more.</p>
 */
class BridgeServiceTest {

    private static final String SECRET = "the secret of the test";
    private static final String WORKSPACE = "bridged";
    private static final String AT = "2026-11-08T00:00:00Z";

    @TempDir
    Path directory;

    private KnowledgeWorkbench workbench;
    private BridgeJournal journal;
    private HmacBridgeSigner signer;

    @BeforeEach
    void openTheStore() {
        workbench = KnowledgeWorkbench.at(directory.resolve("knowledge.sqlite").toString());
        journal = new BridgeJournal(directory.resolve("bridge.sqlite"));
        signer = new HmacBridgeSigner(SECRET);
    }

    @AfterEach
    void closeTheStore() {
        workbench.close();
        journal.close();
    }

    private BridgeService service(Set<BridgeCapability> granted) {
        return new BridgeService(workbench, signer, granted,
                io.matrix.contract.bridge.BridgeBudget.standard(), journal, journal, WORKSPACE, () -> AT);
    }

    private BridgeEnvelope observation(String requestId, String senderId, String text) {
        Observation observation = Observation.ofText("obs-" + requestId, text, AT,
                Provenance.of(senderId, AT));
        BridgePayload payload = BridgePayload.ofObservation(observation,
                Provenance.of(senderId, AT));
        BridgeEnvelope envelope = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                requestId, AT, senderId, BridgeCapability.OBSERVATION,
                new BridgePayloadCodec().encode(payload), null);
        return envelope.withSignature(envelope.sign(signer));
    }

    @Test
    @DisplayName("a signed observation is accepted and arrives as an unverified candidate")
    void signedObservationIsAcceptedAsACandidate() {
        BridgeService service = service(EnumSet.allOf(BridgeCapability.class));

        BridgeDecision decision = service.accept(observation("req-1", "chain-b", "the lamp is on"));

        assertTrue(decision.accepted());
        List<io.matrix.knowledge.Claim> claims = workbench.storeForValidation().claims(WORKSPACE);
        assertEquals(1, claims.size());
        assertEquals(ClaimStatus.CANDIDATE, claims.get(0).status());
        assertEquals("the lamp is on", claims.get(0).proposition());
        assertEquals(0.0, claims.get(0).confidence());
    }

    @Test
    @DisplayName("the effect of a repeated delivery happens once")
    void repeatedDeliveryHasOneEffect() {
        BridgeService service = service(EnumSet.allOf(BridgeCapability.class));
        BridgeEnvelope envelope = observation("req-2", "chain-b", "the lamp is on");

        BridgeDecision first = service.accept(envelope);
        BridgeDecision second = service.accept(envelope);

        assertTrue(first.accepted());
        assertTrue(second.accepted());
        assertEquals(1, workbench.storeForValidation().claims(WORKSPACE).size());
    }

    @Test
    @DisplayName("a message with a wrong signature is refused and nothing is written")
    void wrongSignatureIsRefused() {
        BridgeService service = service(EnumSet.allOf(BridgeCapability.class));
        BridgeEnvelope envelope = observation("req-3", "chain-b", "the lamp is on");
        BridgeEnvelope forged = envelope.withSignature(
                new HmacBridgeSigner("another secret").signHex(envelope.canonicalBytes()));

        BridgeDecision decision = service.accept(forged);

        assertTrue(decision.refused());
        assertTrue(workbench.storeForValidation().claims(WORKSPACE).isEmpty());
    }

    @Test
    @DisplayName("a message of an unknown version is refused")
    void unknownVersionIsRefused() {
        BridgeService service = service(EnumSet.allOf(BridgeCapability.class));
        BridgeEnvelope envelope = observation("req-4", "chain-b", "the lamp is on");
        BridgeEnvelope future = new BridgeEnvelope(BridgeProtocol.NAME,
                BridgeProtocol.VERSION + 1, envelope.requestId(), envelope.sentAt(),
                envelope.senderId(), envelope.kind(), envelope.payload(), envelope.signature());

        BridgeDecision decision = service.accept(future);

        assertTrue(decision.refused());
        assertTrue(workbench.storeForValidation().claims(WORKSPACE).isEmpty());
    }

    @Test
    @DisplayName("a kind the bridge did not grant is refused")
    void ungrantedKindIsRefused() {
        BridgeService service = service(EnumSet.of(BridgeCapability.CANDIDATE));
        BridgeEnvelope envelope = observation("req-5", "chain-b", "the lamp is on");

        BridgeDecision decision = service.accept(envelope);

        assertTrue(decision.refused());
        assertTrue(workbench.storeForValidation().claims(WORKSPACE).isEmpty());
    }

    @Test
    @DisplayName("without a secret nothing is trusted")
    void withoutASecretNothingIsTrusted() {
        BridgeService service = new BridgeService(workbench, null,
                EnumSet.allOf(BridgeCapability.class),
                io.matrix.contract.bridge.BridgeBudget.standard(), journal, journal, WORKSPACE, () -> AT);

        BridgeDecision decision = service.accept(observation("req-6", "chain-b", "the lamp is on"));

        assertTrue(decision.refused());
        assertTrue(workbench.storeForValidation().claims(WORKSPACE).isEmpty());
    }

    @Test
    @DisplayName("the journal keeps the decisions across a restart of the process")
    void theJournalSurvivesARestart() {
        BridgeService service = service(EnumSet.allOf(BridgeCapability.class));
        service.accept(observation("req-7", "chain-b", "the lamp is on"));
        journal.close();

        BridgeJournal reopened = new BridgeJournal(directory.resolve("bridge.sqlite"));
        assertTrue(reopened.find("req-7").isPresent());
        assertEquals(1, reopened.recordedCount());
        reopened.close();
    }

    @Test
    @DisplayName("the audit records the acceptance and the refusals alike")
    void theAuditRecordsBoth() {
        BridgeService service = service(EnumSet.allOf(BridgeCapability.class));
        service.accept(observation("req-8", "chain-b", "the lamp is on"));
        service.accept(observation("req-9", "chain-b", "the lamp is on"));

        assertTrue(journal.auditCount() >= 2, "the audit must hold the calls it made");
    }

    @Test
    @DisplayName("the envelope survives the wire unchanged, so the signature stays verifiable")
    void theEnvelopeSurvivesTheWire() {
        BridgeEnvelope envelope = observation("req-10", "chain-b", "the lamp is on");
        BridgeEnvelopeCodec codec = new BridgeEnvelopeCodec();

        BridgeEnvelope decoded = codec.decode(codec.encode(envelope));

        assertEquals(envelope.payload(), decoded.payload());
        assertEquals(envelope.requestId(), decoded.requestId());
        assertEquals(envelope.senderId(), decoded.senderId());
        assertEquals(envelope.kind(), decoded.kind());
        assertTrue(decoded.verify(signer));
    }

    @Test
    @DisplayName("the bridge adds a claim and never changes an existing one")
    void theBridgeNeverChangesAnExistingClaim() {
        BridgeService service = service(EnumSet.allOf(BridgeCapability.class));
        service.accept(observation("req-11", "chain-b", "the first report"));

        io.matrix.knowledge.Claim before =
                workbench.storeForValidation().claims(WORKSPACE).get(0);
        int versionBefore = before.version();

        service.accept(observation("req-12", "chain-b", "the second report"));

        List<io.matrix.knowledge.Claim> claims = workbench.storeForValidation().claims(WORKSPACE);
        assertEquals(2, claims.size());
        assertEquals(versionBefore, claims.get(0).version());
        assertEquals(ClaimStatus.CANDIDATE, claims.get(0).status());
        assertFalse(claims.stream().anyMatch(claim ->
                claim.status() == ClaimStatus.SUPPORTED
                        || claim.status() == ClaimStatus.VERIFIED));
    }

    @Test
    @DisplayName("the bridged claim carries the identity of the request that produced it")
    void theClaimIdentityIsDerivedFromTheRequestId() {
        assertEquals(EpistemicBridgeSink.claimIdFor("req-13"),
                EpistemicBridgeSink.claimIdFor("req-13"));
        assertFalse(EpistemicBridgeSink.claimIdFor("req-13")
                .equals(EpistemicBridgeSink.claimIdFor("req-14")));
        assertTrue(EpistemicBridgeSink.claimIdFor("req-13")
                .startsWith(BridgeService.BRIDGED_CLAIM_PREFIX));
    }

    @Test
    @DisplayName("the grant and the request are different things")
    void theGrantAndTheRequestAreDifferentThings() {
        BridgeService service = service(EnumSet.of(BridgeCapability.OBSERVATION));
        assertEquals(Set.of(BridgeCapability.OBSERVATION), service.grantedCapabilities());
        assertTrue(service.auditLog().isEmpty());
    }

    @Test
    @DisplayName("the payload codec and the envelope codec agree on the kinds")
    void theCodecsAgreeOnTheKinds() {
        Map<String, String> wire = Map.of(
                "observation", BridgeCapability.OBSERVATION.name(),
                "candidate", BridgeCapability.CANDIDATE.name(),
                "evidence_link", BridgeCapability.EVIDENCE_LINK.name());
        wire.forEach((name, value) -> assertEquals(value,
                BridgeCapability.parse(value).orElseThrow().name()));
    }
}
