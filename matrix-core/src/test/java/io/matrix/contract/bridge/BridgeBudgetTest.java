package io.matrix.contract.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import static io.matrix.contract.bridge.BridgeTestFixtures.NOW_MILLIS;
import static io.matrix.contract.bridge.BridgeTestFixtures.NOW_MILLIS_LATER;
import static io.matrix.contract.bridge.BridgeTestFixtures.REQUEST_ID;
import static io.matrix.contract.bridge.BridgeTestFixtures.SECRET;
import static io.matrix.contract.bridge.BridgeTestFixtures.envelope;
import static io.matrix.contract.bridge.BridgeTestFixtures.payload;
import static io.matrix.contract.bridge.BridgeTestFixtures.signedEnvelope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 — BRIDGE-9: the budget is enforced before the store is touched.
 *
 * <p>Each of the four limits gets its own test, and each of them asserts the same two
 * things: the message is refused, and the refusal is recorded. The second half is the half
 * that is easy to forget — BRIDGE-7 says a refusal is written to the audit chain as
 * carefully as an acceptance, and a budget that refuses without recording is a bridge that
 * drops traffic silently.</p>
 */
class BridgeBudgetTest {

    private static BridgeReceiver receiver(BridgeBudget budget, BridgeTestFixtures.RecordingSink sink) {
        return new BridgeReceiver(new HmacBridgeSigner(SECRET),
                EnumSet.allOf(BridgeCapability.class), budget, new InMemoryBridgeIdempotency(),
                sink);
    }

    @Test
    @DisplayName("a message larger than the limit is refused, and the refusal is recorded")
    void anOversizedMessageIsRefused() {
        BridgeBudget budget = new BridgeBudget(10, Duration.ofMinutes(1), 64, 10, 100);
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(budget, sink);

        BridgeDecision decision = receiver.receive(signedEnvelope(), NOW_MILLIS);

        assertTrue(decision.refused());
        assertTrue(decision.reason().contains("bytes"), decision.reason());
        assertEquals(0, sink.count());
        assertEquals(1, receiver.auditLog().size());
        assertEquals(BridgeDecision.Status.REFUSED, receiver.auditLog().get(0).decision());
    }

    @Test
    @DisplayName("the rate window refuses the message that exceeds it")
    void theRateWindowIsEnforced() {
        BridgeBudget budget = new BridgeBudget(1, Duration.ofMinutes(1), 64 * 1024, 10, 100);
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(budget, sink);

        BridgeDecision first = receiver.receive(signedEnvelope(), NOW_MILLIS);
        BridgeDecision second = receiver.receive(signedEnvelope(envelope(payload(), "req-bridge-2")),
                NOW_MILLIS_LATER);

        assertTrue(first.accepted());
        assertTrue(second.refused());
        assertTrue(second.reason().contains("window"), second.reason());
        assertEquals(1, sink.count());
        assertEquals(2, receiver.auditLog().size());
    }

    @Test
    @DisplayName("the daily ceiling refuses the message that exceeds it")
    void theDailyCeilingIsEnforced() {
        BridgeBudget budget = new BridgeBudget(100, Duration.ofMinutes(1), 64 * 1024, 10, 1);
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(budget, sink);

        BridgeDecision first = receiver.receive(signedEnvelope(), NOW_MILLIS);
        BridgeDecision second = receiver.receive(signedEnvelope(envelope(payload(), "req-bridge-2")),
                NOW_MILLIS_LATER);

        assertTrue(first.accepted());
        assertTrue(second.refused());
        assertTrue(second.reason().contains("ceiling"), second.reason());
        assertEquals(1, sink.count());
    }

    @Test
    @DisplayName("the batch limit is enforced before anything is written")
    void theBatchLimitIsEnforced() {
        BridgeBudget budget = new BridgeBudget(100, Duration.ofMinutes(1), 64 * 1024, 1, 100);
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(budget, sink);

        List<BridgeEnvelope> batch = List.of(
                signedEnvelope(envelope(payload(), "req-bridge-1")),
                signedEnvelope(envelope(payload(), "req-bridge-2")));
        List<BridgeDecision> decisions = receiver.receiveBatch(batch, NOW_MILLIS);

        assertEquals(2, decisions.size());
        assertTrue(decisions.get(0).refused());
        assertTrue(decisions.get(1).refused());
        assertTrue(decisions.get(0).reason().contains("batch"), decisions.get(0).reason());
        assertEquals(0, sink.count());
        assertEquals(2, receiver.auditLog().size());
    }

    @Test
    @DisplayName("a batch inside the limit is admitted one message at a time")
    void aBatchInsideTheLimitIsAdmitted() {
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(BridgeBudget.standard(), sink);

        List<BridgeEnvelope> batch = List.of(
                signedEnvelope(envelope(payload(), "req-bridge-1")),
                signedEnvelope(envelope(payload(), "req-bridge-2")));
        List<BridgeDecision> decisions = receiver.receiveBatch(batch, NOW_MILLIS);

        assertEquals(2, decisions.size());
        assertTrue(decisions.get(0).accepted());
        assertTrue(decisions.get(1).accepted());
        assertEquals(2, sink.count());
    }

    @Test
    @DisplayName("a message refused for its size does not spend the day's allowance")
    void anOversizedMessageDoesNotSpendTheAllowance() {
        BridgeBudget budget = new BridgeBudget(100, Duration.ofMinutes(1), 1024, 10, 2);
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(budget, sink);

        BridgeDecision refused = receiver.receive(
                signedEnvelope(envelope(BridgeTestFixtures.hugePayload(), "req-bridge-big")),
                NOW_MILLIS);

        assertTrue(refused.refused());
        assertTrue(refused.reason().contains("bytes"), refused.reason());
        assertEquals(0, sink.count());

        BridgeDecision accepted = receiver.receive(signedEnvelope(), NOW_MILLIS);
        assertTrue(accepted.accepted(),
                "a message refused for its size must not spend the day's allowance");
        assertEquals(1, sink.count());
    }

    @Test
    @DisplayName("the rate window refuses everything that arrives until it rolls")
    void theRateWindowRefusesUntilItRolls() {
        BridgeBudget budget = new BridgeBudget(1, Duration.ofMinutes(1), 64 * 1024, 10, 100);
        BridgeTestFixtures.RecordingSink sink = new BridgeTestFixtures.RecordingSink();
        BridgeReceiver receiver = receiver(budget, sink);

        assertTrue(receiver.receive(signedEnvelope(), NOW_MILLIS).accepted());
        assertTrue(receiver.receive(signedEnvelope(envelope(payload(), "req-bridge-2")),
                NOW_MILLIS_LATER).refused());
        assertTrue(receiver.receive(signedEnvelope(envelope(payload(), "req-bridge-3")),
                NOW_MILLIS_LATER).refused());
        assertEquals(1, sink.count());
    }

    @Test
    @DisplayName("the budget's limits are named, and the default budget carries them")
    void theLimitsAreNamed() {
        BridgeBudget standard = BridgeBudget.standard();

        assertEquals(BridgeBudget.DEFAULT_MESSAGES_PER_WINDOW, standard.messagesPerWindow());
        assertEquals(BridgeBudget.DEFAULT_WINDOW, standard.window());
        assertEquals(BridgeBudget.DEFAULT_MAX_MESSAGE_BYTES, standard.maxMessageBytes());
        assertEquals(BridgeBudget.DEFAULT_MAX_BATCH, standard.maxBatch());
        assertEquals(BridgeBudget.DEFAULT_PER_DAY_CEILING, standard.perDayCeiling());
    }
}
