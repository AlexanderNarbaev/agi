package io.matrix.api.bridge;

import io.matrix.contract.Belief;
import io.matrix.contract.BeliefStatus;
import io.matrix.contract.Confidence;
import io.matrix.contract.Provenance;
import io.matrix.contract.ValidityInterval;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgeEnvelopeCodec;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.HmacBridgeSigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W52 — chain B's side of the second direction of the bridge.
 *
 * <p><b>What is pinned here.</b> The bridge of the second direction accepts the knowledge
 * chain A offers and teaches it to the brain; the answer says what the brain did with it;
 * a forged signature, a right that was never granted and a missing secret are all refused
 * without the brain learning anything.</p>
 */
class KnowledgeBridgeTest {

    /** The shared secret of the test. */
    private static final byte[] SECRET =
            "the-chain-b-bridge-secret".getBytes(StandardCharsets.UTF_8);

    /** The instant the message claims to have been written at. */
    private static final String SENT_AT = "2026-11-09T10:00:00Z";

    /** The identity of the sender — chain A this time. */
    private static final String SENDER_ID = "chain-a-epistemic-core";

    /** The request id of the message. */
    private static final String REQUEST_ID = "req-knowledge-1";

    /** A brain that remembers what it was taught. */
    private static final class RecordingTeacher implements KnowledgeTeacher {

        private final List<String> inputs = new ArrayList<>();
        private final List<String> responses = new ArrayList<>();

        @Override
        public boolean teach(String input, String response) {
            inputs.add(input);
            responses.add(response);
            return true;
        }

        @Override
        public int knowledgeSize() {
            return inputs.size();
        }
    }

    /** An established belief. */
    private static Belief belief() {
        return new Belief("belief-1", "the lamp is lit when power and switch are both on",
                BeliefStatus.SUPPORTED,
                new Confidence(0.9, Confidence.Basis.MEASURED, 3, 0, "three observations agree",
                        List.of()),
                List.of("ev-1", "ev-2"), List.of(),
                Provenance.of("the cognitive kernel", SENT_AT),
                ValidityInterval.openFrom(SENT_AT), 1, null, null, null);
    }

    /** A signed delivery of established knowledge. */
    private static String delivery() {
        return delivery(SECRET);
    }

    /** A signed delivery of established knowledge, signed with a given secret. */
    private static String delivery(byte[] secret) {
        BridgePayload payload = BridgePayload.ofKnowledge(belief(),
                Provenance.of("chain-a", SENT_AT));
        BridgeEnvelope unsigned = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                REQUEST_ID, SENT_AT, SENDER_ID, BridgeCapability.KNOWLEDGE,
                new BridgePayloadCodec().encode(payload), null);
        BridgeEnvelope signed = unsigned.withSignature(unsigned.sign(secret));
        return new BridgeEnvelopeCodec().encode(signed);
    }

    @Test
    @DisplayName("the knowledge is taught to the brain, and the answer says so")
    void theKnowledgeIsTaughtToTheBrain() {
        RecordingTeacher teacher = new RecordingTeacher();
        KnowledgeBridge bridge = new KnowledgeBridge(teacher, SECRET);

        String answer = bridge.answer(delivery());

        assertTrue(answer.contains("\"status\":\"ACCEPTED\""), answer);
        assertTrue(answer.contains("\"outcome\""),
                "the answer carries what the brain did with it: " + answer);
        assertTrue(answer.contains("the brain took the proposition"), answer);
        assertEquals(1, teacher.inputs.size());
        assertEquals("the lamp is lit when power and switch are both on",
                teacher.inputs.get(0));
        assertTrue(teacher.responses.get(0).contains("SUPPORTED"),
                "the brain is told the standing, not that the proposition is true: "
                        + teacher.responses.get(0));
        assertTrue(teacher.responses.get(0).contains("supporting evidence: 2"),
                teacher.responses.get(0));
    }

    @Test
    @DisplayName("a forged signature is refused, and the brain learns nothing")
    void aForgedSignatureIsRefused() {
        RecordingTeacher teacher = new RecordingTeacher();
        KnowledgeBridge bridge = new KnowledgeBridge(teacher, SECRET);

        String answer = bridge.answer(delivery("a-different-secret".getBytes(
                StandardCharsets.UTF_8)));

        assertTrue(answer.contains("\"status\":\"REFUSED\""), answer);
        assertTrue(teacher.inputs.isEmpty(),
                "a message that failed the signature check must not reach the brain");
    }

    @Test
    @DisplayName("a bridge without a secret refuses everything, and says why")
    void aBridgeWithoutASecretRefusesEverything() {
        RecordingTeacher teacher = new RecordingTeacher();
        KnowledgeBridge bridge = new KnowledgeBridge(teacher, null);

        String answer = bridge.answer(delivery());

        assertTrue(answer.contains("\"status\":\"REFUSED\""), answer);
        assertTrue(answer.contains("no verification secret is configured"), answer);
        assertTrue(teacher.inputs.isEmpty());
    }

    @Test
    @DisplayName("the bridge grants exactly one right: the knowledge")
    void theBridgeGrantsExactlyOneRight() {
        KnowledgeBridge bridge = new KnowledgeBridge(new RecordingTeacher(), SECRET);

        assertEquals(1, bridge.grantedCapabilities().size());
        assertTrue(bridge.grantedCapabilities().contains(BridgeCapability.KNOWLEDGE));
        assertFalse(bridge.grantedCapabilities().contains(BridgeCapability.OBSERVATION),
                "the bridge of the second direction does not accept the first direction's "
                        + "messages");
    }

    @Test
    @DisplayName("a delivery that is not a message is refused, not crashed on")
    void aDeliveryThatIsNotAMessageIsRefused() {
        RecordingTeacher teacher = new RecordingTeacher();
        KnowledgeBridge bridge = new KnowledgeBridge(teacher, SECRET);

        String answer = bridge.answer("this is not an envelope");

        assertTrue(answer.contains("\"status\":\"REFUSED\""), answer);
        assertTrue(teacher.inputs.isEmpty());
    }

    @Test
    @DisplayName("every decision is audited")
    void everyDecisionIsAudited() {
        KnowledgeBridge bridge = new KnowledgeBridge(new RecordingTeacher(), SECRET);

        bridge.answer(delivery());

        assertEquals(1, bridge.auditLog().size());
        assertEquals(BridgeCapability.KNOWLEDGE, bridge.auditLog().get(0).kind());
    }
}
