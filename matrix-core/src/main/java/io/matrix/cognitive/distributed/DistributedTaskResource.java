package io.matrix.cognitive.distributed;

import io.matrix.bridge.TaskPublisher;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.DispatchIntent;
import io.matrix.cognitive.RemoteReportAssessor;
import io.matrix.contract.bridge.HmacBridgeSigner;
import io.matrix.bridge.BridgeResource;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * W53 — chain A's entry point for a task that the peer must work on.
 *
 * <p><b>What it does.</b> It receives one cognitive task, arranges it through the
 * coordinator — which writes its intent down, sends a bounded request over the bridge,
 * checks the answer and evaluates it against chain A's own evidence — and returns the
 * outcome. It also answers a query for the task's durable trace, because a task that
 * cannot be inspected is a task whose history cannot be reconstructed after a
 * restart.</p>
 *
 * <p><b>What it does not do.</b> It does not answer the task itself. The answer comes
 * from the peer's cognitive runtime, and the disposition is chain A's own judgement of
 * that answer; this resource only moves the request and the verdict.</p>
 *
 * <p><b>Where the configuration comes from.</b> The peer's address and the shared
 * secret come from the environment or the system properties, exactly as the rest of the
 * bridge does. There is no default secret: when none is configured, the resource says
 * so and refuses, rather than sending an unauthenticated message.</p>
 */
@jakarta.ws.rs.Path("/v1/cognitive")
@Produces(MediaType.APPLICATION_JSON)
@jakarta.inject.Singleton
public class DistributedTaskResource {

    /** The logger of the resource. */
    private static final Logger LOG = LoggerFactory.getLogger(DistributedTaskResource.class);

    /** The environment variable that carries the peer's address. */
    public static final String PEER_URL_ENV = "MATRIX_PEER_URL";

    /** The system property that carries the peer's address. */
    public static final String PEER_URL_PROPERTY = "matrix.peer.url";

    /** The environment variable that carries the shared secret. */
    public static final String SECRET_ENV = "MATRIX_BRIDGE_SECRET";

    /** The system property that carries the shared secret. */
    public static final String SECRET_PROPERTY = "matrix.bridge.secret";

    /** The workspace the dispatched tasks belong to. */
    private static final String WORKSPACE = "bridged";

    /** The identity chain A claims on the bridge. */
    private static final String SENDER_ID = "chain-a-epistemic-core";

    /**
     * Chain A's own bridge receiver.
     *
     * <p>The peer's report enters the epistemic model through it — the same crossing that
     * admits chain B's knowledge messages, with the same authentication, the same rights
     * and the same audit. There is no second way in.</p>
     */
    @Inject
    BridgeResource bridge;

    /**
     * Chain A's cognitive store — opened once and shared by every request of this process.
     *
     * <p><b>Why once.</b> The store is chain A's <i>single writer</i> for the cognitive
     * database. A resource that opened one per request would be a second writer on the same
     * file, and two writers on one SQLite file do not merely slow each other down: they
     * contend for the write lock, and the loser is reported as a constraint violation —
     * a request that was merely concurrent with another would look like a task whose
     * request id was already dispatched. The store is therefore opened lazily, once, and
     * kept for the life of the process.</p>
     */
    private CognitiveStore store;

    /**
     * The timer of the reconciliation pass.
     *
     * <p>VOLATILE: the startup observer writes it and the shutdown observer reads it, and
     * they are not the same thread. A timer that is not seen is a timer that is never
     * closed, and a closed timer that is not seen is a timer that is never closed either
     * way round.</p>
     */
    private volatile DispatchReconcilerTimer reconcilerTimer;

    /**
     * The coordinator of this process.
     *
     * <p><b>Why one and not one per request.</b> The coordinator holds the bounds it
     * keeps — how many dispatches may be in flight, which tasks were cancelled — and a
     * coordinator per request would make those bounds per request: the concurrency cap
     * would then never be reached, because every request would count itself as the
     * only one in flight. The coordinator is therefore built once and kept, exactly like
     * the store, which is this process's single writer.</p>
     */
    private CognitiveCoordinator coordinator;

    private final TaskPublisher publisher;
    private final RemoteReportAssessor assessor;

    /**
     * Chain A's own knowledge.
     *
     * <p><b>Why the adjudicator needs it.</b> A remote report is judged against what
     * this process already knows: does chain A's own knowledge support the proposition,
     * does it contradict it, and is the report stating the very proposition chain A
     * supplied to the peer? Without this, the adjudicator's local evidence was empty —
     * every count was zero — and the answer was always "unresolved" for want of a
     * reading rather than for want of evidence. The workbench is the CDI bean, so it is
     * the <i>same</i> store the bridge writes its reports into: one writer, one file.</p>
     */
    @Inject
    io.matrix.knowledge.KnowledgeWorkbench workbench;

    /**
     * Chain A's own service — the holder of the profile of measured methods.
     *
     * <p><b>Why the resource needs it.</b> The coordinator judges the peer's answer, and what
     * it concluded has to go somewhere a later process can read: the durable profile of
     * methods, in the stratum the verdict was observed in. The profile belongs to the service
     * that keeps it for the whole process, and the resource asks that one for it — two
     * profiles over one store would disagree until a restart, and a profile nobody can read
     * back is not a profile. The field is null when the resource is built by hand in a test,
     * and a coordinator without a sink simply keeps no profile of the peer.</p>
     */
    @Inject
    io.matrix.cognitive.CognitiveService service;

    /**
     * The resource, built from the environment.
     *
     * @throws java.sql.SQLException when chain A's store cannot be opened
     */
    public DistributedTaskResource() {
        this(null, publisherFromEnvironment(), new RemoteReportAssessor(
                RemoteReportAssessor.LocalEvidence.NONE));
    }

    /**
     * The resource.
     *
     * @param store     chain A's durable store
     * @param publisher the transport the request travels over
     * @param assessor  what judges the peer's answer
     */
    public DistributedTaskResource(CognitiveStore store, TaskPublisher publisher,
                                   RemoteReportAssessor assessor) {
        this.store = store;
        this.publisher = publisher;
        this.assessor = assessor;
    }

    /**
     * The publisher, when the environment configures one.
     *
     * @return the publisher, or null when no secret or no peer address is configured
     */
    private static TaskPublisher publisherFromEnvironment() {
        String url = System.getProperty(PEER_URL_PROPERTY);
        if (url == null || url.isBlank()) {
            url = System.getenv(PEER_URL_ENV);
        }
        String secret = System.getProperty(SECRET_PROPERTY);
        if (secret == null || secret.isBlank()) {
            secret = System.getenv(SECRET_ENV);
        }
        if (url == null || url.isBlank() || secret == null || secret.isBlank()) {
            return null;
        }
        return new TaskPublisher(url, new HmacBridgeSigner(secret.getBytes(StandardCharsets.UTF_8)),
                SENDER_ID);
    }

    /** The system property that names the file chain A's cognitive store lives in. */
    public static final String COGNITIVE_PATH_PROPERTY = "matrix.cognitive.path";

    /** The environment variable that names the file chain A's cognitive store lives in. */
    public static final String COGNITIVE_PATH_ENV = "MATRIX_COGNITIVE_PATH";

    /** The file chain A's cognitive store lives in when nothing names another one. */
    public static final String DEFAULT_COGNITIVE_PATH = "data/cognitive/cognitive.sqlite";

    /**
     * Chain A's cognitive store, opened on the first use and kept afterwards.
     *
     * @return the one store of this process
     */
    private synchronized CognitiveStore store() {
        if (store == null) {
            java.nio.file.Path cognitive = cognitiveStorePath();
            refuseTheKnowledgeFile(cognitive);
            store = new CognitiveStore(cognitive);
        }
        return store;
    }

    /**
     * Refuses a configuration that would put two writers on one database.
     *
     * <p><b>Why this is a refusal and not a warning.</b> The dispatches live in their own
     * file, and the service's cognitive tables live in the knowledge file. A configuration
     * that pointed both at one file would give this process two writers on one SQLite
     * database — the exact thing the wave's boundary forbids — and the writer lock would
     * not catch it, because both holders are in this process and merely join the lock. The
     * failure would show up later as a lock contention error on an unrelated request, so
     * the configuration is refused at the moment the store is opened.</p>
     *
     * @param cognitive the file the dispatch store is about to be opened on
     */
    private void refuseTheKnowledgeFile(java.nio.file.Path cognitive) {
        if (service == null) {
            return;
        }
        java.nio.file.Path knowledge = service.storePath();
        if (knowledge.toAbsolutePath().normalize()
                .equals(cognitive.toAbsolutePath().normalize())) {
            throw new IllegalStateException("the dispatch store must not live in the file of "
                    + "the knowledge store (" + knowledge.toAbsolutePath().normalize()
                    + "): that would give this process two writers on one database");
        }
    }

    /**
     * The file chain A's cognitive store lives in.
     *
     * <p><b>W53 — the path is a configuration value, not a constant in the code.</b> The
     * store used to be opened at a hard-coded relative path, so every process that ran the
     * application wrote into the same file in the working directory. A benchmark, a test
     * or a second instance could not be given a store of its own, and the measurement of
     * the distributed path was taken against a database that grew with every run. The
     * property {@link #COGNITIVE_PATH_PROPERTY} names the file and follows the same shape
     * as the knowledge store's own {@code matrix.knowledge.path}: the default is the
     * working directory's file, and a caller that must not touch it names another one.</p>
     *
     * @return the path of the store's file
     */
    static java.nio.file.Path cognitiveStorePath() {
        String configured = System.getProperty(COGNITIVE_PATH_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(COGNITIVE_PATH_ENV);
        }
        if (configured == null || configured.isBlank()) {
            configured = DEFAULT_COGNITIVE_PATH;
        }
        return java.nio.file.Path.of(configured);
    }

    /**
     * The coordinator this resource arranges tasks with.
     *
     * <p>When the bridge is present, the peer's report is handed to it, so the report
     * becomes a record in chain A's epistemic model — a candidate with its origin attached
     * — and the verdict is chain A's own reading of that record.</p>
     *
     * @return the coordinator
     */
    synchronized CognitiveCoordinator coordinator() {
        if (coordinator == null) {
            coordinator = new CognitiveCoordinator(store(), publisher::dispatch,
                    adjudicator(), WORKSPACE, SENDER_ID,
                    () -> java.time.Instant.now().toString());
            if (bridge != null) {
                coordinator = coordinator.withReportSink(
                        envelope -> bridge.service().accept(envelope));
            }
            if (service != null) {
                // W53: what chain A concluded about the peer's answer is recorded in the
                // profile of methods, in the stratum of the task family and the capability
                // version that ran. The profile is the ledger this process already keeps
                // for its own methods — one ledger, one store, one writer — so the peer's
                // verdicts and the local ones live in the same place and are read the same
                // way.
                coordinator = coordinator.withVerdictSink(
                        new io.matrix.cognitive.LedgerVerdictSink(service.ledger()));
            }
        }
        return coordinator;
    }

    /**
     * What judges the peer's answer.
     *
     * <p>When chain A's knowledge is reachable, the adjudicator reads it: the report is
     * weighed against the claims and the evidence this process already holds. When it is
     * not — the tests that build the resource by hand — the adjudicator falls back to the
     * evidence it was given, and says nothing about a knowledge it cannot see.</p>
     *
     * @return the adjudicator
     */
    private RemoteReportAssessor adjudicator() {
        if (workbench == null || bridge == null) {
            return assessor;
        }
        return new RemoteReportAssessor(new io.matrix.cognitive.KnowledgeStoreEvidence(
                workbench.storeForValidation(), bridge.service().workspaceId()));
    }

    /**
     * Finishes the dispatches a crash left unfinished.
     *
     * <p><b>Why this runs at startup.</b> A dispatch whose answer was lost is on record
     * with its request, and the only way to finish it is to send that request again. The
     * peer recognises the request id, so the logical effect still happens once. The pass
     * runs when the process starts — that is when the unfinished work exists — and it
     * reports what it did, so a reader can see that the recovery happened rather than
     * assuming it.</p>
     *
     * @param event the startup of this process
     */
    void onStartup(@jakarta.enterprise.event.Observes StartupEvent event) {
        if (publisher == null) {
            return;
        }
        try {
            DispatchReconciler.Summary summary = new DispatchReconciler(store(), coordinator(),
                    WORKSPACE).reconcile();
            if (summary.examined() > 0) {
                LOG.info("the reconciliation pass examined {} unfinished dispatch(es), "
                        + "replayed {}, finished {}", summary.examined(), summary.replayed(),
                        summary.finished());
                summary.reasons().forEach(reason -> LOG.warn("the reconciliation pass: {}", reason));
            }
        } catch (RuntimeException e) {
            LOG.warn("the reconciliation pass could not run: {}", e.getMessage());
        }
        // The pass runs on a timer as well: the startup pass covers the crash, and the
        // timer covers the answer that is lost while the process stays up.
        reconcilerTimer = new DispatchReconcilerTimer(new DispatchReconciler(store(),
                coordinator(), WORKSPACE));
        reconcilerTimer.start();
    }

    /**
     * Stops the reconciliation timer when the process goes down.
     *
     * @param event the shutdown of this process
     */
    void onShutdown(@jakarta.enterprise.event.Observes ShutdownEvent event) {
        DispatchReconcilerTimer timer = reconcilerTimer;
        if (timer != null) {
            timer.close();
            reconcilerTimer = null;
        }
    }

    /**
     * Arranges one cognitive task on the peer.
     *
     * @param payload the task: the task id, the capability, the input and what chain A
     *                expects
     * @return the outcome of the task
     */
    @POST
    @jakarta.ws.rs.Path("/task")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response arrange(Map<String, Object> payload) {
        if (publisher == null) {
            return Response.status(503).entity(Map.of(
                    "error", "the peer is not configured; set " + PEER_URL_PROPERTY + " and "
                            + SECRET_PROPERTY + " before dispatching a remote task")).build();
        }
        String taskId = text(payload, "taskId");
        String capabilityId = text(payload, "capabilityId");
        String input = text(payload, "input");
        if (taskId == null || capabilityId == null || input == null) {
            return Response.status(400).entity(Map.of(
                    "error", "taskId, capabilityId and input are required")).build();
        }
        String expectation = text(payload, "expectation");
        List<String> supplied = new ArrayList<>();
        Object raw = payload.get("supplied");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    supplied.add(String.valueOf(item));
                }
            }
        }
        long budget = number(payload, "budgetMillis",
                io.matrix.contract.bridge.CognitiveTaskRequest.DEFAULT_BUDGET_MILLIS);
        long deadline = number(payload, "deadlineMillis",
                io.matrix.contract.bridge.CognitiveTaskRequest.DEFAULT_DEADLINE_MILLIS);

        CognitiveCoordinator.Outcome outcome;
        try {
            outcome = coordinator().arrange(
                    new CognitiveCoordinator.TaskSpec(taskId, capabilityId, input, expectation,
                            budget, deadline, supplied));
        } catch (io.matrix.cognitive.CognitiveConflictException e) {
            // The same request id under a different payload is a conflict, and a conflict
            // is the caller's error, not a failure of this process: the answer says so,
            // and the first dispatch's record stays untouched.
            return Response.status(409).entity(Map.of(
                    "error", e.getMessage(),
                    "taskId", taskId,
                    "requestId", "req-" + taskId,
                    "conflict", e.kind().name())).build();
        }

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("taskId", outcome.taskId());
        answer.put("requestId", outcome.requestId());
        answer.put("state", outcome.state().name());
        answer.put("disposition", outcome.verdict()
                .map(v -> v.disposition().name()).orElse("NONE"));
        answer.put("reason", outcome.reason());
        answer.put("reportDisposition", reportDisposition(outcome.requestId()));
        answer.put("timingsNanos", outcome.timingsNanos());
        return Response.ok(answer).build();
    }

    /**
     * Sends the request of a dispatched task again — the reconciliation path.
     *
     * <p>The peer recognises the request id and answers with the result the first
     * delivery produced, without executing the task again. A reader of the answer can
     * therefore see whether the peer treated it as a duplicate.</p>
     *
     * @param payload the task, with the same fields the first dispatch had
     * @return what the peer answered to the replay
     */
    @POST
    @jakarta.ws.rs.Path("/task/replay")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response replay(Map<String, Object> payload) {
        if (publisher == null) {
            return Response.status(503).entity(Map.of(
                    "error", "the peer is not configured")).build();
        }
        String taskId = text(payload, "taskId");
        String capabilityId = text(payload, "capabilityId");
        String input = text(payload, "input");
        if (taskId == null || capabilityId == null || input == null) {
            return Response.status(400).entity(Map.of(
                    "error", "taskId, capabilityId and input are required")).build();
        }
        Optional<CognitiveCoordinator.Outcome> outcome = coordinator().replay(
                new CognitiveCoordinator.TaskSpec(taskId, capabilityId, input,
                        text(payload, "expectation"),
                        number(payload, "budgetMillis",
                                io.matrix.contract.bridge.CognitiveTaskRequest
                                        .DEFAULT_BUDGET_MILLIS),
                        number(payload, "deadlineMillis",
                                io.matrix.contract.bridge.CognitiveTaskRequest
                                        .DEFAULT_DEADLINE_MILLIS),
                        List.of()));
        if (outcome.isEmpty()) {
            return Response.status(404).entity(Map.of(
                    "error", "no dispatched task with that id")).build();
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("taskId", outcome.get().taskId());
        answer.put("requestId", outcome.get().requestId());
        answer.put("state", outcome.get().state().name());
        answer.put("disposition", outcome.get().verdict()
                .map(v -> v.disposition().name()).orElse("NONE"));
        answer.put("reason", outcome.get().reason());
        answer.put("reportDisposition", reportDisposition(outcome.get().requestId()));
        // The replay carries its own measurements: the cost of the durable idempotency
        // path is a number of its own and must not be read as the cost of the first
        // execution.
        answer.put("timingsNanos", outcome.get().timingsNanos());
        return Response.ok(answer).build();
    }

    /**
     * What chain A did with the peer's report.
     *
     * @param requestId the request
     * @return the admission, or null when there was no report
     */
    private String reportDisposition(String requestId) {
        return store().findDispatch(requestId)
                .map(dispatch -> dispatch.reportDisposition())
                .orElse(null);
    }

    /**
     * The durable trace of a dispatched task.
     *
     * @param taskId the task
     * @return what chain A recorded about it
     */
    @GET
    @jakarta.ws.rs.Path("/task/{taskId}")
    public Response trace(@PathParam("taskId") String taskId) {
        List<DispatchIntent> found = store().dispatches(WORKSPACE).stream()
                .filter(dispatch -> taskId.equals(dispatch.taskId()))
                .toList();
        if (found.isEmpty()) {
            return Response.status(404).entity(Map.of(
                    "error", "no dispatched task with that id")).build();
        }
        List<Map<String, Object>> traces = new ArrayList<>();
        for (DispatchIntent dispatch : found) {
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("taskId", dispatch.taskId());
            trace.put("requestId", dispatch.requestId());
            trace.put("state", dispatch.state().name());
            trace.put("capabilityId", dispatch.capabilityId());
            trace.put("remoteResponder", dispatch.remoteResponder());
            trace.put("remoteStatus", dispatch.remoteStatus());
            trace.put("disposition", dispatch.disposition());
            trace.put("dispositionReason", dispatch.dispositionReason());
            trace.put("reportDisposition", dispatch.reportDisposition());
            trace.put("createdAt", dispatch.createdAt());
            trace.put("updatedAt", dispatch.updatedAt());
            traces.add(trace);
        }
        return Response.ok(Map.of("taskId", taskId, "dispatches", traces)).build();
    }

    /** A text field of the payload, or null. */
    private static String text(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    /** A numeric field of the payload, or the default. */
    private static long number(Map<String, Object> payload, String field, long fallback) {
        Object value = payload.get(field);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    /** The optional peer address, for the status route. */
    Optional<String> peerUrl() {
        String url = System.getProperty(PEER_URL_PROPERTY);
        if (url == null || url.isBlank()) {
            url = System.getenv(PEER_URL_ENV);
        }
        return Optional.ofNullable(url);
    }
}
