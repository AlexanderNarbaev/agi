package io.matrix.contract.bridge;

import io.matrix.contract.Belief;
import io.matrix.contract.BeliefStatus;
import io.matrix.contract.Confidence;
import io.matrix.contract.Provenance;
import io.matrix.contract.ValidityInterval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W52 — the SECOND direction of the bridge, at the level of the protocol.
 *
 * <p><b>What is pinned here.</b> The right names the direction; a receiver that did not
 * grant the right refuses the message; a receiver that granted it accepts the message and
 * reports what it did with it; a retry produces the same answer and no second effect; a
 * forged signature is refused; and the version rule says exactly which versions this build
 * reads.</p>
 */
class KnowledgeDirectionTest {

    /** The shared secret of the tests. */
    private static final byte[] SECRET =
            "the-bridge-secret-of-the-tests".getBytes(StandardCharsets.UTF_8);

    /** A secret that is not the right one. */
    private static final byte[] OTHER_SECRET =
            "a-different-secret".getBytes(StandardCharsets.UTF_8);

    /** The instant every fixture claims to have been written at. */
    private static final String SENT_AT = "2026-11-09T10:00:00Z";

    /** The instant every fixture is delivered at. */
    private static final long NOW_MILLIS = 1_763_049_600_000L;

    /** The identity of the sender — chain A this time. */
    private static final String SENDER_ID = "chain-a-epistemic-core";

    /** The request id of the first message. */
    private static final String REQUEST_ID = "req-knowledge-1";

    /** An established belief. */
    private static Belief belief() {
        return new Belief("belief-1", "the lamp is lit when power and switch are both on",
                BeliefStatus.SUPPORTED,
                new Confidence(0.9, Confidence.Basis.MEASURED, 3, 0, "three observations agree",
                        List.of()),
                List.of("ev-1", "ev-2", "ev-3"), List.of(),
                Provenance.of("the cognitive kernel", SENT_AT),
                ValidityInterval.openFrom(SENT_AT), 1, null, null,
                "the model settled on this hypothesis");
    }

    /** A payload carrying the established belief. */
    private static BridgePayload payload() {
        return BridgePayload.ofKnowledge(belief(), Provenance.of("chain-a", SENT_AT));
    }

    /** A signed envelope carrying the established belief. */
    private static BridgeEnvelope signed() {
        return signed(requestId -> requestId, payload());
    }

    private static BridgeEnvelope signed(java.util.function.UnaryOperator<String> requestId,
                                         BridgePayload payload) {
        BridgeEnvelope unsigned = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                requestId.apply(REQUEST_ID), SENT_AT, SENDER_ID, payload.kind(),
                new BridgePayloadCodec().encode(payload), null);
        return unsigned.withSignature(unsigned.sign(SECRET));
    }

    /** A sink that counts what it was given and answers with an outcome. */
    private static final class CountingSink implements BridgeSink {

        private int accepted;
        private final String observed;

        private CountingSink(String observed) {
            this.observed = observed;
        }

        @Override
        public io.matrix.contract.Outcome accept(BridgeEnvelope envelope) {
            accepted++;
            return new io.matrix.contract.Outcome("outcome-1", "action-1", null, null,
                    io.matrix.contract.Outcome.Status.SUCCEEDED, observed, true, 0.0,
                    "the receiver took the proposition", SENT_AT);
        }
    }

    @Test
    @DisplayName("the right names the direction: the knowledge is not an observation")
    void theRightNamesTheDirection() {
        assertEquals(BridgeCapability.KNOWLEDGE, payload().kind());
        assertNotNull(payload().candidate());
        assertNull(payload().observation());
        assertTrue(BridgeCapability.parse("KNOWLEDGE").isPresent(),
                "the wire name of the right must be readable");
    }

    @Test
    @DisplayName("a receiver that granted the right accepts the knowledge and reports the effect")
    void aReceiverThatGrantedTheRightAcceptsTheKnowledge() {
        CountingSink sink = new CountingSink("the brain took the proposition");
        BridgeReceiver receiver = new BridgeReceiver(new HmacBridgeSigner(SECRET),
                EnumSet.of(BridgeCapability.KNOWLEDGE), BridgeBudget.standard(),
                new InMemoryBridgeIdempotency(), sink);

        BridgeAnswer answer = receiver.receiveAnswer(signed(), NOW_MILLIS);

        assertEquals(BridgeDecision.Status.ACCEPTED, answer.decision().status());
        assertNotNull(answer.outcome(), "the receiver did something, so it reports what");
        assertEquals("the brain took the proposition", answer.outcome().observed());
        assertEquals(1, sink.accepted);
    }

    @Test
    @DisplayName("a receiver that did not grant the right refuses the message")
    void aReceiverThatDidNotGrantTheRightRefuses() {
        CountingSink sink = new CountingSink("the brain took the proposition");
        BridgeReceiver receiver = new BridgeReceiver(new HmacBridgeSigner(SECRET),
                EnumSet.of(BridgeCapability.OBSERVATION, BridgeCapability.CANDIDATE,
                        BridgeCapability.EVIDENCE_LINK),
                BridgeBudget.standard(), new InMemoryBridgeIdempotency(), sink);

        BridgeAnswer answer = receiver.receiveAnswer(signed(), NOW_MILLIS);

        assertEquals(BridgeDecision.Status.REFUSED, answer.decision().status());
        assertTrue(answer.decision().reason()
                        .contains(BridgeRefusalReason.CAPABILITY_NOT_GRANTED.description()),
                "the refusal names its reason: " + answer.decision().reason());
        assertEquals(0, sink.accepted, "a refused message must have no effect at all");
    }

    @Test
    @DisplayName("a message of the first direction is refused by the bridge of the second")
    void aMessageOfTheFirstDirectionIsRefused() {
        CountingSink sink = new CountingSink("the brain took the proposition");
        BridgeReceiver receiver = new BridgeReceiver(new HmacBridgeSigner(SECRET),
                EnumSet.of(BridgeCapability.KNOWLEDGE), BridgeBudget.standard(),
                new InMemoryBridgeIdempotency(), sink);

        BridgeEnvelope observation = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                REQUEST_ID, SENT_AT, SENDER_ID, BridgeCapability.OBSERVATION,
                new BridgePayloadCodec().encode(BridgePayload.ofObservation(
                        io.matrix.contract.Observation.ofText("obs-1", "the lamp is lit",
                                SENT_AT, Provenance.of("chain-b", SENT_AT)),
                        Provenance.of("chain-b", SENT_AT))), null);
        BridgeAnswer answer = receiver.receiveAnswer(
                observation.withSignature(observation.sign(SECRET)), NOW_MILLIS);

        assertEquals(BridgeDecision.Status.REFUSED, answer.decision().status());
        assertTrue(answer.decision().reason()
                        .contains(BridgeRefusalReason.CAPABILITY_NOT_GRANTED.description()),
                "the refusal names its reason: " + answer.decision().reason());
    }

    @Test
    @DisplayName("a forged signature is refused, and the knowledge is not taken")
    void aForgedSignatureIsRefused() {
        CountingSink sink = new CountingSink("the brain took the proposition");
        BridgeReceiver receiver = new BridgeReceiver(new HmacBridgeSigner(SECRET),
                EnumSet.of(BridgeCapability.KNOWLEDGE), BridgeBudget.standard(),
                new InMemoryBridgeIdempotency(), sink);

        BridgeEnvelope forged = signed().withSignature(
                signed().signature().substring(0, 32) + "0000000000000000000000000000");

        BridgeAnswer answer = receiver.receiveAnswer(forged, NOW_MILLIS);

        assertEquals(BridgeDecision.Status.REFUSED, answer.decision().status());
        assertTrue(answer.decision().reason()
                        .contains(BridgeRefusalReason.SIGNATURE_INVALID.description()),
                "the refusal names its reason: " + answer.decision().reason());
        assertEquals(0, sink.accepted);
    }

    @Test
    @DisplayName("a wrong secret is refused: the receiver has no secret of its own")
    void aWrongSecretIsRefused() {
        CountingSink sink = new CountingSink("the brain took the proposition");
        BridgeReceiver receiver = new BridgeReceiver(new HmacBridgeSigner(OTHER_SECRET),
                EnumSet.of(BridgeCapability.KNOWLEDGE), BridgeBudget.standard(),
                new InMemoryBridgeIdempotency(), sink);

        BridgeAnswer answer = receiver.receiveAnswer(signed(), NOW_MILLIS);

        assertEquals(BridgeDecision.Status.REFUSED, answer.decision().status());
        assertTrue(answer.decision().reason()
                        .contains(BridgeRefusalReason.SIGNATURE_INVALID.description()),
                "the refusal names its reason: " + answer.decision().reason());
        assertEquals(0, sink.accepted);
    }

    @Test
    @DisplayName("a retry answers the same way and does not take the knowledge twice")
    void aRetryDoesNotTakeTheKnowledgeTwice() {
        CountingSink sink = new CountingSink("the brain took the proposition");
        BridgeReceiver receiver = new BridgeReceiver(new HmacBridgeSigner(SECRET),
                EnumSet.of(BridgeCapability.KNOWLEDGE), BridgeBudget.standard(),
                new InMemoryBridgeIdempotency(), sink);

        BridgeAnswer first = receiver.receiveAnswer(signed(), NOW_MILLIS);
        BridgeAnswer second = receiver.receiveAnswer(signed(), NOW_MILLIS);

        assertEquals(first.decision().status(), second.decision().status());
        assertEquals(first.decision().auditId(), second.decision().auditId(),
                "a retry is the same delivery, so it is the same answer");
        assertNotNull(second.outcome(), "the answer of a retry still carries the effect");
        assertEquals(1, sink.accepted, "the effect happened once");
    }

    @Test
    @DisplayName("the version rule: this build reads version 1, 2 and 3, and refuses the rest")
    void theVersionRule() {
        assertEquals(3, BridgeProtocol.VERSION, "the cognitive task direction is version 3");
        assertTrue(BridgeProtocol.supportedVersion(1),
                "a message of the first direction is still readable");
        assertTrue(BridgeProtocol.supportedVersion(2),
                "a message of the second direction is still readable");
        assertTrue(BridgeProtocol.supportedVersion(3));
        assertFalse(BridgeProtocol.supportedVersion(4),
                "a version this build has never heard of is refused");
        assertFalse(BridgeProtocol.supportedVersion(0));
    }

    @Test
    @DisplayName("a version-1 and a version-2 message keep their meaning: the reader accepts "
            + "both and writes the same records")
    void theOldVersionsKeepTheirMeaning() {
        BridgeReceiver receiver = new BridgeReceiver(new HmacBridgeSigner(SECRET),
                EnumSet.of(BridgeCapability.KNOWLEDGE), BridgeBudget.standard(),
                new InMemoryBridgeIdempotency(), new CountingSink("the brain took it"));
        for (int version : new int[]{1, 2}) {
            BridgeEnvelope envelope = new BridgeEnvelope(BridgeProtocol.NAME, version,
                    "req-old-" + version, SENT_AT, SENDER_ID, BridgeCapability.KNOWLEDGE,
                    new BridgePayloadCodec().encode(payload()), null);
            BridgeEnvelope signed = envelope.withSignature(envelope.sign(new HmacBridgeSigner(SECRET)));
            BridgeAnswer answer = receiver.receiveAnswer(signed, NOW_MILLIS);
            assertTrue(answer.decision().accepted(),
                    "a version-" + version + " message is still understood: "
                            + answer.decision().reason());
            assertEquals("req-old-" + version, answer.decision().requestId());
        }
    }

    @Test
    @DisplayName("every decision is audited, in the order it was made")
    void everyDecisionIsAudited() {
        CountingSink sink = new CountingSink("the brain took the proposition");
        BridgeReceiver receiver = new BridgeReceiver(new HmacBridgeSigner(SECRET),
                EnumSet.of(BridgeCapability.KNOWLEDGE), BridgeBudget.standard(),
                new InMemoryBridgeIdempotency(), sink);

        receiver.receiveAnswer(signed(), NOW_MILLIS);
        receiver.receiveAnswer(signed(requestId -> "req-knowledge-2", payload()), NOW_MILLIS);

        List<BridgeAuditRecord> audit = receiver.auditLog();
        assertEquals(2, audit.size());
        assertEquals(BridgeDecision.Status.ACCEPTED, audit.get(0).decision());
        assertEquals(BridgeDecision.Status.ACCEPTED, audit.get(1).decision());
        assertEquals(BridgeCapability.KNOWLEDGE, audit.get(0).kind(),
                "the audit names the right that was exercised");
    }
}
