package io.matrix.api.bridge;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.matrix.contract.Belief;
import io.matrix.contract.BeliefStatus;
import io.matrix.contract.Outcome;
import io.matrix.contract.bridge.BridgeAnswer;
import io.matrix.contract.bridge.BridgeAuditRecord;
import io.matrix.contract.bridge.BridgeBudget;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgeEnvelopeCodec;
import io.matrix.contract.bridge.BridgeIdempotency;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeReceiver;
import io.matrix.contract.bridge.BridgeRefusalReason;
import io.matrix.contract.bridge.BridgeSink;
import io.matrix.contract.bridge.HmacBridgeSigner;
import io.matrix.contract.bridge.InMemoryBridgeIdempotency;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * W52 — chain B's side of the SECOND direction of the bridge.
 *
 * <p><b>What this class is.</b> W51 built the bridge in one direction: chain B offers what
 * it observed, chain A's epistemic core records it. This class is the other end — chain A
 * offers what it has ESTABLISHED, and chain B's brain takes it into its own memory, so
 * that the brain can answer with what the epistemic core knows. A system whose parts can
 * only report upwards is not one system; it is two systems with a pipe.</p>
 *
 * <p><b>The right names the direction.</b> This bridge grants exactly one right:
 * {@link BridgeCapability#KNOWLEDGE}. A message that offers an observation, a candidate or
 * an evidence link is refused here for the same reason it would be refused anywhere else —
 * the right was never granted. Chain A's bridge, symmetrically, grants the three rights of
 * the first direction and not this one.</p>
 *
 * <p><b>The governance is the same governance, not a lighter one.</b> The envelope is
 * verified against the shared secret, the capability is checked, the budget is enforced
 * before the brain is touched, the request id is the idempotency key, and every decision is
 * audited. A second direction with weaker rules would be a second, quieter bridge — the
 * kind of thing that is not in the register and is therefore not governed.</p>
 *
 * <p><b>What the brain is told, and what it is not.</b> The belief is taught as the
 * proposition and what is known about it: the status, how much evidence supports it, how
 * much contradicts it, and where it came from. The brain is NOT told that the proposition is
 * true — it is told that the epistemic core established it, with the evidence named, and
 * the brain's own memory then carries the whole of that, including the possibility of
 * contradiction.</p>
 *
 * <p><b>Where this class lives matters.</b> It lives in chain B's own module, and it
 * reaches the shared contract layer — which is pure — and nothing of chain A. The
 * isolation gate measures that: chain B must reach no class of chain A's bridge server, and
 * this class is written so that it does not.</p>
 */
public final class KnowledgeBridge {

    /** The environment variable that carries the shared secret. */
    public static final String SECRET_ENV = "MATRIX_BRIDGE_SECRET";

    /** The system property that carries the shared secret. */
    public static final String SECRET_PROPERTY = "matrix.bridge.secret";

    /** The path the established knowledge arrives at. */
    public static final String KNOWLEDGE_PATH = "/v1/bridge/knowledge";

    /** The mapper both sides use for the answer. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private final BridgeReceiver receiver;
    private final KnowledgeTeacher teacher;
    private final BridgeEnvelopeCodec envelopeCodec = new BridgeEnvelopeCodec();
    private final BridgePayloadCodec payloadCodec = new BridgePayloadCodec();

    /**
     * A bridge that refuses everything: no secret, no teacher.
     *
     * <p>Used when the process was started without a shared secret. It is not an error to
     * have no secret — it is an error to <i>accept</i> a message without one, and this
     * receiver refuses every message with the reason BRIDGE-3 names.</p>
     */
    public KnowledgeBridge() {
        this(null, null);
    }

    /**
     * A bridge that verifies the shared secret and teaches the brain.
     *
     * @param teacher the brain that takes the knowledge
     * @param secret  the shared secret
     */
    public KnowledgeBridge(KnowledgeTeacher teacher, byte[] secret) {
        this.teacher = teacher;
        this.receiver = new BridgeReceiver(
                secret == null ? null : new HmacBridgeSigner(secret),
                EnumSet.of(BridgeCapability.KNOWLEDGE),
                BridgeBudget.standard(),
                new InMemoryBridgeIdempotency(),
                new BrainSink(teacher));
    }

    /**
     * The shared secret this process is configured with.
     *
     * @return the secret, or empty when none is configured
     */
    public static Optional<byte[]> secret() {
        String configured = System.getProperty(SECRET_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(SECRET_ENV);
        }
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(configured.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A bridge configured from the environment.
     *
     * @param teacher the brain that takes the knowledge
     * @return the bridge
     */
    public static KnowledgeBridge configured(KnowledgeTeacher teacher) {
        return new KnowledgeBridge(teacher, secret().orElse(null));
    }

    /**
     * The answer to a delivery, as the body of the HTTP response.
     *
     * @param body the body of the delivery
     * @return the JSON answer
     */
    public String answer(String body) {
        BridgeAnswer answer;
        try {
            BridgeEnvelope envelope = envelopeCodec.decode(body);
            answer = receiver.receiveAnswer(envelope, System.currentTimeMillis());
        } catch (RuntimeException e) {
            answer = BridgeAnswer.of(io.matrix.contract.bridge.BridgeDecision.refused(
                    "audit-unknown", "audit-unknown", BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the delivery could not be read: " + e.getMessage()));
        }
        return jsonOf(answer);
    }

    /**
     * The decisions this bridge has made, in the order it made them.
     *
     * @return the audit trail
     */
    public List<BridgeAuditRecord> auditLog() {
        return receiver.auditLog();
    }

    /**
     * The rights this bridge grants.
     *
     * @return the granted rights
     */
    public java.util.Set<BridgeCapability> grantedCapabilities() {
        return receiver.grantedCapabilities();
    }

    /**
     * The answer as JSON.
     *
     * <p>The outcome travels with it when the receiver reported one: the sender learns not
     * only that its knowledge was accepted, but what the brain did with it.</p>
     *
     * @param answer the answer
     * @return the JSON
     */
    private String jsonOf(BridgeAnswer answer) {
        StringBuilder json = new StringBuilder();
        json.append("{\"status\":\"").append(answer.decision().status().name()).append('"');
        json.append(",\"requestId\":\"").append(esc(answer.decision().requestId())).append('"');
        json.append(",\"auditId\":\"").append(esc(answer.decision().auditId())).append('"');
        json.append(",\"reason\":\"").append(esc(answer.decision().reason())).append('"');
        Outcome outcome = answer.outcome();
        if (outcome != null) {
            json.append(",\"outcome\":");
            try {
                json.append(MAPPER.writeValueAsString(outcome));
            } catch (Exception e) {
                json.append("{\"status\":\"FAILED\",\"reason\":\"the outcome could not be "
                        + "written\"}");
            }
        }
        json.append('}');
        return json.toString();
    }

    /**
     * The sink: the brain takes the offered knowledge into its memory.
     *
     * <p>The sink is the ONLY place where the brain is touched, and it runs only after the
     * signature, the capability, the budget and the idempotency have all been checked.</p>
     */
    private static final class BrainSink implements BridgeSink {

        private final KnowledgeTeacher teacher;
        private final BridgePayloadCodec codec = new BridgePayloadCodec();

        private BrainSink(KnowledgeTeacher teacher) {
            this.teacher = teacher;
        }

        @Override
        public Outcome accept(BridgeEnvelope envelope) {
            if (teacher == null) {
                // This process has no brain to take the knowledge. The message was
                // accepted under the protocol; the effect did not happen, and the answer
                // says so rather than letting an acceptance stand for a learning that
                // never occurred.
                return new Outcome("outcome-" + envelope.requestId(),
                        "action-" + envelope.requestId(), null, null, Outcome.Status.FAILED,
                        "this process has no brain to take the knowledge",
                        false, 1.0,
                        "the bridge accepted the message, but there is nothing here that "
                                + "could learn it",
                        Instant.now().toString());
            }
            BridgePayload payload = codec.decode(envelope.payload());
            Belief belief = payload.candidate();
            if (belief == null) {
                return null;
            }
            boolean taught = teacher.teach(belief.proposition(), standingOf(belief));
            int held = teacher.knowledgeSize();
            return new Outcome("outcome-" + envelope.requestId(),
                    "action-" + envelope.requestId(), null, null,
                    taught ? Outcome.Status.SUCCEEDED : Outcome.Status.FAILED,
                    taught
                            ? "the brain took the proposition; the brain's memory holds "
                                    + held + " item(s)"
                            : "the brain refused the proposition",
                    taught,
                    taught ? 0.0 : 1.0,
                    taught
                            ? "the brain's memory holds the proposition and what is known about it"
                            : "the brain did not take the proposition",
                    Instant.now().toString());
        }

        /**
         * What the brain is told about the proposition.
         *
         * <p>The standing, not the truth: the status, the evidence that supports it, the
         * evidence that contradicts it and where it came from. A brain that was told only
         * "this is true" would be told more than the epistemic core knows.</p>
         *
         * @param belief the established belief
         * @return the standing, as one line
         */
        private static String standingOf(Belief belief) {
            BeliefStatus status = belief.status();
            int supporting = belief.evidenceIds() == null ? 0 : belief.evidenceIds().size();
            int contradicting = belief.contradictingEvidenceIds() == null
                    ? 0 : belief.contradictingEvidenceIds().size();
            StringBuilder standing = new StringBuilder();
            standing.append(status == null ? "UNKNOWN" : status.name());
            standing.append("; supporting evidence: ").append(supporting);
            standing.append("; contradicting evidence: ").append(contradicting);
            if (belief.provenance() != null && belief.provenance().origin() != null) {
                standing.append("; origin: ").append(belief.provenance().origin());
            }
            if (belief.rationale() != null && !belief.rationale().isBlank()) {
                standing.append("; rationale: ").append(belief.rationale());
            }
            return standing.toString();
        }
    }

    /**
     * Escapes a string for the JSON answer.
     *
     * @param value the value
     * @return the escaped value
     */
    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
