package io.matrix.contract.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static io.matrix.contract.bridge.BridgeTestFixtures.NOW_MILLIS;
import static io.matrix.contract.bridge.BridgeTestFixtures.REQUEST_ID;
import static io.matrix.contract.bridge.BridgeTestFixtures.SECRET;
import static io.matrix.contract.bridge.BridgeTestFixtures.SENT_AT;
import static io.matrix.contract.bridge.BridgeTestFixtures.SENDER_ID;
import static io.matrix.contract.bridge.BridgeTestFixtures.envelope;
import static io.matrix.contract.bridge.BridgeTestFixtures.payload;
import static io.matrix.contract.bridge.BridgeTestFixtures.signedEnvelope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 — BRIDGE-5 and BRIDGE-2: what the bridge may carry, and which version it speaks.
 *
 * <p>BRIDGE-5 is the safety boundary of the whole proposal: the bridge may add an
 * observation, a candidate or an evidence link, and nothing else. The tests here check that
 * boundary from both sides — that a kind outside the three is refused, and that a kind
 * inside the three which this particular bridge did not grant is also refused, because
 * "permitted by the protocol" and "granted to this sender" are different things.</p>
 *
 * <p>BRIDGE-2 is checked together with it because both are refusals, and a refusal that
 * cannot be told apart from another refusal is not an audit record.</p>
 */
class BridgeSafetyBoundaryTest {

    private static BridgeReceiver receiver(Set<BridgeCapability> granted, BridgeTestFixtures.RecordingSink sink) {
        return new BridgeReceiver(new HmacBridgeSigner(SECRET), granted, BridgeBudget.standard(),
                new InMemoryBridgeIdempotency(), sink);
    }

    @Test
    @DisplayName("a payload whose kind is not one of the three is refused")
    void unknownKindIsRefused() {
        BridgePayloadCodec codec = new BridgePayloadCodec();
        String json = codec.encode(BridgeTestFixtures.payload())
                .replace("\"OBSERVATION\"", "\"ADJUST_CLAIM\"");

        BridgeProtocolException failure =
                assertThrows(BridgeProtocolException.class, () -> codec.decode(json));

        assertEquals(BridgeRefusalReason.UNKNOWN_PAYLOAD_KIND, failure.reason());
    }

    @Test
    @DisplayName("a message whose payload names an unknown kind is refused by the receiver")
    void unknownKindIsRefusedByTheReceiver() {
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(EnumSet.allOf(BridgeCapability.class), sink);
        String json = new BridgePayloadCodec().encode(BridgeTestFixtures.payload())
                .replace("\"OBSERVATION\"", "\"ADJUST_CLAIM\"");
        BridgeEnvelope unsigned = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                REQUEST_ID, SENT_AT, SENDER_ID, BridgeCapability.OBSERVATION, json, null);
        BridgeEnvelope envelope = unsigned.withSignature(unsigned.sign(SECRET));

        BridgeDecision decision = receiver.receive(envelope, NOW_MILLIS);

        assertTrue(decision.refused());
        assertTrue(decision.reason().contains("kind"), decision.reason());
        assertEquals(0, sink.count());
        assertEquals(1, receiver.auditLog().size());
    }

    @Test
    @DisplayName("a kind the bridge did not grant is refused")
    void ungrantedKindIsRefused() {
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(Set.of(BridgeCapability.OBSERVATION), sink);
        BridgeEnvelope unsigned = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                REQUEST_ID, SENT_AT, SENDER_ID, BridgeCapability.CANDIDATE,
                new BridgePayloadCodec().encode(BridgeTestFixtures.payload()), null);
        BridgeEnvelope envelope = unsigned.withSignature(unsigned.sign(SECRET));

        BridgeDecision decision = receiver.receive(envelope, NOW_MILLIS);

        assertTrue(decision.refused());
        assertTrue(decision.reason().contains("did not grant"), decision.reason());
        assertEquals(0, sink.count());
    }

    @Test
    @DisplayName("the capability check asks whether the right was granted, not whether it is forbidden")
    void capabilityCheckIsPositive() {
        Set<BridgeCapability> granted = Set.of(BridgeCapability.OBSERVATION);

        assertTrue(BridgeCapability.OBSERVATION.grantedBy(granted));
        assertFalse(BridgeCapability.CANDIDATE.grantedBy(granted));
        assertFalse(BridgeCapability.EVIDENCE_LINK.grantedBy(granted));
        assertFalse(BridgeCapability.OBSERVATION.grantedBy(Set.of()));
        assertFalse(BridgeCapability.OBSERVATION.grantedBy(null));
    }

    @Test
    @DisplayName("a version this reader does not know is refused")
    void unknownVersionIsRefused() {
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(EnumSet.allOf(BridgeCapability.class), sink);
        BridgeEnvelope unsigned = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION + 1,
                REQUEST_ID, SENT_AT, SENDER_ID, BridgeCapability.OBSERVATION,
                new BridgePayloadCodec().encode(BridgeTestFixtures.payload()), null);
        BridgeEnvelope envelope = unsigned.withSignature(unsigned.sign(SECRET));

        BridgeDecision decision = receiver.receive(envelope, NOW_MILLIS);

        assertTrue(decision.refused());
        assertTrue(decision.reason().contains("version"), decision.reason());
        assertEquals(0, sink.count());
        assertEquals(1, receiver.auditLog().size());
    }

    @Test
    @DisplayName("a protocol this reader does not know is refused")
    void unknownProtocolIsRefused() {
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(EnumSet.allOf(BridgeCapability.class), sink);
        BridgeEnvelope unsigned = new BridgeEnvelope("matrix.something-else", BridgeProtocol.VERSION,
                REQUEST_ID, SENT_AT, SENDER_ID, BridgeCapability.OBSERVATION,
                new BridgePayloadCodec().encode(BridgeTestFixtures.payload()), null);
        BridgeEnvelope envelope = unsigned.withSignature(unsigned.sign(SECRET));

        BridgeDecision decision = receiver.receive(envelope, NOW_MILLIS);

        assertTrue(decision.refused());
        assertTrue(decision.reason().contains("protocol"), decision.reason());
        assertEquals(0, sink.count());
    }

    @Test
    @DisplayName("two versions are compatible only when they are the same version")
    void compatibilityIsExact() {
        assertTrue(BridgeProtocol.compatible(BridgeProtocol.VERSION, BridgeProtocol.VERSION));
        assertFalse(BridgeProtocol.compatible(BridgeProtocol.VERSION + 1, BridgeProtocol.VERSION));
        assertFalse(BridgeProtocol.compatible(BridgeProtocol.VERSION, BridgeProtocol.VERSION + 1));
        assertTrue(BridgeProtocol.supportedVersion(BridgeProtocol.VERSION));
        assertFalse(BridgeProtocol.supportedVersion(BridgeProtocol.VERSION + 1));
    }

    @Test
    @DisplayName("the payload kind and the envelope kind must agree")
    void payloadKindMustAgreeWithEnvelopeKind() {
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(EnumSet.allOf(BridgeCapability.class), sink);
        BridgeEnvelope unsigned = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                REQUEST_ID, SENT_AT, SENDER_ID, BridgeCapability.CANDIDATE,
                new BridgePayloadCodec().encode(BridgeTestFixtures.payload()), null);
        BridgeEnvelope envelope = unsigned.withSignature(unsigned.sign(SECRET));

        BridgeDecision decision = receiver.receive(envelope, NOW_MILLIS);

        assertTrue(decision.refused());
        assertTrue(decision.reason().contains("does not agree"), decision.reason());
        assertEquals(0, sink.count());
    }

    @Test
    @DisplayName("a message with no provenance is refused")
    void missingProvenanceIsRefused() {
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(EnumSet.allOf(BridgeCapability.class), sink);
        BridgePayload payload = BridgePayload.ofObservation(BridgeTestFixtures.observation(), null);
        BridgeEnvelope unsigned = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                REQUEST_ID, SENT_AT, SENDER_ID, BridgeCapability.OBSERVATION,
                new BridgePayloadCodec().encode(payload), null);
        BridgeEnvelope envelope = unsigned.withSignature(unsigned.sign(SECRET));

        BridgeDecision decision = receiver.receive(envelope, NOW_MILLIS);

        assertTrue(decision.refused());
        assertTrue(decision.reason().contains("provenance"), decision.reason());
        assertEquals(0, sink.count());
    }

    @Test
    @DisplayName("a message signed with another secret is refused")
    void aForgedMessageIsRefused() {
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(EnumSet.allOf(BridgeCapability.class), sink);
        BridgeEnvelope forged = signedEnvelope().withSignature(
                signedEnvelope().sign(BridgeTestFixtures.OTHER_SECRET));

        BridgeDecision decision = receiver.receive(forged, NOW_MILLIS);

        assertTrue(decision.refused());
        assertTrue(decision.reason().contains("signature"), decision.reason());
        assertEquals(0, sink.count());
    }
}
