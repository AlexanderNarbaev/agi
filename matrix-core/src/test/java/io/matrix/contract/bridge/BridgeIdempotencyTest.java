package io.matrix.contract.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import static io.matrix.contract.bridge.BridgeTestFixtures.NOW_MILLIS;
import static io.matrix.contract.bridge.BridgeTestFixtures.REQUEST_ID;
import static io.matrix.contract.bridge.BridgeTestFixtures.SECRET;
import static io.matrix.contract.bridge.BridgeTestFixtures.envelope;
import static io.matrix.contract.bridge.BridgeTestFixtures.payload;
import static io.matrix.contract.bridge.BridgeTestFixtures.signedEnvelope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 — BRIDGE-8: the bridge is idempotent per request id.
 *
 * <p>Two promises are proved here, and they are separate. The first is that a repeated
 * delivery has no second effect: the sink is called once, and the number of records the
 * bridge wrote does not grow. The second is that the retry gets the <i>first</i> outcome
 * rather than a new one — the same decision, with the same audit id, because it is the same
 * logical call.</p>
 *
 * <p>The conflict case is proved too, because it is the case a naive implementation gets
 * wrong: a request id that arrives twice with two different payloads is not a retry, and
 * answering it with the first outcome would hide a sender that disagrees with itself.</p>
 */
class BridgeIdempotencyTest {

    private static BridgeReceiver receiver(InMemoryBridgeIdempotency registry,
                                           BridgeTestFixtures.RecordingSink sink) {
        return new BridgeReceiver(new HmacBridgeSigner(SECRET),
                EnumSet.allOf(BridgeCapability.class), BridgeBudget.standard(), registry, sink);
    }

    @Test
    @DisplayName("a repeated delivery gives the same result and no second effect")
    void aRepeatedDeliveryHasNoSecondEffect() {
        InMemoryBridgeIdempotency registry = new InMemoryBridgeIdempotency();
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(registry, sink);
        BridgeEnvelope message = signedEnvelope();

        BridgeDecision first = receiver.receive(message, NOW_MILLIS);
        BridgeDecision second = receiver.receive(message, NOW_MILLIS);

        assertTrue(first.accepted());
        assertTrue(second.accepted());
        assertEquals(first.auditId(), second.auditId());
        assertEquals(first.reason(), second.reason());
        assertEquals(1, sink.count(), "the sink must be called once, not twice");
        assertEquals(1, registry.recordedCount());
        assertEquals(1, receiver.auditLog().size(),
                "a repeated delivery is the same call, so it is recorded once");
    }

    @Test
    @DisplayName("a repeated delivery does not consume a second slot of the budget")
    void aRepeatedDeliveryDoesNotSpendTheBudget() {
        BridgeBudget budget = new BridgeBudget(1, Duration.ofMinutes(1), 64 * 1024, 10, 100);
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = new BridgeReceiver(new HmacBridgeSigner(SECRET),
                EnumSet.allOf(BridgeCapability.class), budget, new InMemoryBridgeIdempotency(), sink);
        BridgeEnvelope message = signedEnvelope();

        assertTrue(receiver.receive(message, NOW_MILLIS).accepted());
        assertTrue(receiver.receive(message, NOW_MILLIS).accepted());
        assertTrue(receiver.receive(signedEnvelope(envelope(payload(), "req-bridge-2")),
                NOW_MILLIS).refused(),
                "the retry did not spend the window, so the next distinct message does");
    }

    @Test
    @DisplayName("the retry gets the first outcome, not a new one")
    void theRetryGetsTheFirstOutcome() {
        InMemoryBridgeIdempotency registry = new InMemoryBridgeIdempotency();
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(registry, sink);
        BridgeEnvelope message = signedEnvelope();

        BridgeDecision first = receiver.receive(message, NOW_MILLIS);
        BridgeDecision replay = receiver.receive(message, NOW_MILLIS);

        assertSame(first, replay, "the registry returns the recorded decision itself");
    }

    @Test
    @DisplayName("a request id reused for a different payload is refused as a conflict")
    void aReusedRequestIdIsAConflict() {
        InMemoryBridgeIdempotency registry = new InMemoryBridgeIdempotency();
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(registry, sink);

        BridgeDecision first = receiver.receive(signedEnvelope(), NOW_MILLIS);
        BridgeEnvelope different = signedEnvelope(
                envelope(BridgeTestFixtures.multiKeyPayload(), REQUEST_ID));
        BridgeDecision conflict = receiver.receive(different, NOW_MILLIS);

        assertTrue(first.accepted());
        assertTrue(conflict.refused());
        assertTrue(conflict.reason().contains("different payload"), conflict.reason());
        assertNotEquals(first.auditId(), conflict.auditId());
        assertEquals(1, sink.count(), "a conflict must not write anything");
        assertEquals(2, receiver.auditLog().size(),
                "the conflict is a call of its own and is recorded as one");
    }

    @Test
    @DisplayName("the registry runs the first delivery once, whatever the caller does afterwards")
    void theRegistryComputesTheOutcomeOnce() {
        InMemoryBridgeIdempotency registry = new InMemoryBridgeIdempotency();
        int[] calls = {0};

        BridgeIdempotency.Outcome first = registry.firstOutcome(REQUEST_ID, "digest", () -> {
            calls[0]++;
            return new BridgeIdempotency.Outcome(
                    BridgeDecision.accepted(REQUEST_ID, "audit-1"), "digest");
        });
        BridgeIdempotency.Outcome second = registry.firstOutcome(REQUEST_ID, "digest", () -> {
            calls[0]++;
            return new BridgeIdempotency.Outcome(
                    BridgeDecision.accepted(REQUEST_ID, "audit-2"), "digest");
        });

        assertEquals(1, calls[0]);
        assertEquals(first.decision().auditId(), second.decision().auditId());
        assertEquals(1, registry.recordedCount());
    }

    @Test
    @DisplayName("the registry refuses a blank request id")
    void theRegistryRefusesABlankRequestId() {
        InMemoryBridgeIdempotency registry = new InMemoryBridgeIdempotency();

        assertTrue(registry.find("").isEmpty());
        assertTrue(registry.find(null).isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> registry.firstOutcome("  ", "digest", () -> null));
    }

    @Test
    @DisplayName("every call the bridge makes or refuses is recorded")
    void everyCallIsRecorded() {
        InMemoryBridgeIdempotency registry = new InMemoryBridgeIdempotency();
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(registry, sink);

        receiver.receive(signedEnvelope(), NOW_MILLIS);
        receiver.receive(signedEnvelope(envelope(payload(), "req-bridge-2")), NOW_MILLIS);
        receiver.receive(signedEnvelope(envelope(payload(), "req-bridge-3")), NOW_MILLIS);

        List<BridgeAuditRecord> records = receiver.auditLog();
        assertEquals(3, records.size());
        for (BridgeAuditRecord record : records) {
            assertFalse(record.reason().isBlank());
            assertEquals(BridgeProtocol.VERSION, record.contractVersion());
            assertFalse(record.payloadDigest().isBlank());
            assertEquals(BridgeCapability.OBSERVATION, record.kind());
            assertEquals(BridgeTestFixtures.SENDER_ID, record.senderId());
        }
    }
}
