package io.matrix.brain.runtime.bridge;

import io.matrix.contract.Belief;
import io.matrix.contract.Evidence;
import io.matrix.contract.Observation;
import io.matrix.contract.Provenance;
import io.matrix.contract.RetryPolicy;
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
 * W51 — the chain B side of the bridge: the client.
 *
 * <p><b>Why chain B is the client.</b> BRIDGE-3 says so: "The chain B side is the client and
 * holds no ambient trust: the trust of a call is established per call, not by the fact that
 * the process happens to run on the same host." A brain runtime that could write to the
 * epistemic store would be a second writer of a database that is designed to have one, and
 * the whole isolation argument rests on there being exactly one.</p>
 *
 * <p><b>What the publisher may send.</b> An observation, a candidate or an evidence link —
 * the three things BRIDGE-5 allows. Every message is signed with the shared secret, and the
 * request id is the idempotency key: a retry after a timeout sends the SAME request id, so
 * the receiver answers with the original outcome instead of recording a second observation.</p>
 *
 * <p><b>What the publisher cannot do.</b> It cannot reach the epistemic store, it cannot
 * raise a status, and it cannot write anything without the receiver accepting it. This class
 * depends only on {@code io.matrix.contract} and {@code io.matrix.contract.bridge}, both of
 * which are free of any database or route, so the isolation gate's rules hold for it.</p>
 *
 * <p><b>Determinism.</b> The instant is a parameter of each call, and the request id is
 * supplied by the caller. Nothing here reads a clock or a random source by itself.</p>
 */
public final class BridgePublisher {

    /** The path the bridge's messages are posted to. */
    public static final String MESSAGES_PATH = "/v1/bridge/messages";

    /** The time a publish may take. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final String baseUrl;
    private final BridgeSigner signer;
    private final String senderId;
    private final BridgePayloadCodec payloadCodec = new BridgePayloadCodec();
    private final BridgeEnvelopeCodec envelopeCodec = new BridgeEnvelopeCodec();
    private final HttpClient client;
    private final Duration timeout;

    /**
     * W51 §11.5 — when a delivery may be attempted again.
     *
     * <p><b>What this is for.</b> A {@code RetryPolicy} existed as a contract with no
     * consumer, which meant the repository had a statement about retries and no behaviour.
     * The publisher is the right consumer: the delivery of an idempotent message over a
     * network can fail in a way that says nothing about whether the receiver applied it —
     * a timeout, a connection reset — and the message carries the idempotency key that
     * makes the retry safe.</p>
     *
     * <p><b>What is NOT retried.</b> A refusal is an answer, not a failure: the receiver
     * understood the message and said no, and sending it again says the same thing. Only a
     * transport failure is retried.</p>
     */
    private final RetryPolicy retryPolicy;

    /**
     * Creates a publisher.
     *
     * @param baseUrl  the base URL of chain A
     * @param signer   the signer that signs the outgoing messages
     * @param senderId the identity of this sender, which the receiver records
     */
    public BridgePublisher(String baseUrl, BridgeSigner signer, String senderId) {
        this(baseUrl, signer, senderId, DEFAULT_TIMEOUT);
    }

    /**
     * Creates a publisher with an explicit retry policy.
     *
     * @param baseUrl    the base URL of chain A
     * @param signer     the signer that signs the outgoing messages
     * @param senderId   the identity this sender presents
     * @param timeout    how long a publish may take
     * @param retryPolicy when a delivery may be attempted again
     */
    public BridgePublisher(String baseUrl, BridgeSigner signer, String senderId, Duration timeout,
                           RetryPolicy retryPolicy) {
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
     * Creates a publisher with an explicit timeout.
     *
     * @param baseUrl  the base URL of chain A
     * @param signer   the signer that signs the outgoing messages
     * @param senderId the identity of this sender
     * @param timeout  how long a publish may take
     */
    public BridgePublisher(String baseUrl, BridgeSigner signer, String senderId, Duration timeout) {
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
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.signer = signer;
        this.senderId = senderId;
        this.timeout = timeout;
        this.retryPolicy = RetryPolicy.STANDARD;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    /**
     * Publishes an observation.
     *
     * @param requestId   the idempotency key of the message
     * @param observation the observation to publish
     * @param provenance  the provenance of the delivery
     * @param sentAt      the instant of the delivery
     * @return the decision the receiver made
     */
    public BridgeDecision publishObservation(String requestId, Observation observation,
                                             Provenance provenance, String sentAt) {
        return publish(requestId, BridgePayload.ofObservation(observation, provenance),
                BridgeCapability.OBSERVATION, sentAt);
    }

    /**
     * Publishes a candidate belief.
     *
     * @param requestId the idempotency key of the message
     * @param candidate the candidate to publish
     * @param provenance the provenance of the delivery
     * @param sentAt    the instant of the delivery
     * @return the decision the receiver made
     */
    public BridgeDecision publishCandidate(String requestId, Belief candidate,
                                            Provenance provenance, String sentAt) {
        return publish(requestId, BridgePayload.ofCandidate(candidate, provenance),
                BridgeCapability.CANDIDATE, sentAt);
    }

    /**
     * Publishes a link to evidence.
     *
     * @param requestId the idempotency key of the message
     * @param link      the evidence link to publish
     * @param provenance the provenance of the delivery
     * @param sentAt    the instant of the delivery
     * @return the decision the receiver made
     */
    public BridgeDecision publishEvidenceLink(String requestId, Evidence link,
                                              Provenance provenance, String sentAt) {
        return publish(requestId, BridgePayload.ofEvidenceLink(link, provenance),
                BridgeCapability.EVIDENCE_LINK, sentAt);
    }

    /**
     * Publishes one message.
     *
     * @param requestId the idempotency key of the message
     * @param payload   the payload
     * @param kind      the kind of the payload
     * @param sentAt    the instant of the delivery
     * @return the decision the receiver made
     */
    public BridgeDecision publish(String requestId, BridgePayload payload, BridgeCapability kind,
                                  String sentAt) {
        BridgeEnvelope envelope = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                requestId, sentAt, senderId, kind, payloadCodec.encode(payload), null);
        BridgeEnvelope signed = envelope.withSignature(envelope.sign(signer));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + MESSAGES_PATH))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header(BridgeProtocol.HEADER_PROTOCOL, BridgeProtocol.NAME)
                .header(BridgeProtocol.HEADER_VERSION, String.valueOf(BridgeProtocol.VERSION))
                .header(BridgeProtocol.HEADER_REQUEST_ID, requestId)
                .header(BridgeProtocol.HEADER_SENDER_ID, senderId)
                .header(BridgeProtocol.HEADER_SIGNATURE, signed.signature())
                .POST(HttpRequest.BodyPublishers.ofString(envelopeCodec.encode(signed)))
                .build();
        // The attempts are bounded by the retry policy, and only a TRANSPORT failure is
        // retried: a refusal is an answer and is returned as it is.
        java.io.IOException lastFailure = null;
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            try {
                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());
                return decisionOf(response.body(), requestId);
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
                        throw new BridgePublicationFailure("the publication was interrupted", interrupted);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BridgePublicationFailure("the publication was interrupted", e);
            }
        }
        throw new BridgePublicationFailure("the bridge could not be reached after "
                + retryPolicy.maxAttempts() + " attempt(s): " + lastFailure.getMessage(),
                lastFailure);
    }

    /**
     * The decision the receiver answered with.
     *
     * <p>The receiver's answer is a small JSON document. When it cannot be read, the failure
     * is reported as a refusal rather than as an exception: the sender cannot know whether
     * the receiver applied the message, and a sender that pretends otherwise would retry a
     * message that may already have been applied.</p>
     */
    private BridgeDecision decisionOf(String body, String requestId) {
        if (body == null || body.isBlank()) {
            return BridgeDecision.refused(requestId, "audit-unknown",
                    io.matrix.contract.bridge.BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the receiver answered with an empty body");
        }
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            String status = node.path("status").asText("");
            String reason = node.path("reason").asText("the receiver answered without a reason");
            String auditId = node.path("auditId").asText("audit-unknown");
            BridgeDecision.Status decision = BridgeDecision.Status.valueOf(status);
            return new BridgeDecision(decision, requestId, auditId, reason);
        } catch (Exception e) {
            return BridgeDecision.refused(requestId, "audit-unknown",
                    io.matrix.contract.bridge.BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the receiver's answer could not be read: " + e.getMessage());
        }
    }
}
