package io.matrix.contract.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static io.matrix.contract.bridge.BridgeTestFixtures.NOW_MILLIS;
import static io.matrix.contract.bridge.BridgeTestFixtures.REQUEST_ID;
import static io.matrix.contract.bridge.BridgeTestFixtures.SECRET;
import static io.matrix.contract.bridge.BridgeTestFixtures.envelope;
import static io.matrix.contract.bridge.BridgeTestFixtures.payload;
import static io.matrix.contract.bridge.BridgeTestFixtures.signedEnvelope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 — the accepted path of the bridge, and the refusal that has no secret.
 *
 * <p>The other test classes check the refusals one by one. This one checks the path that
 * works, because a bridge that refuses everything would pass every refusal test: the
 * accepted message must reach the sink, be answered with an acceptance, and leave exactly
 * one audit record behind.</p>
 *
 * <p>It also checks the refusal that is not about the message at all: a receiver with no
 * secret configured. BRIDGE-3 says an unauthenticated call is refused, and the honest way to
 * refuse every call is to have no secret — not to accept them all.</p>
 */
class BridgeReceiverTest {

    private static BridgeReceiver receiver(BridgeSigner verifier,
                                           BridgeTestFixtures.RecordingSink sink) {
        return new BridgeReceiver(verifier, EnumSet.allOf(BridgeCapability.class),
                BridgeBudget.standard(), new InMemoryBridgeIdempotency(), sink);
    }

    @Test
    @DisplayName("a message that satisfies the contract is accepted and reaches the sink")
    void anAcceptedMessageReachesTheSink() {
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(new HmacBridgeSigner(SECRET), sink);
        BridgeEnvelope message = signedEnvelope();

        BridgeDecision decision = receiver.receive(message, NOW_MILLIS);

        assertTrue(decision.accepted());
        assertEquals(REQUEST_ID, decision.requestId());
        assertNotNull(decision.auditId());
        assertEquals(BridgeDecision.ACCEPTED_REASON, decision.reason());
        assertEquals(1, sink.count());
        assertEquals(message, sink.accepted().get(0));
    }

    @Test
    @DisplayName("the acceptance is recorded with the digest of the payload")
    void theAcceptanceIsRecorded() {
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(new HmacBridgeSigner(SECRET), sink);
        BridgeEnvelope message = signedEnvelope();

        BridgeDecision decision = receiver.receive(message, NOW_MILLIS);

        assertEquals(1, receiver.auditLog().size());
        BridgeAuditRecord record = receiver.auditLog().get(0);
        assertEquals(decision.auditId(), record.auditId());
        assertEquals(REQUEST_ID, record.requestId());
        assertEquals(BridgeDecision.Status.ACCEPTED, record.decision());
        assertEquals(BridgeProtocol.VERSION, record.contractVersion());
        assertEquals(message.payloadDigest(), record.payloadDigest());
        assertEquals(BridgeCapability.OBSERVATION, record.kind());
        assertFalse(record.at().isBlank());
    }

    @Test
    @DisplayName("a receiver with no secret refuses every message")
    void aReceiverWithNoSecretRefusesEverything() {
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(null, sink);

        BridgeDecision decision = receiver.receive(signedEnvelope(), NOW_MILLIS);

        assertTrue(decision.refused());
        assertTrue(decision.reason().contains("secret"), decision.reason());
        assertEquals(0, sink.count());
        assertEquals(1, receiver.auditLog().size());
    }

    @Test
    @DisplayName("the audit chain is append-only: a caller cannot change it")
    void theAuditChainIsAppendOnly() {
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(new HmacBridgeSigner(SECRET), sink);
        receiver.receive(signedEnvelope(), NOW_MILLIS);

        var chain = receiver.auditLog();
        assertEquals(1, chain.size());
        assertThrowsOnMutation(chain);
    }

    private static void assertThrowsOnMutation(java.util.List<BridgeAuditRecord> chain) {
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> chain.add(null));
    }

    @Test
    @DisplayName("the receiver writes nothing it was not asked to write")
    void theReceiverWritesOnlyWhatItWasGiven() {
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(new HmacBridgeSigner(SECRET), sink);

        receiver.receive(signedEnvelope(envelope(payload(), "req-bridge-1")), NOW_MILLIS);
        receiver.receive(signedEnvelope(envelope(payload(), "req-bridge-2")), NOW_MILLIS);

        assertEquals(2, sink.count());
        assertEquals(2, receiver.auditLog().size());
        assertEquals("req-bridge-1", sink.accepted().get(0).requestId());
        assertEquals("req-bridge-2", sink.accepted().get(1).requestId());
    }
}
