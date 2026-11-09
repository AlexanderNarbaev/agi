package io.matrix.bridge;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.matrix.contract.Belief;
import io.matrix.contract.Outcome;
import io.matrix.contract.Provenance;
import io.matrix.contract.RetryPolicy;
import io.matrix.contract.bridge.BridgeAnswer;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgeEnvelopeCodec;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.BridgeSigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * W52 — the SECOND direction of the bridge: chain A offers what it has established.
 *
 * <p><b>Why this direction exists.</b> W51 built the bridge in one direction only: chain B's
 * brain runtime offers what it observed, and chain A's epistemic core records it as a
 * candidate. That direction alone leaves the two halves of MATRIX unable to inform each
 * other: the core knows what it has established, and the brain never hears of it. The
 * mandate asks for a system whose parts take part in one cognitive process, and a part that
 * can only report upwards is not taking part in anything.</p>
 *
 * <p><b>The right names the direction.</b> Chain A does not send a different kind of message
 * with a flag flipped — it exercises a right the other side granted. Chain A's bridge grants
 * {@code OBSERVATION}, {@code CANDIDATE} and {@code EVIDENCE_LINK}; chain B's bridge grants
 * {@code KNOWLEDGE}. A message of the wrong direction is refused for the same reason a
 * forged one is: the right was never granted.</p>
 *
 * <p><b>The governance is the same governance.</b> The envelope is signed with the shared
 * secret, the request id is the idempotency key, a transport failure is retried under the
 * retry policy and a refusal is returned as it is, the budget is enforced by the receiver,
 * and the receiver audits what it did. A second direction with weaker rules would be a
 * second, quieter bridge.</p>
 *
 * <p><b>What is published is what the core believes, not what it believes about itself.</b>
 * The belief carries its status, its confidence, the evidence that bears on it and the
 * evidence that contradicts it — so the receiver can tell an established proposition from a
 * proposed one, and can refuse to learn one it has grounds to doubt.</p>
 */
public final class KnowledgePublisher {

    /** The path the established knowledge is posted to. */
    public static final String KNOWLEDGE_PATH = "/v1/bridge/knowledge";

    /** The time a publication may take. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final String baseUrl;
    private final BridgeSigner signer;
    private final String senderId;
    private final BridgePayloadCodec payloadCodec = new BridgePayloadCodec();
    private final BridgeEnvelopeCodec envelopeCodec = new BridgeEnvelopeCodec();
    private final HttpClient client;
    private final Duration timeout;
    private final RetryPolicy retryPolicy;

    /**
     * The mapper both sides use for the answer.
     *
     * <p>It reads the canonical {@link Outcome} the receiver reports. An unknown field is a
     * refusal rather than a loss, for the reason the payload codec gives: a reader that
     * silently ignored what it did not know would be reading a message of a version it does
     * not speak.</p>
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    /**
     * A publisher for chain B's bridge.
     *
     * @param baseUrl   the base URL of chain B
     * @param signer    the signer of the shared secret
     * @param senderId  the identity this side publishes under
     */
    public KnowledgePublisher(String baseUrl, BridgeSigner signer, String senderId) {
        this(baseUrl, signer, senderId, DEFAULT_TIMEOUT, RetryPolicy.STANDARD);
    }

    /**
     * A publisher for chain B's bridge, with the timeout and the retry policy named.
     *
     * @param baseUrl     the base URL of chain B
     * @param signer      the signer of the shared secret
     * @param senderId    the identity this side publishes under
     * @param timeout     the time a publication may take
     * @param retryPolicy the policy that bounds the attempts
     */
    public KnowledgePublisher(String baseUrl, BridgeSigner signer, String senderId,
                              Duration timeout, RetryPolicy retryPolicy) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("base url must not be blank");
        }
        if (signer == null) {
            throw new IllegalArgumentException("signer must not be null");
        }
        if (senderId == null || senderId.isBlank()) {
            throw new IllegalArgumentException("sender id must not be blank");
        }
        if (timeout == null) {
            throw new IllegalArgumentException("timeout must not be null");
        }
        if (retryPolicy == null) {
            throw new IllegalArgumentException("retry policy must not be null");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.signer = signer;
        this.senderId = senderId;
        this.timeout = timeout;
        this.retryPolicy = retryPolicy;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    /**
     * Offers an established belief to chain B's brain.
     *
     * <p>The request id is the idempotency key: a retry of the same publication asks for
     * the same effect, and the receiver does it once.</p>
     *
     * @param requestId  the idempotency key
     * @param belief     the established belief
     * @param provenance the provenance of the delivery
     * @param sentAt     the instant of the delivery
     * @return the answer: the decision, and what the receiver did with it
     */
    public BridgeAnswer publishKnowledge(String requestId, Belief belief, Provenance provenance,
                                         String sentAt) {
        if (belief == null) {
            throw new IllegalArgumentException("there is no belief to publish");
        }
        BridgePayload payload = BridgePayload.ofKnowledge(belief, provenance);
        BridgeEnvelope envelope = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                requestId, sentAt, senderId, BridgeCapability.KNOWLEDGE,
                payloadCodec.encode(payload), null);
        BridgeEnvelope signed = envelope.withSignature(envelope.sign(signer));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + KNOWLEDGE_PATH))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header(BridgeProtocol.HEADER_PROTOCOL, BridgeProtocol.NAME)
                .header(BridgeProtocol.HEADER_VERSION, String.valueOf(BridgeProtocol.VERSION))
                .header(BridgeProtocol.HEADER_REQUEST_ID, requestId)
                .header(BridgeProtocol.HEADER_SENDER_ID, senderId)
                .header(BridgeProtocol.HEADER_SIGNATURE, signed.signature())
                .POST(HttpRequest.BodyPublishers.ofString(envelopeCodec.encode(signed)))
                .build();

        java.io.IOException lastFailure = null;
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            try {
                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());
                return answerOf(response.body(), requestId);
            } catch (java.io.IOException e) {
                lastFailure = e;
                if (!retryPolicy.mayRetry(attempt)) {
                    break;
                }
                long wait = retryPolicy.backoffMillis(attempt + 1);
                if (wait > 0L) {
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new KnowledgePublicationFailure(
                                "the publication was interrupted", interrupted);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KnowledgePublicationFailure("the publication was interrupted", e);
            }
        }
        throw new KnowledgePublicationFailure("the receiver did not answer after "
                + retryPolicy.maxAttempts() + " attempt(s)", lastFailure);
    }

    /**
     * The answer the receiver gave.
     *
     * @param body      the body of the answer
     * @param requestId the request id of the publication
     * @return the answer
     */
    private BridgeAnswer answerOf(String body, String requestId) {
        if (body == null || body.isBlank()) {
            return BridgeAnswer.of(BridgeDecision.refused(requestId, "audit-unknown",
                    io.matrix.contract.bridge.BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the receiver answered with an empty body"));
        }
        try {
            var node = MAPPER.readTree(body);
            String status = node.path("status").asText("");
            String reason = node.path("reason").asText("the receiver answered without a reason");
            String auditId = node.path("auditId").asText("audit-unknown");
            BridgeDecision decision = new BridgeDecision(BridgeDecision.Status.valueOf(status),
                    requestId, auditId, reason);
            Outcome outcome = null;
            if (node.has("outcome") && !node.get("outcome").isNull()) {
                outcome = MAPPER.treeToValue(node.get("outcome"), Outcome.class);
            }
            return new BridgeAnswer(decision, outcome);
        } catch (Exception e) {
            return BridgeAnswer.of(BridgeDecision.refused(requestId, "audit-unknown",
                    io.matrix.contract.bridge.BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the receiver's answer could not be read: " + e.getMessage()));
        }
    }

    /**
     * The base URL this publisher talks to.
     *
     * @return the base URL
     */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * The identity this publisher publishes under.
     *
     * @return the sender id
     */
    public String senderId() {
        return senderId;
    }
}
