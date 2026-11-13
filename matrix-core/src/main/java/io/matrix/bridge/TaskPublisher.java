package io.matrix.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.contract.RetryPolicy;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgeEnvelopeCodec;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.BridgeSigner;
import io.matrix.contract.bridge.CognitiveTaskReport;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import io.matrix.contract.bridge.CognitiveTaskResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * W53 — chain A's side of the remote cognitive task: the dispatch and its answer.
 *
 * <p><b>What this class is for.</b> Chain A cannot do everything itself. When it needs
 * work that only the peer's cognitive runtime can do, it hands the peer a bounded,
 * typed request and waits for what actually comes back. This class performs that one
 * exchange, and it performs it through the bridge — the same governed crossing every
 * other message uses, with the same canonical form, the same signature and the same
 * protocol version rule.</p>
 *
 * <p><b>Acceptance is not completion.</b> The receiver's answer says whether the
 * <i>message</i> was accepted; it does not say whether the task ran. The task's own
 * outcome is in the response, and this class keeps the two apart: a dispatch that was
 * accepted but that carries no response is a dispatch that did not finish, and it says
 * so.</p>
 *
 * <p><b>The answer is authenticated in both directions.</b> The request is signed by the
 * caller; the response and the report come back as signed envelopes, and this class
 * verifies both signatures before it hands anything to the epistemic side. A report whose
 * signature does not verify is not a report — it is noise, and it is dropped rather than
 * passed on.</p>
 *
 * <p><b>What this class does not do.</b> It does not decide what to believe about the
 * answer, it does not write to the epistemic store, and it does not keep the dispatch's
 * state. The durable intent lives in chain A's own store, and the adjudication lives in
 * the epistemic model. This class is the transport with its authentication, nothing
 * more.</p>
 */
public final class TaskPublisher {

    /** The path the remote cognitive task arrives at. */
    public static final String TASK_PATH = "/v1/bridge/task";

    /** The default timeout of the exchange. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    /** The mapper the answer is read with. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final BridgeSigner signer;
    private final String senderId;
    private final Duration timeout;
    private final RetryPolicy retryPolicy;
    private final HttpClient client;
    private final BridgeEnvelopeCodec envelopeCodec = new BridgeEnvelopeCodec();
    private final BridgePayloadCodec payloadCodec = new BridgePayloadCodec();

    /**
     * The publisher.
     *
     * @param baseUrl  the base URL of chain B
     * @param signer   the signer of the request and the verifier of the answer
     * @param senderId the identity this process claims
     */
    public TaskPublisher(String baseUrl, BridgeSigner signer, String senderId) {
        this(baseUrl, signer, senderId, DEFAULT_TIMEOUT, RetryPolicy.STANDARD);
    }

    /**
     * The publisher.
     *
     * @param baseUrl     the base URL of chain B
     * @param signer      the signer of the request and the verifier of the answer
     * @param senderId    the identity this process claims
     * @param timeout     the timeout of the exchange
     * @param retryPolicy the policy that bounds the attempts
     */
    public TaskPublisher(String baseUrl, BridgeSigner signer, String senderId,
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
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.signer = signer;
        this.senderId = senderId;
        this.timeout = timeout;
        this.retryPolicy = retryPolicy;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    /**
     * What the peer answered.
     *
     * @param requestId      the request this answers
     * @param status         the decision the peer made about the message
     * @param reason         the peer's reason, in words
     * @param response       the structured result, when the task finished
     * @param report         the secondary report, when the peer sent one
     * @param responseSigned whether the response's signature was verified
     * @param reportSigned   whether the report's signature was verified
     */
    public record Dispatch(
            String requestId,
            BridgeDecision.Status status,
            String reason,
            Optional<CognitiveTaskResponse> response,
            Optional<CognitiveTaskReport> report,
            boolean responseSigned,
            boolean reportSigned,
            Optional<io.matrix.contract.bridge.BridgeEnvelope> reportEnvelope) {

        /** The dispatch of a task that produced nothing. */
        public static Dispatch empty(String requestId, BridgeDecision.Status status, String reason) {
            return new Dispatch(requestId, status, reason, Optional.empty(), Optional.empty(),
                    false, false, Optional.empty());
        }

        /**
         * True when the peer accepted the message and the task finished.
         *
         * @return true when there is a result
         */
        public boolean completed() {
            return response.isPresent()
                    && response.get().status() == CognitiveTaskResponse.Status.COMPLETED;
        }
    }

    /**
     * Sends the request and reads the answer.
     *
     * @param request the typed request
     * @param sentAt  the instant of the delivery
     * @return what the peer answered
     * @throws TaskDispatchException when the peer could not be reached at all
     */
    /**
     * Tells the peer that a dispatched task is withdrawn.
     *
     * <p><b>What the message is.</b> A signed envelope of its own kind, on the same route:
     * the cancellation is a crossing like any other, with the same authentication, the
     * same protocol version and the same audit. The right to send it is the peer's grant,
     * not a field of the message.</p>
     *
     * @param requestId the request to withdraw
     * @param taskId    the task the request belongs to
     * @param reason    why the task is withdrawn
     * @param sentAt    the instant of the delivery
     * @return true when the peer accepted the cancellation
     */
    public boolean cancel(String requestId, String taskId, String reason, String sentAt) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("the cancellation needs a request id");
        }
        io.matrix.contract.bridge.CognitiveTaskCancel cancel =
                new io.matrix.contract.bridge.CognitiveTaskCancel(
                        BridgeProtocol.VERSION, taskId == null ? "" : taskId, requestId,
                        new io.matrix.contract.Correlation("cancel-" + requestId, "ep-parent",
                                "ep-" + requestId, requestId),
                        reason == null || reason.isBlank() ? "the task was withdrawn" : reason,
                        sentAt,
                        io.matrix.contract.Provenance.of(senderId, sentAt));
        BridgePayload payload = BridgePayload.ofTaskCancel(cancel, cancel.provenance());
        // The message carries its own identity. The idempotency of the bridge is keyed
        // on the request id, and the withdrawal is not the request: reusing the request
        // id would make the peer see a second, different payload under an id it has
        // already seen, and refuse it as a conflict — the very message meant to end the
        // request would be refused as a forgery of it. The name is derived from the
        // request, so a repeated withdrawal is the same message and stays idempotent.
        String messageId = "cancel-" + requestId;
        BridgeEnvelope envelope = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                messageId, sentAt, senderId, BridgeCapability.INFERENCE_CANCEL,
                payloadCodec.encode(payload), null);
        BridgeEnvelope signed = envelope.withSignature(envelope.sign(signer));
        HttpRequest http = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + TASK_PATH))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header(BridgeProtocol.HEADER_PROTOCOL, BridgeProtocol.NAME)
                .header(BridgeProtocol.HEADER_VERSION, String.valueOf(BridgeProtocol.VERSION))
                .header(BridgeProtocol.HEADER_REQUEST_ID, messageId)
                .header(BridgeProtocol.HEADER_SENDER_ID, senderId)
                .header(BridgeProtocol.HEADER_SIGNATURE, signed.signature())
                .POST(HttpRequest.BodyPublishers.ofString(envelopeCodec.encode(signed)))
                .build();
        try {
            HttpResponse<String> answer = client.send(http, HttpResponse.BodyHandlers.ofString());
            JsonNode root = MAPPER.readTree(answer.body() == null ? "" : answer.body());
            return statusOf(root.path("status").asText(null))
                    == io.matrix.contract.bridge.BridgeDecision.Status.ACCEPTED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException e) {
            // The cancellation could not be delivered. The caller must learn that: a
            // cancellation that was silently lost is a task that keeps running.
            return false;
        }
    }

    public Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
        if (request == null) {
            throw new IllegalArgumentException("there is no request to dispatch");
        }
        BridgePayload payload = BridgePayload.ofTaskRequest(request, request.provenance());
        BridgeEnvelope envelope = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                request.requestId(), sentAt, senderId, BridgeCapability.INFERENCE_REQUEST,
                payloadCodec.encode(payload), null);
        BridgeEnvelope signed = envelope.withSignature(envelope.sign(signer));

        HttpRequest http = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + TASK_PATH))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header(BridgeProtocol.HEADER_PROTOCOL, BridgeProtocol.NAME)
                .header(BridgeProtocol.HEADER_VERSION, String.valueOf(BridgeProtocol.VERSION))
                .header(BridgeProtocol.HEADER_REQUEST_ID, request.requestId())
                .header(BridgeProtocol.HEADER_SENDER_ID, senderId)
                .header(BridgeProtocol.HEADER_SIGNATURE, signed.signature())
                .POST(HttpRequest.BodyPublishers.ofString(envelopeCodec.encode(signed)))
                .build();

        IOException lastFailure = null;
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            try {
                HttpResponse<String> answer = client.send(http,
                        HttpResponse.BodyHandlers.ofString());
                return read(answer.body(), request.requestId());
            } catch (IOException e) {
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
                        throw new TaskDispatchException("the dispatch was interrupted",
                                interrupted);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TaskDispatchException("the dispatch was interrupted", e);
            }
        }
        throw new TaskDispatchException("the peer did not answer after "
                + retryPolicy.maxAttempts() + " attempt(s)", lastFailure);
    }

    /**
     * Reads the peer's answer.
     *
     * @param body      the body of the answer
     * @param requestId the request id
     * @return what the peer answered
     */
    private Dispatch read(String body, String requestId) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body == null ? "" : body);
        } catch (IOException e) {
            return Dispatch.empty(requestId, BridgeDecision.Status.REFUSED,
                    "the peer's answer could not be read");
        }
        BridgeDecision.Status status = statusOf(root.path("status").asText(null));
        String reason = root.path("reason").asText("");
        Optional<CognitiveTaskResponse> response = Optional.empty();
        boolean responseSigned = false;
        String responseEnvelope = embedded(root.path("response"));
        if (responseEnvelope != null) {
            Optional<CognitiveTaskResponse> verified = verifiedResponse(responseEnvelope);
            response = verified;
            responseSigned = verified.isPresent();
        }
        Optional<CognitiveTaskReport> report = Optional.empty();
        boolean reportSigned = false;
        Optional<io.matrix.contract.bridge.BridgeEnvelope> reportEnvelope = Optional.empty();
        String reportJson = embedded(root.path("report"));
        if (reportJson != null) {
            Optional<CognitiveTaskReport> verified = verifiedReport(reportJson);
            report = verified;
            reportSigned = verified.isPresent();
            if (reportSigned) {
                reportEnvelope = Optional.of(envelopeCodec.decode(reportJson));
            }
        }
        return new Dispatch(requestId, status, reason, response, report, responseSigned,
                reportSigned, reportEnvelope);
    }

    /**
     * The envelope the peer embedded in the answer, as text.
     *
     * <p>The peer embeds the signed envelope as a JSON object, so it is read back out of
     * the parsed answer rather than copied as a string; either shape would do, but the
     * object is the one that cannot be confused with a field the peer made up.</p>
     *
     * @param node the node of the answer
     * @return the envelope's JSON, or null when the peer sent none
     */
    private static String embedded(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            String text = node.asText();
            return text.isBlank() ? null : text;
        }
        try {
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The response, when its signature verifies.
     *
     * @param envelope the encoded signed envelope
     * @return the response, or empty when the envelope cannot be trusted
     */
    private Optional<CognitiveTaskResponse> verifiedResponse(String envelope) {
        try {
            BridgeEnvelope signed = envelopeCodec.decode(envelope);
            if (!signed.verify(signer)) {
                return Optional.empty();
            }
            BridgePayload payload = payloadCodec.decode(signed.payload());
            return Optional.ofNullable(payload.taskResponse());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * The report, when its signature verifies.
     *
     * @param envelope the encoded signed envelope
     * @return the report, or empty when the envelope cannot be trusted
     */
    private Optional<CognitiveTaskReport> verifiedReport(String envelope) {
        try {
            BridgeEnvelope signed = envelopeCodec.decode(envelope);
            if (!signed.verify(signer)) {
                return Optional.empty();
            }
            BridgePayload payload = payloadCodec.decode(signed.payload());
            return Optional.ofNullable(payload.taskReport());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * The status the peer named, or a refusal when it named none.
     */
    private static BridgeDecision.Status statusOf(String name) {
        if (name == null || name.isBlank()) {
            return BridgeDecision.Status.REFUSED;
        }
        for (BridgeDecision.Status status : BridgeDecision.Status.values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return BridgeDecision.Status.REFUSED;
    }
}
