package io.matrix.api.bridge;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.matrix.contract.Outcome;
import io.matrix.contract.bridge.BridgeAnswer;
import io.matrix.contract.bridge.BridgeAuditRecord;
import io.matrix.contract.bridge.BridgeBudget;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgeEnvelopeCodec;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.BridgeReceiver;
import io.matrix.contract.bridge.BridgeRefusalReason;
import io.matrix.contract.bridge.BridgeSink;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import io.matrix.contract.bridge.CognitiveTaskReport;
import io.matrix.contract.bridge.CognitiveTaskResponse;
import io.matrix.contract.bridge.HmacBridgeSigner;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * W53 — chain B's receiver of remote cognitive tasks.
 *
 * <p><b>What this is.</b> The second kind of work the bridge carries: not a proposition,
 * but a request that the peer <b>execute</b> something. The request arrives over the same
 * governed boundary as the knowledge messages, is authenticated the same way, is bounded
 * the same way, and is audited the same way — and it is the same receiver that checks all
 * of that, so a task cannot take a path the knowledge messages do not.</p>
 *
 * <p><b>The order of operations, which is the whole design.</b> The request is
 * <b>durably admitted before it is executed</b>: the inbox row is written with the
 * request id, the payload digest and the state <code>ACCEPTED</code>, and only then does
 * the cognitive path run. A duplicate of an admitted request replays the stored result;
 * a duplicate with a different payload is refused as a conflict; and a request found
 * unfinished after a restart is visible to the reconciliation pass rather than being
 * silently re-run.</p>
 *
 * <p><b>What the answer carries.</b> The decision — accepted or refused, with the reason —
 * and, when the task produced one, the <b>signed</b> response envelope. The response is
 * signed because the mandate requires the response to be authenticated as well as the
 * request: a caller that trusts an unsigned answer has no way to know who produced it.</p>
 */
public final class TaskBridge {

    /** The environment variable that carries the shared secret. */
    public static final String SECRET_ENV = "MATRIX_BRIDGE_SECRET";

    /** The system property that carries the shared secret. */
    public static final String SECRET_PROPERTY = "matrix.bridge.secret";

    /** The path the remote cognitive task arrives at. */
    public static final String TASK_PATH = "/v1/bridge/task";

    /** The mapper the answer and the response use. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private final BridgeReceiver receiver;
    private final RemoteTaskStore store;
    private final CognitiveCapability capabilities;
    private final BridgeEnvelopeCodec envelopeCodec = new BridgeEnvelopeCodec();
    private final BridgePayloadCodec payloadCodec = new BridgePayloadCodec();
    private final byte[] secret;

    /**
     * A receiver with no capabilities and no inbox.
     */
    public TaskBridge() {
        this(null, null, null, null);
    }

    /**
     * The receiver.
     *
     * @param store        chain B's durable inbox
     * @param capabilities the capabilities this bridge serves
     * @param secret       the shared secret, or null when none is configured
     */
    public TaskBridge(RemoteTaskStore store, CognitiveCapability capabilities, byte[] secret) {
        this(store, capabilities, secret, null);
    }

    /**
     * The receiver.
     *
     * @param store        chain B's durable inbox
     * @param capabilities the capabilities this bridge serves
     * @param secret       the shared secret, or null when none is configured
     * @param clock        the source of the instant, for the tests
     */
    public TaskBridge(RemoteTaskStore store, CognitiveCapability capabilities, byte[] secret,
                      java.util.function.Supplier<String> clock) {
        this.store = store;
        this.capabilities = capabilities == null ? new CognitiveCapability() : capabilities;
        this.secret = secret;
        this.receiver = new BridgeReceiver(
                secret == null ? null : new HmacBridgeSigner(secret),
                EnumSet.of(BridgeCapability.INFERENCE_REQUEST,
                        BridgeCapability.INFERENCE_CANCEL),
                BridgeBudget.standard(),
                // W53 — the registry is the durable inbox, and there is NO in-memory
                // fallback: a bridge without a durable inbox cannot serve a task at all.
                // The W52 defect — a process-local registry that loses the record of a
                // delivery when the process restarts — must not survive as a
                // configuration someone can switch on.
                store == null ? DurableBridgeIdempotency.refusing()
                        : new DurableBridgeIdempotency(store),
                new TaskSink());
        this.clock = clock == null ? () -> Instant.now().toString() : clock;
    }

    /** The source of the instant. */
    private final java.util.function.Supplier<String> clock;

    /**
     * The measurement of the delivery being handled on this thread.
     *
     * <p><b>Why a thread-local and not a field.</b> The peer serves concurrent
     * deliveries, so a field would mix the stages of two tasks. The measurement is
     * thread-confined: the receiver calls the sink on the thread that handles the
     * delivery, and the delivery's own timing is taken on that same thread. The holder
     * is removed when the delivery ends, so nothing is kept alive after it.</p>
     */
    private final ThreadLocal<PeerStages> stages = new ThreadLocal<>();

    /** The value of a stage that was not measured. */
    static final double NOT_MEASURED = -1.0;

    /**
     * The shared secret, when the environment or the system property carries one.
     *
     * @return the secret, or empty
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
     * A receiver configured from the environment.
     *
     * @param store        chain B's durable inbox
     * @param capabilities the capabilities this bridge serves
     * @return the receiver
     */
    public static TaskBridge configured(RemoteTaskStore store, CognitiveCapability capabilities) {
        return new TaskBridge(store, capabilities, secret().orElse(null));
    }

    /**
     * The capability set this bridge enforces.
     *
     * <p>The owner of the bridge holds the set, so that a capability can be withdrawn
     * while the process runs; the bridge itself never widens it.</p>
     *
     * @return the capability set
     */
    public CognitiveCapability capabilities() {
        return capabilities;
    }

    /**
     * The capabilities this bridge serves right now.
     *
     * <p>The set is the allowlist the peer really runs, not a declaration: a withdrawn
     * capability is not in it any more. The mandate's §13.2 asks both processes to report
     * their active capabilities, and this is what the peer reports — the ids that can be
     * executed, and nothing else. No class name, no plugin id and no method crosses with
     * them.</p>
     *
     * @return the ids of the capabilities this bridge serves
     */
    public java.util.Set<String> servedCapabilities() {
        return capabilities.served();
    }

    /**
     * The answer to a delivery, as the body of the HTTP response.
     *
     * @param body the body of the delivery
     * @return the JSON answer: the decision, and the signed response when there is one
     */
    public String answer(String body) {
        BridgeAnswer answer;
        String response = null;
        String report = null;
        PeerStages measured = new PeerStages();
        stages.set(measured);
        String requestId = null;
        try {
            BridgeEnvelope envelope = envelopeCodec.decode(body);
            requestId = envelope.requestId();
            long deliveryStarted = System.nanoTime();
            answer = receiver.receiveAnswer(envelope, System.currentTimeMillis());
            measured.deliveryMillis = millisSince(deliveryStarted);
            long signingStarted = System.nanoTime();
            response = storedResponse(envelope.requestId());
            report = signedReport(envelope.requestId());
            measured.answerSigningMillis = millisSince(signingStarted);
        } catch (RuntimeException e) {
            answer = BridgeAnswer.of(BridgeDecision.refused("audit-unknown", "audit-unknown",
                    BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the delivery could not be read: " + e.getMessage()));
        } finally {
            // A delivery that failed is measured too: the stages it did reach are the
            // stages that happened, and a measurement that is written only on the happy
            // path describes the successful deliveries alone.
            recordStages(requestId, measured);
            stages.remove();
        }
        return jsonOf(answer, response, report);
    }

    /**
     * The peer's own measurement of one delivery, in milliseconds.
     *
     * <p>The measurement is written to the peer's own row and nowhere else. The answer
     * contract does not change: the caller receives exactly what it received before, and
     * the reader of the performance report reads the peer's own file.</p>
     *
     * @param requestId the request the delivery carried
     * @param measured  the measurement
     */
    private void recordStages(String requestId, PeerStages measured) {
        if (store == null || requestId == null) {
            return;
        }
        String json = measured.asJson();
        if (json == null) {
            return;
        }
        try {
            store.recordStages(requestId, json);
        } catch (SQLException e) {
            // A measurement must never break the delivery: the task's result and its
            // audit record are already written, and a missing measurement costs the
            // report a number, not the peer a task.
        }
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
     * The signed response of a request, when there is one.
     *
     * <p><b>Why the response travels as a signed envelope and not as a bare record.</b>
     * The mandate requires the response to be authenticated as well as the request: a
     * caller that trusts an unsigned answer has no way to know who produced it. The
     * envelope is the same canonical form every other message uses, so the reader
     * verifies it with the machinery it already has.</p>
     *
     * @param requestId the request
     * @return the encoded signed envelope, or null when there is no response
     */
    private String storedResponse(String requestId) {
        if (store == null || requestId == null) {
            return null;
        }
        try {
            Optional<RemoteTaskStore.Row> row = store.find(requestId);
            if (row.isEmpty()) {
                return null;
            }
            if (row.get().resultJson() == null || row.get().resultJson().isBlank()) {
                // The task is still running. The caller must be told so: a delivery that
                // is answered with silence leaves the caller to discover the delay as a
                // failure of its own, while the peer is in fact working on the task.
                return runningResponse(row.get());
            }
            CognitiveTaskResponse response = MAPPER.readValue(row.get().resultJson(),
                    CognitiveTaskResponse.class);
            BridgeEnvelope unsigned = new BridgeEnvelope(BridgeProtocol.NAME,
                    BridgeProtocol.VERSION, requestId, response.at() == null
                            ? clock.get() : response.at(), "chain-b-brain-runtime",
                    BridgeCapability.INFERENCE_RESPONSE,
                    payloadCodec.encode(BridgePayload.ofTaskResponse(response,
                            io.matrix.contract.Provenance.of("chain-b", clock.get()))), null);
            BridgeEnvelope signed = unsigned.withSignature(
                    unsigned.sign(new HmacBridgeSigner(secret)));
            return envelopeCodec.encode(signed);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The signed report of the task, when there is one.
     *
     * <p><b>Why the report is signed separately.</b> The response says whether the
     * execution finished; the report says what the peer thinks, and it may contradict.
     * The peer that reads it must be able to check who produced it, so the report travels
     * as its own signed envelope — the same canonical form, the same signer, the same
     * verification the reader already performs on every message that crosses the
     * bridge.</p>
     *
     * <p><b>What the report claims and what it does not.</b> The relation is
     * {@code INDEPENDENT}: the peer answered the question it was given and asserts no
     * relation to any proposition of the reader's. The lineage names the records the
     * reader supplied and the peer used — which is exactly what lets the reader notice
     * that a proposition came back instead of being independently observed. The
     * confidence is {@code DECLARED}: the cycle reports its own confidence, and the
     * reader is told so rather than being left to guess.</p>
     *
     * @param requestId the request the report answers
     * @return the encoded signed envelope, or null when the task produced no report
     */
    /**
     * The status a row's state means to the caller.
     *
     * <p>A row that is still in flight is {@code IN_PROGRESS}; a row that ended without
     * a result keeps its own ending — a refused task is refused, a failed one failed.
     * Reporting every unfinished row as "still running" would tell the caller to wait
     * for work that is never going to happen.</p>
     *
     * @param row the row
     * @return the status
     */
    private static CognitiveTaskResponse.Status statusOf(RemoteTaskStore.Row row) {
        return switch (row.state()) {
            case ACCEPTED, RUNNING -> CognitiveTaskResponse.Status.IN_PROGRESS;
            case REFUSED -> CognitiveTaskResponse.Status.REFUSED;
            case UNSUPPORTED -> CognitiveTaskResponse.Status.UNSUPPORTED;
            case FAILED -> CognitiveTaskResponse.Status.FAILED;
            case TIMED_OUT -> CognitiveTaskResponse.Status.TIMED_OUT;
            case CANCELLED -> CognitiveTaskResponse.Status.CANCELLED;
            case COMPLETED -> CognitiveTaskResponse.Status.COMPLETED;
        };
    }

    /**
     * The state a task's ending means in the peer's own record.
     *
     * <p><b>Why the state must match the reason.</b> A task the peer does not serve and a
     * task whose execution failed are different facts, and the row is where the peer
     * keeps what happened. Writing "failed" for a capability that does not exist would
     * make the record say the peer tried and could not, when the truth is that it never
     * served that capability at all.</p>
     *
     * @param status the peer's own status
     * @return the state
     */
    private static RemoteTaskStore.State stateOf(CognitiveTaskResponse.Status status) {
        if (status == null) {
            return RemoteTaskStore.State.FAILED;
        }
        return switch (status) {
            case REFUSED -> RemoteTaskStore.State.REFUSED;
            case UNSUPPORTED -> RemoteTaskStore.State.UNSUPPORTED;
            case TIMED_OUT -> RemoteTaskStore.State.TIMED_OUT;
            case CANCELLED -> RemoteTaskStore.State.CANCELLED;
            default -> RemoteTaskStore.State.FAILED;
        };
    }

    /**
     * The answer to a task that is still running.
     *
     * <p><b>Why this exists.</b> The idempotency rules say that a duplicate request
     * returns the existing status. A task that is running has an existing status, and
     * saying nothing is not saying it: the caller would spend its retry budget
     * discovering a delay that the peer knows about and could have said.</p>
     *
     * @param row the row of the task
     * @return the signed envelope of the answer
     */
    private String runningResponse(RemoteTaskStore.Row row) {
        CognitiveTaskResponse.Status status = statusOf(row);
        CognitiveTaskResponse response = new CognitiveTaskResponse(
                row.taskId(),
                row.requestId(),
                new io.matrix.contract.Correlation("corr-" + row.requestId(), "ep-parent",
                        "ep-" + row.requestId(), row.requestId()),
                row.payloadHash(),
                "chain-b-brain-runtime",
                row.capabilityId(),
                row.capabilityVersion(),
                status,
                null,
                null,
                CognitiveTaskResponse.ConfidenceBasis.UNKNOWN,
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                io.matrix.contract.Provenance.of("chain-b", clock.get()),
                status == CognitiveTaskResponse.Status.IN_PROGRESS
                        ? "the task is still running on this peer"
                        : "the task did not finish on this peer: "
                                + (row.refusalReason() == null ? row.state().name()
                                        : row.refusalReason()),
                null,
                row.traceReference(),
                status == CognitiveTaskResponse.Status.IN_PROGRESS
                        ? "the task is still running on this peer"
                        : "the task did not finish on this peer: "
                                + (row.refusalReason() == null ? row.state().name()
                                        : row.refusalReason()),
                clock.get());
        BridgeEnvelope unsigned = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                row.requestId(), clock.get(), "chain-b-brain-runtime",
                BridgeCapability.INFERENCE_RESPONSE,
                payloadCodec.encode(BridgePayload.ofTaskResponse(response,
                        io.matrix.contract.Provenance.of("chain-b", clock.get()))), null);
        BridgeEnvelope signed = unsigned.withSignature(unsigned.sign(new HmacBridgeSigner(secret)));
        return envelopeCodec.encode(signed);
    }

    private String signedReport(String requestId) {
        if (store == null || requestId == null) {
            return null;
        }
        try {
            Optional<RemoteTaskStore.Row> row = store.find(requestId);
            if (row.isEmpty()) {
                return null;
            }
            RemoteTaskStore.Row stored = row.get();
            if (stored.resultJson() == null || stored.resultJson().isBlank()) {
                return null;
            }
            CognitiveTaskResponse response = MAPPER.readValue(stored.resultJson(),
                    CognitiveTaskResponse.class);
            CognitiveTaskReport report = new CognitiveTaskReport(
                    BridgeProtocol.VERSION,
                    response.taskId(),
                    response.requestId(),
                    subjectOf(response),
                    null,
                    CognitiveTaskReport.Relation.INDEPENDENT,
                    response.confidence(),
                    response.confidenceBasis() == null
                            ? CognitiveTaskReport.ConfidenceBasis.UNKNOWN
                            : CognitiveTaskReport.ConfidenceBasis.valueOf(
                                    response.confidenceBasis().name()),
                    lineageOf(response),
                    response.responderId(),
                    response.capabilityId(),
                    response.capabilityVersion(),
                    limitationsOf(response),
                    io.matrix.contract.Provenance.of("chain-b", response.at() == null
                            ? clock.get() : response.at()),
                    response.at() == null ? clock.get() : response.at());
            BridgeEnvelope unsigned = new BridgeEnvelope(BridgeProtocol.NAME,
                    BridgeProtocol.VERSION, requestId, report.at(), "chain-b-brain-runtime",
                    BridgeCapability.INFERENCE_RESPONSE,
                    payloadCodec.encode(BridgePayload.ofTaskReport(report,
                            io.matrix.contract.Provenance.of("chain-b", report.at()))), null);
            BridgeEnvelope signed = unsigned.withSignature(
                    unsigned.sign(new HmacBridgeSigner(secret)));
            return envelopeCodec.encode(signed);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The proposition the report is about: the question the peer was asked.
     */
    private static String subjectOf(CognitiveTaskResponse response) {
        return response.taskId();
    }

    /**
     * The records the peer was given and used.
     */
    private static List<String> lineageOf(CognitiveTaskResponse response) {
        return List.of();
    }

    /**
     * The limits the peer stated.
     */
    private static List<String> limitationsOf(CognitiveTaskResponse response) {
        return response.contradictions() == null ? List.of() : response.contradictions();
    }

    /**
     * The answer as JSON.
     *
     * @param answer   the decision
     * @param response the signed response envelope, when the task produced one
     * @return the JSON
     */
    private String jsonOf(BridgeAnswer answer, String response, String report) {
        StringBuilder json = new StringBuilder();
        json.append("{\"status\":\"").append(answer.decision().status().name()).append('"');
        json.append(",\"requestId\":\"").append(esc(answer.decision().requestId())).append('"');
        json.append(",\"auditId\":\"").append(esc(answer.decision().auditId())).append('"');
        json.append(",\"reason\":\"").append(esc(answer.decision().reason())).append('"');
        // The response and the report are embedded as the JSON objects they are, not as
        // strings: the reader parses the answer once and gets the envelopes back.
        if (response != null && !response.isBlank()) {
            json.append(",\"response\":").append(response);
        }
        if (report != null && !report.isBlank()) {
            json.append(",\"report\":").append(report);
        }
        json.append('}');
        return json.toString();
    }

    /** Escapes a value for the JSON answer. */
    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /**
     * Refuses a task and says so durably.
     *
     * <p><b>Why a refusal is not a throw.</b> The caller has to learn <i>why</i> its
     * task was not done, and it has to learn it from the peer rather than from its own
     * timeout. So the refusal is written into the inbox and returned as a signed
     * response with the status the contract defines — the same shape as any other
     * answer, only with the honest status.</p>
     *
     * @param envelope the request's envelope
     * @param request  the request
     * @param reason   why the task was refused
     * @param now      the instant
     * @return the outcome of the refusal
     */
    private Outcome refuseTask(BridgeEnvelope envelope, CognitiveTaskRequest request,
                                String reason, String now) {
        CognitiveTaskResponse response = new CognitiveTaskResponse(
                request.taskId(),
                request.requestId(),
                request.correlation(),
                request.payloadHash(),
                "chain-b-brain-runtime",
                request.requiredCapability(),
                request.capabilityVersion(),
                CognitiveTaskResponse.Status.REFUSED,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                io.matrix.contract.Provenance.of("chain-b", now),
                "the task was refused before it was executed",
                0L,
                null,
                reason,
                now);
        try {
            store.markTerminal(request.requestId(), RemoteTaskStore.State.REFUSED, reason,
                    MAPPER.writeValueAsString(response), now);
        } catch (Exception e) {
            return new Outcome("outcome-" + envelope.requestId(),
                    "action-" + envelope.requestId(), null, null, Outcome.Status.FAILED,
                    "the refusal could not be written", false, 1.0,
                    "the refusal could not be written: " + e.getMessage(), now);
        }
        return new Outcome("outcome-" + envelope.requestId(),
                "action-" + envelope.requestId(), null, null, Outcome.Status.REFUSED,
                reason, false, 1.0, reason, now);
    }

    /**
     * The sink: the task is admitted durably, executed, and persisted.
     *
     * <p>This is the only place where the brain is touched, and it runs only after the
     * signature, the capability, the budget and the idempotency have all been checked.</p>
     */
    /**
     * Withdraws a task on the caller's behalf.
     *
     * <p><b>What the peer does with a withdrawal.</b> A task it has not started is
     * withdrawn outright and will never be executed. A task it is running is recorded as
     * withdrawn: the brain cannot be interrupted, so the work finishes, and the record
     * says what became of it. A task it has already finished is left alone — a
     * cancellation cannot undo a committed effect.</p>
     *
     * @param envelope the signed envelope
     * @param cancel   the typed cancellation
     * @return what the peer decided
     */
    private Outcome cancelTask(BridgeEnvelope envelope,
                               io.matrix.contract.bridge.CognitiveTaskCancel cancel) {
        String now = clock.get();
        if (store == null) {
            return new Outcome("outcome-" + envelope.requestId(),
                    "action-" + envelope.requestId(), null, null, Outcome.Status.FAILED,
                    "this bridge has no durable inbox, so it cannot withdraw a task", false,
                    1.0, "this bridge has no durable inbox", now);
        }
        java.util.Optional<RemoteTaskStore.State> state;
        try {
            state = store.cancel(cancel.requestId(), now);
        } catch (SQLException e) {
            return new Outcome("outcome-" + envelope.requestId(),
                    "action-" + envelope.requestId(), null, null, Outcome.Status.FAILED,
                    "the task inbox could not be written", false, 1.0,
                    "the task inbox could not be written: " + e.getMessage(), now);
        }
        if (state.isEmpty()) {
            return new Outcome("outcome-" + envelope.requestId(),
                    "action-" + envelope.requestId(), null, null, Outcome.Status.REFUSED,
                    "this peer holds no such task", false, 1.0,
                    "this peer holds no such task", now);
        }
        RemoteTaskStore.State after = state.get();
        boolean withdrawn = after == RemoteTaskStore.State.CANCELLED
                || after == RemoteTaskStore.State.RUNNING;
        return new Outcome("outcome-" + envelope.requestId(),
                "action-" + envelope.requestId(), null, null,
                withdrawn ? Outcome.Status.SUCCEEDED : Outcome.Status.REFUSED,
                after == RemoteTaskStore.State.CANCELLED
                        ? "the task was withdrawn before it started"
                        : after == RemoteTaskStore.State.RUNNING
                                ? "the task is running and the withdrawal was recorded"
                                : "the task had already finished and was not withdrawn",
                withdrawn, 1.0, "the withdrawal was accepted", now);
    }

    private final class TaskSink implements BridgeSink {

        @Override
        public Outcome accept(BridgeEnvelope envelope) {
            BridgePayload payload;
            try {
                payload = payloadCodec.decode(envelope.payload());
            } catch (RuntimeException e) {
                return new Outcome("outcome-" + envelope.requestId(),
                        "action-" + envelope.requestId(), null, null, Outcome.Status.FAILED,
                        "the task payload could not be read", false, 1.0,
                        "the payload could not be read: " + e.getMessage(), clock.get());
            }
            if (payload.taskCancel() != null) {
                return cancelTask(envelope, payload.taskCancel());
            }
            CognitiveTaskRequest request = payload.taskRequest();
            if (request == null) {
                return new Outcome("outcome-" + envelope.requestId(),
                        "action-" + envelope.requestId(), null, null, Outcome.Status.FAILED,
                        "the message carries no task request", false, 1.0,
                        "the message carries no task request", clock.get());
            }
            if (store == null) {
                return new Outcome("outcome-" + envelope.requestId(),
                        "action-" + envelope.requestId(), null, null, Outcome.Status.FAILED,
                        "this bridge has no durable inbox, so it cannot accept a task", false,
                        1.0, "this bridge has no durable inbox", clock.get());
            }
            String now = clock.get();
            RemoteTaskStore.Admission admission;
            long inboxStarted = System.nanoTime();
            try {
                admission = store.admit(request, envelope.payloadDigest(), now);
                measure("inboxWrite", millisSince(inboxStarted));
            } catch (SQLException e) {
                return new Outcome("outcome-" + envelope.requestId(),
                        "action-" + envelope.requestId(), null, null, Outcome.Status.FAILED,
                        "the task inbox could not be written", false, 1.0,
                        "the task inbox could not be written: " + e.getMessage(), now);
            }
            if (admission.decision() == RemoteTaskStore.Decision.INBOX_FULL) {
                // The peer is at its bound. Refusing is honest: a queue that grows
                // without limit would answer the caller late, and a late answer is a
                // timeout the caller had to discover on its own. The refusal is written
                // down and returned, so the caller learns why from the peer itself.
                return refuseTask(envelope, request, RemoteTaskStore.INBOX_FULL_REASON, now);
            }
            if (admission.decision() == RemoteTaskStore.Decision.CONFLICT) {
                return new Outcome("outcome-" + envelope.requestId(),
                        "action-" + envelope.requestId(), null, null, Outcome.Status.REFUSED,
                        "the request id was already used with a different payload", false, 1.0,
                        "the request id was already used with a different payload", now);
            }
            if (admission.decision() == RemoteTaskStore.Decision.DUPLICATE_COMPLETED
                    || admission.decision() == RemoteTaskStore.Decision.DUPLICATE_TERMINAL
                    || admission.decision()
                            == RemoteTaskStore.Decision.DUPLICATE_IN_PROGRESS) {
                // The duplicate is answered from the durable record: no second execution,
                // and the caller gets the result the first delivery produced.
                RemoteTaskStore.Row row = admission.row();
                return new Outcome("outcome-" + envelope.requestId(),
                        "action-" + envelope.requestId(), null, null,
                        row.state() == RemoteTaskStore.State.COMPLETED
                                ? Outcome.Status.SUCCEEDED : Outcome.Status.REFUSED,
                        row.state() == RemoteTaskStore.State.COMPLETED
                                ? "the task was already completed" : "the task is still running",
                        row.state() == RemoteTaskStore.State.COMPLETED, 0.0,
                        "the request was already admitted and was not executed again", now);
            }
            if (!request.withinPayloadLimit()) {
                // The task declared how large its own input may be; a request that
                // exceeds its own bound is refused before it reaches the brain, and the
                // refusal is durable like every other outcome.
                return refuseTask(envelope, request,
                        "the request exceeds the payload limit it declares", now);
            }
            long stateStarted = System.nanoTime();
            try {
                store.markRunning(request.requestId(), now);
                measure("stateWrite", millisSince(stateStarted));
            } catch (SQLException e) {
                return new Outcome("outcome-" + envelope.requestId(),
                        "action-" + envelope.requestId(), null, null, Outcome.Status.FAILED,
                        "the task state could not be written", false, 1.0,
                        "the task state could not be written: " + e.getMessage(), now);
            }
            long executionStarted = System.nanoTime();
            CognitiveCapability.Execution execution = capabilities.execute(request);
            measure("execution", millisSince(executionStarted));
            // The peer honours the deadline the task gave it. The work may have been done
            // and the answer may still be worth keeping — a deadline that passes does not
            // delete a result — but the caller must learn that the answer came late.
            long duration = execution.durationMillis() == null ? 0L : execution.durationMillis();
            boolean overran = request.deadlineMillis() > 0L
                    && duration > request.deadlineMillis();
            // The response carries ONLY what the production path exposed: the reply, the
            // confidence, the modulators that fired and the duration. The hypothesis, the
            // prediction, the outcome and the supporting observations stay empty, because
            // the capability does not expose them and a remote answer must not carry a
            // fabricated reasoning step.
            CognitiveTaskResponse response = new CognitiveTaskResponse(
                    request.taskId(),
                    request.requestId(),
                    request.correlation(),
                    request.payloadHash(),
                    "chain-b-brain-runtime",
                    request.requiredCapability(),
                    request.capabilityVersion(),
                    overran ? CognitiveTaskResponse.Status.TIMED_OUT : execution.status(),
                    execution.result(),
                    execution.confidence(),
                    execution.basis(),
                    List.of(),
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    execution.trace(),
                    io.matrix.contract.Provenance.of("chain-b", now),
                    "the capability " + request.requiredCapability() + " was named by the task",
                    duration,
                    execution.traceReference(),
                    overran ? "the execution took " + duration + " ms, longer than the "
                            + request.deadlineMillis() + " ms the task allowed"
                            : execution.refusalReason(),
                    now);
            long outcomeStarted = System.nanoTime();
            try {
                String json = MAPPER.writeValueAsString(response);
                // A withdrawal that arrived while the task was running closes the row as
                // cancelled — unless the effect was already committed, in which case the
                // task did happen and the record must say so.
                boolean withdrawn = store.cancelRequested(request.requestId());
                if (withdrawn && execution.status() == CognitiveTaskResponse.Status.COMPLETED
                        && !overran && !execution.effectCommitted()) {
                    store.markTerminal(request.requestId(), RemoteTaskStore.State.CANCELLED,
                            "the task was withdrawn by the requester while it was running",
                            json, now);
                } else if (execution.status() == CognitiveTaskResponse.Status.COMPLETED
                        && !overran) {
                    store.markCompleted(request.requestId(), json, execution.traceReference(),
                            execution.effectCommitted(), now);
                } else {
                    store.markTerminal(request.requestId(),
                            overran ? RemoteTaskStore.State.TIMED_OUT
                                    : stateOf(execution.status()),
                            overran ? "the execution took " + duration + " ms, longer than the "
                                    + request.deadlineMillis() + " ms the task allowed"
                                    : execution.refusalReason(),
                            json, now);
                }
                measure("outcomeWrite", millisSince(outcomeStarted));
            } catch (Exception e) {
                return new Outcome("outcome-" + envelope.requestId(),
                        "action-" + envelope.requestId(), null, null, Outcome.Status.FAILED,
                        "the result could not be written", false, 1.0,
                        "the result could not be written: " + e.getMessage(), now);
            }
            return new Outcome("outcome-" + envelope.requestId(),
                    "action-" + envelope.requestId(), null, null,
                    execution.status() == CognitiveTaskResponse.Status.COMPLETED && !overran
                            ? Outcome.Status.SUCCEEDED : Outcome.Status.FAILED,
                    execution.result(), execution.status()
                            == CognitiveTaskResponse.Status.COMPLETED,
                    execution.confidence() == null ? 1.0 : execution.confidence(),
                    "the task was executed by the capability " + request.requiredCapability(),
                    now);
        }
    }

    /** How many nanoseconds make a millisecond. */
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0;

    /**
     * The milliseconds that passed since a monotonic instant.
     *
     * @param startedNanos the instant, from {@link System#nanoTime()}
     * @return the elapsed milliseconds
     */
    private static double millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / NANOS_PER_MILLISECOND;
    }

    /**
     * Records one measured stage of the delivery being handled.
     *
     * @param stage the name of the stage
     * @param millis how long it took
     */
    private void measure(String stage, double millis) {
        PeerStages measured = stages.get();
        if (measured != null) {
            measured.put(stage, millis);
        }
    }

    /**
     * The peer's own measurement of one delivery.
     *
     * <p><b>What the numbers mean.</b> Every stage is timed with the same monotonic
     * clock, on the thread that handles the delivery: the durable inbox write, the write
     * that moves the request to running, the execution of the capability, the write of
     * the outcome, and the serialization and signing of the answer. The delivery total is
     * the whole reception, and the remainder — what is left of it once the peer's own
     * four writes and its execution are taken out — is the authentication and the
     * validation together with the receiver's own bookkeeping. It is a remainder and the
     * report says so; it is not a measurement of the signature alone.</p>
     *
     * <p><b>What it is not.</b> It is not a claim about the cost of the cryptography by
     * itself, and it never leaves the peer: the caller receives the same answer it
     * received before.</p>
     */
    private static final class PeerStages {

        /** The stages the peer measured, with their durations. */
        private final java.util.Map<String, Double> values = new java.util.TreeMap<>();

        /** The whole reception of the delivery. */
        private double deliveryMillis = NOT_MEASURED;

        /** The serialization and the signing of the answer. */
        private double answerSigningMillis = NOT_MEASURED;

        /** The stages whose sum is the peer's own work inside the delivery. */
        private static final String[] INNER_STAGES =
                {"inboxWrite", "stateWrite", "execution", "outcomeWrite"};

        /** Records one stage. */
        void put(String stage, double millis) {
            values.put(stage, millis);
        }

        /** The measurement as canonical JSON, or null when nothing was measured. */
        String asJson() {
            if (values.isEmpty()) {
                return null;
            }
            java.util.Map<String, Object> document = new java.util.TreeMap<>(values);
            if (deliveryMillis != NOT_MEASURED) {
                document.put("deliveryTotal", deliveryMillis);
            }
            if (answerSigningMillis != NOT_MEASURED) {
                document.put("answerSigning", answerSigningMillis);
            }
            Double inner = innerMillis();
            if (inner != null && deliveryMillis != NOT_MEASURED) {
                // The remainder, not a measurement of the signature alone: it is what
                // the reception cost beyond the peer's own four writes and its
                // execution, so it carries the authentication, the validation and the
                // receiver's bookkeeping together.
                document.put("authenticationAndValidation",
                        Math.max(0.0, deliveryMillis - inner));
            }
            try {
                return MAPPER.writeValueAsString(document);
            } catch (Exception e) {
                return null;
            }
        }

        /** The sum of the peer's own stages, or null when one of them is missing. */
        private Double innerMillis() {
            double sum = 0.0;
            for (String stage : INNER_STAGES) {
                Double value = values.get(stage);
                if (value == null) {
                    return null;
                }
                sum += value;
            }
            return sum;
        }
    }
}
