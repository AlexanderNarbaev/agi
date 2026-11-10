package io.matrix.cognitive.distributed;

import io.matrix.bridge.TaskDispatchException;
import io.matrix.bridge.TaskPublisher;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.DispatchIntent;
import io.matrix.cognitive.RemoteReportAssessor;
import io.matrix.contract.Correlation;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeEnvelopeCodec;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskReport;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import io.matrix.contract.bridge.CognitiveTaskResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * W53 — chain A's coordinator for a task that the peer must work on.
 *
 * <p><b>What this class is.</b> One coordinator, one lifecycle, one durable trace. The
 * mandate asks for a single explicit execution coordinator rather than a competing
 * global selector, and this is it for the distributed case: it resolves what the task
 * needs, builds a bounded request, writes down its intent <i>before</i> it acts, sends
 * the request through the governed crossing, checks what came back, evaluates it against
 * chain A's own evidence, records the verdict and completes the task.</p>
 *
 * <p><b>What it owns.</b> The dispatch intent, the answer, the report and the verdict —
 * all four in chain A's own store, so that the history of a task can be reconstructed
 * from durable state alone after a restart. The peer owns the execution and its trace;
 * chain A owns the decision.</p>
 *
 * <p><b>What it deliberately does not do.</b> It does not promote the remote result. It
 * does not decide that the peer was right. It does not write the peer's answer into the
 * epistemic store — that is the bridge's sink's work, and it writes a candidate. The
 * coordinator's verdict is about the task, not about the world's beliefs, and it is
 * computed by the assessor, which holds chain A's own evidence.</p>
 *
 * <p><b>The transport is a port.</b> The coordinator talks to a {@link TaskTransport},
 * not to an HTTP client, so that the same lifecycle runs against the real publisher and
 * against a test double, and so that the coordinator does not depend on the details of
 * how a message travels.</p>
 */
public final class CognitiveCoordinator {

    /**
     * The transport a dispatch travels over.
     */
    public interface TaskTransport {

        /**
         * Sends the request and returns what the peer answered.
         *
         * @param request the request
         * @param sentAt  the instant of the delivery
         * @return the peer's answer
         */
        TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt);

        /**
         * Tells the peer that a dispatched task is withdrawn.
         *
         * <p><b>Why this has a default that refuses.</b> A transport that cannot carry a
         * cancellation says so, instead of the coordinator believing that the peer was
         * told. The default returns false, and the coordinator records that fact: the
         * task is cancelled on chain A, and the peer was not told — which is a different
         * situation from the peer having cancelled it.</p>
         *
         * @param requestId the request to withdraw
         * @param reason    why the task is withdrawn
         * @param sentAt    the instant of the delivery
         * @return true when the peer accepted the cancellation
         */
        default boolean cancel(String requestId, String reason, String sentAt) {
            return false;
        }
    }

    /**
     * A task the coordinator is asked to arrange.
     *
     * @param taskId           the stable task id
     * @param capabilityId     the capability the peer must run
     * @param input            the bounded input the peer works on
     * @param expectation      what chain A expects the answer to look like
     * @param budgetMillis     the execution budget
     * @param deadlineMillis   the deadline
     * @param supplied         the records chain A supplied to the peer earlier
     */
    public record TaskSpec(
            String taskId,
            String capabilityId,
            String input,
            String expectation,
            long budgetMillis,
            long deadlineMillis,
            List<String> supplied,
            String parentEpisodeId) {

        /**
         * A task that belongs to no episode.
         *
         * <p>A task arranged outside a cognitive episode — through the HTTP entry point,
         * say — has no episode to name. The correlation then says so, instead of naming
         * an episode that does not exist.</p>
         *
         * @param taskId         the stable task id
         * @param capabilityId   the capability the peer must run
         * @param input          the bounded input the peer works on
         * @param expectation    what chain A expects the answer to look like
         * @param budgetMillis   the execution budget
         * @param deadlineMillis the deadline
         * @param supplied       the records chain A supplied to the peer earlier
         */
        public TaskSpec(String taskId, String capabilityId, String input, String expectation,
                        long budgetMillis, long deadlineMillis, List<String> supplied) {
            this(taskId, capabilityId, input, expectation, budgetMillis, deadlineMillis,
                    supplied, null);
        }
    }

    /**
     * What the coordinator did.
     *
     * @param taskId      the task
     * @param requestId   the request
     * @param state       where the dispatch is
     * @param verdict     the assessment of the answer, when there was one
     * @param reason      why, in the coordinator's words
     */
    public record Outcome(
            String taskId,
            String requestId,
            DispatchIntent.State state,
            Optional<RemoteReportAssessor.Verdict> verdict,
            String reason,
            Map<String, Long> timingsNanos) {

        /**
         * The outcome without a measurement.
         *
         * @param taskId    the task
         * @param requestId the request
         * @param state     where the dispatch ended
         * @param verdict   what chain A concluded
         * @param reason    why
         */
        public Outcome(String taskId, String requestId, DispatchIntent.State state,
                       Optional<RemoteReportAssessor.Verdict> verdict, String reason) {
            this(taskId, requestId, state, verdict, reason, Map.of());
        }
    }

    /**
     * The largest request payload the coordinator will build.
     *
     * <p>It is the limit the request contract itself enforces, and the coordinator does
     * not widen it: a bounded task is bounded before it is sent, not after.</p>
     */
    public static final int DEFAULT_MAX_PAYLOAD_BYTES = 4096;

    /**
     * The execution budget the coordinator gives a task when none is named.
     *
     * <p>It is the request contract's own default, named once here so that the kernel's
     * port and the HTTP entry point agree on what "no budget named" means.</p>
     */
    public static final long DEFAULT_BUDGET_MILLIS =
            io.matrix.contract.bridge.CognitiveTaskRequest.DEFAULT_BUDGET_MILLIS;

    /**
     * The deadline the coordinator gives a task when none is named.
     *
     * <p>It is the request contract's own default, named once here for the same reason.</p>
     */
    public static final long DEFAULT_DEADLINE_MILLIS =
            io.matrix.contract.bridge.CognitiveTaskRequest.DEFAULT_DEADLINE_MILLIS;

    /**
     * The largest number of dispatches the coordinator keeps in flight at once.
     *
     * <p>Bounded work means refusing the work that exceeds the bound, not queueing it
     * without limit: an unbounded queue is an unbounded memory and an unbounded
     * latency.</p>
     */
    public static final int DEFAULT_MAX_CONCURRENT_DISPATCHES = 4;

    /** The largest number of times one request may be replayed. */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** The number of nanoseconds in a millisecond. */
    private static final long NANOS_PER_MILLI = 1_000_000L;

    /** The context key under which the caller's expectation travels. */
    public static final String EXPECTATION_KEY = "expectation";

    /** The context key under which the records the caller supplied travel. */
    public static final String SUPPLIED_KEY = "supplied";

    private final CognitiveStore store;
    private final TaskTransport transport;
    private final RemoteReportAssessor assessor;
    private final String workspaceId;
    private final String senderId;
    private final Supplier<String> clock;
    private final DispatchPolicy policy;
    private final LongSupplier nanoClock;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private ReportSink reportSink;

    /**
     * The coordinator.
     *
     * @param store       chain A's durable store
     * @param transport   the transport the request travels over
     * @param assessor    what judges the peer's answer
     * @param workspaceId the workspace the task belongs to
     * @param senderId    the identity this process claims
     * @param clock       the source of the instants
     */
    public CognitiveCoordinator(CognitiveStore store, TaskTransport transport,
                                RemoteReportAssessor assessor, String workspaceId,
                                String senderId, Supplier<String> clock) {
        this(store, transport, assessor, workspaceId, senderId, clock,
                DispatchPolicy.standard(), System::nanoTime);
    }

    /**
     * The coordinator with its own bounds and its own monotonic clock.
     *
     * <p>The clock is monotonic and injectable on purpose: the deadline is a decision
     * about how long chain A waits, and a decision must not depend on the wall clock.
     * A test can advance it; production uses the system counter.</p>
     *
     * @param store      chain A's durable store
     * @param transport  the transport the request travels over
     * @param assessor   what judges the peer's answer
     * @param workspaceId the workspace the task belongs to
     * @param senderId   the identity this process claims
     * @param clock      the source of the instants
     * @param policy     the bounds the coordinator keeps
     * @param nanoClock  the monotonic source of elapsed time
     */
    public CognitiveCoordinator(CognitiveStore store, TaskTransport transport,
                                RemoteReportAssessor assessor, String workspaceId,
                                String senderId, Supplier<String> clock,
                                DispatchPolicy policy, LongSupplier nanoClock) {
        this.store = store;
        this.transport = transport;
        this.assessor = assessor;
        this.workspaceId = workspaceId;
        this.senderId = senderId;
        this.clock = clock;
        this.policy = policy == null ? DispatchPolicy.standard() : policy;
        this.nanoClock = nanoClock == null ? System::nanoTime : nanoClock;
    }

    /**
     * The coordinator that keeps the given bounds.
     *
     * @param policy the bounds
     * @return the coordinator
     */
    public CognitiveCoordinator withPolicy(DispatchPolicy policy) {
        return new CognitiveCoordinator(store, transport, assessor, workspaceId, senderId,
                clock, policy, nanoClock);
    }

    /**
     * The coordinator that measures elapsed time with the given monotonic source.
     *
     * @param nanoClock the monotonic source
     * @return the coordinator
     */
    public CognitiveCoordinator withNanoClock(LongSupplier nanoClock) {
        return new CognitiveCoordinator(store, transport, assessor, workspaceId, senderId,
                clock, policy, nanoClock);
    }

    /**
     * The coordinator that hands the peer's report to chain A's own bridge receiver.
     *
     * <p>Without a sink the report is evaluated but never enters the epistemic model; with
     * one it becomes a candidate in chain A's store — never an established belief, always a
     * secondary report with its origin, its responder and its capability attached.</p>
     *
     * @param sink the governed crossing of chain A
     * @return the coordinator
     */
    public CognitiveCoordinator withReportSink(ReportSink sink) {
        CognitiveCoordinator copy = new CognitiveCoordinator(store, transport, assessor,
                workspaceId, senderId, clock);
        copy.reportSink = sink;
        return copy;
    }

    /**
     * Hands the peer's report to chain A's own receiver and records what it decided.
     *
     * <p>The report goes through the crossing that already governs chain B's knowledge
     * messages — the same authentication, the same capability rights, the same audit. A
     * report that the receiver refuses is not silently dropped: the refusal is recorded
     * beside the dispatch.</p>
     *
     * @param intent the dispatch the report belongs to
     * @param report the signed report envelope, when the peer sent one
     * @return what the receiver decided, or null when there was no report to admit
     */
    private String admitReport(DispatchIntent intent,
                                Optional<io.matrix.contract.bridge.BridgeEnvelope> report) {
        if (report.isEmpty() || reportSink == null || intent == null) {
            return null;
        }
        io.matrix.contract.bridge.BridgeDecision decision = reportSink.accept(report.get());
        String recorded = decision.status() + ": " + decision.reason();
        store.recordReportDisposition(intent.requestId(), recorded, clock.get());
        return recorded;
    }

    /**
     * Arranges the task: dispatch, receive, evaluate, record.
     *
     * @param spec the task
     * @return what the coordinator did
     */
    public Outcome arrange(TaskSpec spec) {
        String now = clock.get();
        String requestId = "req-" + spec.taskId();
        CognitiveTaskRequest request = request(spec, requestId, now);
        Map<String, Long> timings = new LinkedHashMap<>();

        // The payload limit is checked BEFORE anything is written and BEFORE anything is
        // sent: a task whose input does not fit leaves no dispatch record, because no
        // dispatch happened.
        if (!request.withinPayloadLimit()) {
            return new Outcome(spec.taskId(), requestId, DispatchIntent.State.FAILED,
                    Optional.empty(), "the task's input of " + request.inputBytes()
                            + " bytes exceeds the payload limit of " + policy.maxPayloadBytes()
                            + " bytes; nothing was dispatched", timings);
        }

        // The concurrency cap refuses the work that exceeds it instead of queueing it:
        // an unbounded queue is an unbounded memory and an unbounded latency.
        int flight = inFlight.incrementAndGet();
        if (flight > policy.maxConcurrentDispatches()) {
            inFlight.decrementAndGet();
            return new Outcome(spec.taskId(), requestId, DispatchIntent.State.FAILED,
                    Optional.empty(), "the coordinator is at its concurrency limit of "
                            + policy.maxConcurrentDispatches()
                            + " dispatches; the task was refused", timings);
        }
        try {
            return dispatchAndJudge(spec, request, requestId, now, timings);
        } finally {
            inFlight.decrementAndGet();
        }
    }

    /**
     * The part of {@link #arrange(TaskSpec)} that runs while the dispatch is in flight.
     *
     * @param spec      the task
     * @param request   the request that was built for it
     * @param requestId the request id
     * @param now       the instant the request was issued
     * @param timings   where the stage times go
     * @return what the coordinator did
     */
    private Outcome dispatchAndJudge(TaskSpec spec, CognitiveTaskRequest request,
                                     String requestId, String now, Map<String, Long> timings) {
        long startedAt = System.nanoTime();

        // The intent is written BEFORE the request is sent. That is the whole of the
        // recovery story: after a crash the unfinished dispatch is still here, and the
        // same request id can be sent again — the peer deduplicates it, so the logical
        // effect still happens once.
        store.recordDispatchIntent(new DispatchIntent(requestId, spec.taskId(),
                spec.parentEpisodeId(), workspaceId,
                spec.capabilityId(), request.payloadHash(),
                DispatchIntent.State.DISPATCH_PENDING, null, null, null, null, null, null,
                null, now, now), requestJson(request));
        timings.put("intent_written_nanos", System.nanoTime() - startedAt);

        // The deadline is measured with the injected monotonic clock, not with the wall
        // clock: how long chain A waits is a decision, and a decision must not depend on
        // the time of day.
        long issuedAt = nanoClock.getAsLong();
        long dispatchedAt = System.nanoTime();
        TaskPublisher.Dispatch dispatch = deliver(request, requestId, now);
        timings.put("dispatch_nanos", System.nanoTime() - dispatchedAt);
        if (dispatch.response().isEmpty()) {
            store.recordDispatchAnswer(requestId, DispatchIntent.State.FAILED,
                    null, dispatch.status().name(), null, null, clock.get());
            return new Outcome(spec.taskId(), requestId, DispatchIntent.State.FAILED,
                    Optional.empty(), "the peer did not answer the task: "
                            + dispatch.reason(), timings);
        }
        CognitiveTaskResponse response = dispatch.response().orElseThrow();
        // The peer reports how long its own execution took. That is the one part of the
        // crossing chain A can measure from the other side, so it is recorded next to the
        // crossing itself: the difference between the two is the network, the signing and
        // the peer's own bookkeeping. The peer's clock has millisecond resolution, so the
        // value is a rounded measurement and is reported as such.
        if (response.durationMillis() != null) {
            timings.put("peer_execution_nanos", response.durationMillis() * NANOS_PER_MILLI);
        }
        DispatchIntent.State state = DispatchIntent.State.fromRemote(response.status().name());
        if (state == null) {
            state = DispatchIntent.State.FAILED;
        }

        // A cancellation is an explicit decision and outranks the deadline. A deadline
        // that has passed is a fact about the wait, not about the peer's work: the peer
        // may still have committed its effect, and the answer it sent is still an answer.
        // Neither case deletes what the peer said.
        boolean wasCancelled = cancelled.remove(requestId);
        long elapsedMillis = (nanoClock.getAsLong() - issuedAt) / NANOS_PER_MILLI;
        boolean tooLate = elapsedMillis > request.deadlineMillis();
        if (wasCancelled) {
            store.recordDispatchAnswer(requestId, DispatchIntent.State.CANCELLED,
                    response.responderId(), response.status().name(),
                    responseJson(response), reportJson(dispatch), clock.get());
            return new Outcome(spec.taskId(), requestId, DispatchIntent.State.CANCELLED,
                    Optional.empty(), "the task was cancelled while it was in flight; the "
                            + "peer's answer stays on record and was not judged", timings);
        }
        if (tooLate) {
            store.recordDispatchAnswer(requestId, DispatchIntent.State.TIMED_OUT,
                    response.responderId(), response.status().name(),
                    responseJson(response), reportJson(dispatch), clock.get());
            return new Outcome(spec.taskId(), requestId, DispatchIntent.State.TIMED_OUT,
                    Optional.empty(), "the peer answered " + elapsedMillis
                            + " ms after the deadline of " + request.deadlineMillis()
                            + " ms; the answer stays on record and was not judged", timings);
        }
        store.recordDispatchAnswer(requestId, state,
                response.responderId(), response.status().name(),
                responseJson(response), reportJson(dispatch), clock.get());

        if (response.status() == CognitiveTaskResponse.Status.IN_PROGRESS) {
            // The peer is still working on the task. There is nothing to judge yet, and
            // judging "still running" would close a task that is not closed: the dispatch
            // stays in flight, the peer keeps the result, and the request can be sent
            // again with the same id.
            return new Outcome(spec.taskId(), requestId, DispatchIntent.State.DISPATCHED,
                    Optional.empty(), "the peer is still running the task; its answer is not "
                            + "judged yet and the result stays on the peer's record",
                    timings);
        }

        // The report enters chain A's epistemic model through the governed crossing, and
        // only then is it judged. The order matters: the report is a record of what the
        // peer said, and the verdict is chain A's reading of that record.
        long admittedAt = System.nanoTime();
        admitReport(store.findDispatch(requestId).orElse(null), dispatch.reportEnvelope());
        timings.put("report_admission_nanos", System.nanoTime() - admittedAt);

        long assessedAt = System.nanoTime();
        RemoteReportAssessor.Verdict verdict = assessor.assess(request, response,
                dispatch.report().orElse(null), spec.supplied());
        timings.put("assessment_nanos", System.nanoTime() - assessedAt);
        store.recordDispatchDisposition(requestId, verdict.disposition().name(),
                verdict.reason(), clock.get());
        timings.put("total_nanos", System.nanoTime() - startedAt);
        return new Outcome(spec.taskId(), requestId, DispatchIntent.State.ADJUDICATED,
                Optional.of(verdict), verdict.reason(), timings);
    }

    /**
     * The dispatch of a request, when it is known.
     *
     * @param requestId the request
     * @return the dispatch, or empty when nothing was dispatched under that id
     */
    public Optional<DispatchIntent> findDispatch(String requestId) {
        return store.findDispatch(requestId);
    }

    /**
     * Sends the request of an already-dispatched task again.
     *
     * <p><b>Why this exists.</b> A delivery that was sent but whose answer was lost must
     * be finishable after a restart, and the only way to finish it is to send it again
     * with the same request id. The request id is derived from the task id and the
     * payload from the task's own fields, so the replayed request is byte-for-byte the
     * request that was sent before — the peer's durable inbox recognises it and does
     * not execute the task a second time. That is the whole of the recovery path, and it
     * is why the guarantee is at-least-once delivery with effectively-once logical
     * effects and not distributed exactly-once execution.</p>
     *
     * @param spec the task, with the same fields the first dispatch had
     * @return what the peer answered, or empty when the task was never dispatched
     */
    public Optional<Outcome> replay(TaskSpec spec) {
        String requestId = "req-" + spec.taskId();
        boolean dispatched = store.findDispatch(requestId).isPresent();
        if (!dispatched) {
            return Optional.empty();
        }
        String now = clock.get();
        CognitiveTaskRequest request = request(spec, requestId, now);
        return Optional.of(resend(request, requestId, spec.supplied()));
    }

    /**
     * The canonical request, as it is written with the intent.
     *
     * <p>The request is stored so that a reconciliation pass can send the very same
     * request again after a crash; the request id is the identity of the attempt, not
     * its content.</p>
     *
     * @param request the request
     * @return the canonical request, or null when it cannot be encoded
     */
    private String requestJson(CognitiveTaskRequest request) {
        try {
            return new BridgePayloadCodec().encode(
                    BridgePayload.ofTaskRequest(request, request.provenance()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Sends the request of an unfinished dispatch again, after a restart.
     *
     * <p><b>What this is.</b> The recovery path of a delivery whose answer was lost.
     * The request comes from the durable intent, so it is byte-for-byte the request that
     * was sent before, with the same request id — and the peer's durable inbox
     * recognises it, so the logical effect still happens once. This is at-least-once
     * delivery with effectively-once logical effects, and it is deliberately not a
     * distributed exactly-once claim.</p>
     *
     * @param requestId the request to send again
     * @return what the peer answered, or empty when there is nothing to replay
     */
    public Optional<Outcome> replayByRequestId(String requestId) {
        Optional<String> stored = store.requestJson(requestId);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        CognitiveTaskRequest request;
        try {
            request = new BridgePayloadCodec().decode(stored.get()).taskRequest();
        } catch (RuntimeException e) {
            return Optional.of(new Outcome("", requestId, DispatchIntent.State.FAILED,
                    Optional.empty(), "the stored request cannot be read: " + e.getMessage()));
        }
        return Optional.of(resend(request, requestId, suppliedOf(request)));
    }

    /**
     * The records chain A supplied to the peer, as they travel in the request.
     *
     * @param request the request
     * @return the records, which may be empty
     */
    private static List<String> suppliedOf(CognitiveTaskRequest request) {
        String supplied = request.context().get(SUPPLIED_KEY);
        if (supplied == null || supplied.isBlank()) {
            return List.of();
        }
        return List.of(supplied.split(","));
    }

    /**
     * Sends a request again and records what came back.
     *
     * @param request   the request to send
     * @param requestId the request id
     * @param supplied  the records chain A supplied to the peer earlier
     * @return what the coordinator concluded
     */
    private Outcome resend(CognitiveTaskRequest request, String requestId,
                           List<String> supplied) {
        // The replay is a path of its own and is measured as one: the durable
        // idempotency path has its own cost, and the mandate asks for it to be told
        // apart from the first execution. The keys carry the "replay_" prefix so the
        // two paths can never be confused in a report.
        Map<String, Long> timings = new LinkedHashMap<>();
        long startedAt = System.nanoTime();
        int budget = Math.max(0, policy.maxRetries());
        if (store.attempts(requestId) > budget) {
            // The budget was already spent on this task. A restart does not buy a new
            // one: the peer was dialled a bounded number of times in total.
            return new Outcome(request.taskId(), requestId, DispatchIntent.State.FAILED,
                    Optional.empty(), "the retry budget of " + (budget + 1)
                            + " attempt(s) is spent, so the request is not sent again");
        }
        store.recordDispatchAttempt(requestId, clock.get());
        String now = clock.get();
        long dispatchedAt = System.nanoTime();
        TaskPublisher.Dispatch dispatch = transport.dispatch(request, now);
        timings.put("replay_dispatch_nanos", System.nanoTime() - dispatchedAt);
        if (dispatch.response().isEmpty()) {
            store.recordDispatchAnswer(requestId, DispatchIntent.State.FAILED,
                    null, dispatch.status().name(), null, null, clock.get());
            return new Outcome(request.taskId(), requestId, DispatchIntent.State.FAILED,
                    Optional.empty(), "the peer did not answer the replayed request: "
                            + dispatch.reason());
        }
        CognitiveTaskResponse response = dispatch.response().orElseThrow();
        DispatchIntent.State state = DispatchIntent.State.fromRemote(response.status().name());
        if (state == null) {
            state = DispatchIntent.State.FAILED;
        }
        store.recordDispatchAnswer(requestId, state, response.responderId(),
                response.status().name(), responseJson(response), reportJson(dispatch),
                clock.get());
        if (response.status() == CognitiveTaskResponse.Status.IN_PROGRESS) {
            // The peer is still working on the task. There is nothing to judge yet, and
            // judging "still running" would close a task that is not closed — the same
            // rule the first dispatch follows.
            return new Outcome(request.taskId(), requestId, DispatchIntent.State.DISPATCHED,
                    Optional.empty(), "the peer is still running the task; its answer is not "
                            + "judged yet and the result stays on the peer's record");
        }
        long admittedAt = System.nanoTime();
        admitReport(store.findDispatch(requestId).orElse(null), dispatch.reportEnvelope());
        timings.put("replay_report_admission_nanos", System.nanoTime() - admittedAt);
        long assessedAt = System.nanoTime();
        RemoteReportAssessor.Verdict verdict = assessor.assess(request, response,
                dispatch.report().orElse(null), supplied);
        timings.put("replay_assessment_nanos", System.nanoTime() - assessedAt);
        store.recordDispatchDisposition(requestId, verdict.disposition().name(),
                verdict.reason(), clock.get());
        timings.put("replay_total_nanos", System.nanoTime() - startedAt);
        return new Outcome(request.taskId(), requestId, DispatchIntent.State.ADJUDICATED,
                Optional.of(verdict), verdict.reason(), timings);
    }

    /**
     * Delivers the request, and spends the retry budget when the peer does not answer.
     *
     * <p><b>Why the retry is safe.</b> Every attempt sends the <i>same</i> request with
     * the same request id, so the peer's durable inbox recognises the later attempt and
     * does not execute the task a second time. That is at-least-once delivery with
     * effectively-once logical effects — it is deliberately not a distributed
     * exactly-once claim.</p>
     *
     * <p><b>Why the budget is durable.</b> The count lives in the dispatch's own row,
     * next to the request it belongs to, so a restart does not hand the task a fresh
     * budget: a peer that stays down is dialled a bounded number of times in total, not
     * a bounded number of times per process life.</p>
     *
     * <p><b>What is not caught.</b> Only a delivery failure is a delivery failure. Any
     * other exception is a defect in this process, and it is allowed to escape: the
     * dispatch then stays unfinished, and the reconciliation pass after the restart is
     * what finishes it.</p>
     *
     * @param request   the request
     * @param requestId the request id
     * @param now       the instant of the attempt
     * @return what the peer answered, or an empty dispatch when the budget is spent
     */
    private TaskPublisher.Dispatch deliver(CognitiveTaskRequest request, String requestId,
                                           String now) {
        int budget = Math.max(0, policy.maxRetries());
        TaskPublisher.Dispatch last = TaskPublisher.Dispatch.empty(requestId,
                BridgeDecision.Status.REFUSED, "the peer did not answer the task");
        for (int attempt = 0; attempt <= budget; attempt++) {
            store.recordDispatchAttempt(requestId, clock.get());
            try {
                last = transport.dispatch(request, now);
                if (last.response().isPresent()
                        && last.response().get().status()
                                != CognitiveTaskResponse.Status.IN_PROGRESS) {
                    return last;
                }
                // A peer that says the task is still running has answered: the caller
                // waits within its budget instead of spending the attempt as if the peer
                // had said nothing.
            } catch (TaskDispatchException e) {
                last = TaskPublisher.Dispatch.empty(requestId, BridgeDecision.Status.REFUSED,
                        "the delivery failed: " + e.getMessage());
            }
        }
        if (last.response().isPresent()
                && last.response().get().status() == CognitiveTaskResponse.Status.IN_PROGRESS) {
            // The peer is working on the task and said so. The caller's budget is spent,
            // but the task is not lost: the peer keeps the result, and the dispatch can
            // be sent again with the same request id.
            return last;
        }
        return TaskPublisher.Dispatch.empty(requestId, BridgeDecision.Status.REFUSED,
                "the peer did not answer the task and the retry budget of " + (budget + 1)
                        + " attempt(s) is spent: " + last.reason());
    }

    /**
     * Where the peer's report goes.
     *
     * <p>The report is a signed message from another process, and the only place it may
     * enter chain A's epistemic model is the governed bridge receiver — the same crossing
     * that admits chain B's knowledge messages. This port is that receiver, named so the
     * coordinator does not depend on the concrete service.</p>
     */
    @FunctionalInterface
    public interface ReportSink {

        /**
         * Admits a signed report into chain A's epistemic model.
         *
         * @param envelope the report's signed envelope
         * @return what the receiver decided
         */
        io.matrix.contract.bridge.BridgeDecision accept(
                io.matrix.contract.bridge.BridgeEnvelope envelope);
    }

    /**
     * Cancels a dispatch that is in flight.
     *
     * <p><b>What a cancellation is and is not.</b> It is chain A's decision to stop
     * waiting for the peer; it is not an undo. The peer may already have committed its
     * effect, and the answer it sent stays on record in the dispatch row — what the
     * cancellation removes is chain A's <i>judgement</i> of that answer, because the task
     * was cancelled and a cancelled task has no verdict.</p>
     *
     * @param requestId the request to cancel
     * @return true when a dispatched task was found and marked as cancelled
     */
    public boolean cancel(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return false;
        }
        DispatchIntent dispatch = store.findDispatch(requestId).orElse(null);
        if (dispatch == null) {
            return false;
        }
        cancelled.add(requestId);
        store.markDispatchState(requestId, DispatchIntent.State.CANCELLED, clock.get());
        // The peer is told too, when the transport can carry the message. A peer that is
        // not told keeps working on the task: the cancellation withdraws chain A's
        // waiting, not the peer's work.
        transport.cancel(requestId, "the task was cancelled by the requester",
                clock.get());
        return true;
    }

    /**
     * The structured answer, as it is written into the dispatch row.
     *
     * <p>The trace is best effort and the decision is not: a payload that cannot be
     * encoded leaves the trace field empty rather than breaking the dispatch.</p>
     *
     * @param response the peer's answer
     * @return the encoded answer, or null when it cannot be encoded
     */
    private String responseJson(CognitiveTaskResponse response) {
        try {
            return new BridgePayloadCodec().encode(BridgePayload.ofTaskResponse(response,
                    Provenance.of(response.responderId(), clock.get())));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The signed report envelope, as it is written into the dispatch row.
     *
     * @param dispatch what the peer sent back
     * @return the encoded envelope, or null when there was none
     */
    private String reportJson(TaskPublisher.Dispatch dispatch) {
        try {
            return dispatch.reportEnvelope()
                    .map(envelope -> new BridgeEnvelopeCodec().encode(envelope))
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The bounded request for a task.
     *
     * <p><b>Why the expectation travels with the request.</b> The research track on
     * cybernetics found the gap: a request that carries no criterion leaves the answer a
     * report that nobody judged. The coordinator therefore says what it expects before
     * it sends, and the assessor compares the answer against it.</p>
     *
     * @param spec      the task
     * @param requestId the request id
     * @param now       the instant
     * @return the request
     */
    private CognitiveTaskRequest request(TaskSpec spec, String requestId, String now) {
        Map<String, String> context = Map.of(
                EXPECTATION_KEY, spec.expectation() == null ? "" : spec.expectation(),
                SUPPLIED_KEY, String.join(",", spec.supplied() == null
                        ? List.<String>of() : spec.supplied()));
        return new CognitiveTaskRequest(
                BridgeProtocol.VERSION,
                spec.taskId(),
                requestId,
                // The correlation names the episode the task belongs to, when it belongs
                // to one. The episode is the same id the kernel's trace carries, so the
                // trace and the dispatch row name each other.
                new Correlation("corr-" + spec.taskId(), spec.parentEpisodeId(),
                        spec.parentEpisodeId(), requestId),
                spec.parentEpisodeId() == null ? "" : spec.parentEpisodeId(),
                senderId,
                spec.capabilityId(),
                "v1",
                CognitiveTaskRequest.Kind.COGNITIVE_INFERENCE,
                spec.input(),
                context,
                List.of("one remote hop"),
                "cognitive-task-response/v1",
                spec.budgetMillis() > 0L ? spec.budgetMillis() : policy.defaultBudgetMillis(),
                spec.deadlineMillis() > 0L ? spec.deadlineMillis()
                        : policy.defaultDeadlineMillis(),
                policy.maxPayloadBytes(),
                CognitiveTaskRequest.MAX_REMOTE_HOPS,
                Provenance.of(senderId, now),
                "hash-" + requestId,
                null);
    }
}
