package io.matrix.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.actions.ArtifactAction;
import io.matrix.actions.ArtifactActionService;
import io.matrix.actions.ArtifactWriter;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.algorithms.AlgorithmFamilies;
import io.matrix.cognitive.algorithms.AlgorithmRefusalException;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.algorithms.ProblemRequest;
import io.matrix.cognitive.report.ArtifactObserver;
import io.matrix.cognitive.report.ReportJourney;
import io.matrix.cognitive.report.ReportJourneyOutcome;
import io.matrix.cognitive.report.WorkspaceFingerprint;
import io.matrix.cognitive.report.ReportJourneyMethod;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.cognitive.report.ReportJourneyTrace;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CONCEPT-TO-ACTION — the one product route that turns a question into a real file.
 *
 * <p><b>What this route is.</b> It asks a workspace a question through the workbench's own
 * retrieval, assembles a report from the answer the workspace gave, and hands that report to
 * the project's existing bounded file action ({@code report.artifact.write}). The bytes come
 * from the workspace's own claims and sources; the route never accepts content, a path, a
 * command or a tool name from the caller.</p>
 *
 * <p><b>What the route is not.</b> It is not a general action interface. There is one
 * action, one destination root and one consent. The root is the server's configuration; the
 * action identity is the server's derivation; the only thing the caller chooses is the
 * question, the name it would like the report to have, and whether it consents.</p>
 *
 * <p><b>What the answer means.</b> The response keeps apart five statements that are easy to
 * confuse: the action executed; the artifact exists and carries the digest that was
 * measured; the answer is supported by the workspace's evidence; a proposition is unresolved;
 * and the report may have come from an earlier request. A report is a <i>record</i> of what
 * the workspace said — it is not evidence for its own contents, and the response says so.</p>
 */
@Path("/v1/workbench/workspaces/{id}/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ArtifactReportResource {

    /**
     * The workbench the route asks.
     *
     * <p>The field is public so that the route can be driven from a test outside this
     * package, the way the container drives it. The container injects it either way.
     */
    @Inject
    public KnowledgeWorkbench workbench;

    /**
     * The bounded action the route hands the report to.
     *
     * <p>The field is public for the same reason as {@link #workbench}.
     */
    @Inject
    public ArtifactActionService actions;

    /**
     * The kernel's plan for the journey.
     *
     * <p>P2 — the route does not decide how the report is produced. It asks the kernel's
     * planner for a method and a reason, and it stops when the kernel has no method for
     * the request. The planner is public for the same reason as {@link #workbench}: the
     * route is driven from a test the way the container drives it.</p>
     */
    @Inject
    public ReportJourneyPlanner planner;

    /**
     * The kernel's service, whose store holds the journey's trace.
     *
     * <p>The trace is written through the kernel's own durable mechanism — the events of
     * one episode in the cognitive store, which is the store the kernel writes its own
     * cycles into. There is no new database and no new file system authority.</p>
     */
    @Inject
    public CognitiveService cognitive;

    /**
     * Whether the journey consults the trace it left behind.
     *
     * <p>This is the seam the design's no-learning baseline is measured through: the same
     * journey, with the same trace written, but with the retained journey not read back.
     * The comparison against it is what decides whether consulting the trace is worth
     * anything — and the answer has to be measured, not assumed.</p>
     */
    public boolean consultRetainedTrace = true;

    /** The JSON the response is written in, and the retained response is kept in. */
    private static final ObjectMapper JSON = new ObjectMapper();


    /** The longest report name the route accepts. */
    private static final int MAX_REPORT_NAME_LENGTH = 128;

    /** The status the route answers when the root's own budget is exhausted. */
    private static final int HTTP_INSUFFICIENT_STORAGE = 507;

    /** The body of a report request. */
    public record ReportRequest(String question, String reportName, Boolean consent,
                                String idempotencyKey, String level) {
    }

    /** One cited piece of evidence. */
    public record EvidenceReference(String claimId, String proposition, String status,
                                    String sourceId, String sourceTitle) {
    }

    /**
     * The epistemic status of the question's premise, as the workspace made of it.
     *
     * <p>It is a field of its own, and it is separate from the disposition of the ACTION, from
     * the epistemic status of the ANSWER, from the limitations and from the refusal reasons:
     * a caller that wants to know what the workspace made of the premise reads this, and a
     * caller that wants to know whether the answer was given reads the disposition.
     */
    public record PremiseStatus(String status, String kind, String premise, String reason,
                                 List<String> refutingClaimIds, List<String> refutingSourceIds) {

        public PremiseStatus {
            refutingClaimIds = List.copyOf(refutingClaimIds == null ? List.of() : refutingClaimIds);
            refutingSourceIds =
                    List.copyOf(refutingSourceIds == null ? List.of() : refutingSourceIds);
        }
    }

    /** One side of a conflict the corpus holds, with its provenance. */
    public record ConflictSide(String claimId, String proposition, String sourceId,
                                String sourceTitle, String sourceLocator, String detail) {
    }

    /** A conflict the corpus holds: both sides, and the system's refusal to resolve it. */
    public record ConflictReport(String reason, List<ConflictSide> sides) {

        public ConflictReport {
            sides = List.copyOf(sides == null ? List.of() : sides);
        }
    }

    /** One reading of a question that admits several, with its provenance. */
    public record ReadingReport(String reading, List<EvidenceReference> evidence) {

        public ReadingReport {
            evidence = List.copyOf(evidence == null ? List.of() : evidence);
        }
    }

    /** The outcome of a report request. */
    public record ReportResponse(String taskId,
                                 String actionName,
                                 int actionVersion,
                                 String disposition,
                                 String artifactName,
                                 String artifactLocator,
                                 String sha256,
                                 List<EvidenceReference> evidence,
                                 String epistemicDisposition,
                                 String traceReference,
                                 List<String> limitations,
                                 List<String> refusalReasons,
                                 PremiseStatus premise,
                                 List<ConflictReport> conflicts,
                                 List<ReadingReport> readings) {

        public ReportResponse {
            evidence = List.copyOf(evidence == null ? List.of() : evidence);
            limitations = List.copyOf(limitations == null ? List.of() : limitations);
            refusalReasons = List.copyOf(refusalReasons == null ? List.of() : refusalReasons);
            conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
            readings = List.copyOf(readings == null ? List.of() : readings);
        }
    }

    /**
     * Asks the workspace and writes the report.
     *
     * <p>This is the only method of this resource that carries an HTTP annotation, and
     * the only one an HTTP caller can reach. The method the journey runs is chosen by the
     * kernel from the evidence; nothing in the request can name it.</p>
     *
     * @param workspaceId the workspace the question is addressed to
     * @param request the request
     * @return the outcome
     */
    @POST
    public Response report(@PathParam("id") String workspaceId, ReportRequest request) {
        return run(workspaceId, request, null);
    }

    /**
     * The journey, with the method optionally FORCED.
     *
     * <p>This is the entry point the counterfactual arm of the closed loop runs through:
     * the same request, with the method chosen by somebody else instead of by the
     * evidence.
     *
     * <p><b>This is not an HTTP endpoint.</b> It carries no JAX-RS annotation, the
     * request it takes carries no field a caller could use to name a method, and the
     * annotated {@link #report(String, ReportRequest)} never calls it — the serving path
     * reaches the journey through {@link #run(String, ReportRequest, String)} with no
     * forced method. The forcing exists so that the two arms of the counterfactual
     * differ in exactly one thing: the method.</p>
     *
     * <p><b>What forcing does NOT do.</b> It does not widen anything. The consent, the
     * output-root confinement, the writer's lease and the idempotency are checked
     * before the journey starts, exactly as they are for an ordinary request, and a
     * forced method whose own preconditions do not hold refuses the request.</p>
     *
     * @param workspaceId the workspace the question is addressed to
     * @param request the request
     * @param forcedMethodId the method that must run
     * @return the response and what the journey produced
     */
    public JourneyResult journey(String workspaceId, ReportRequest request,
            String forcedMethodId) {
        Response response = run(workspaceId, request, forcedMethodId);
        return new JourneyResult(response, outcomeOf(workspaceId, request, response));
    }

    /**
     * The journey, as the serving path and the counterfactual both run it.
     *
     * @param workspaceId the workspace the question is addressed to
     * @param request the request
     * @param forcedMethodId the method that must run, or null to let the evidence choose
     * @return the response
     */
    private Response run(String workspaceId, ReportRequest request, String forcedMethodId) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return refused(400, "question_required", "a question is required");
        }
        if (request.question().length() > ArtifactActionService.MAX_QUESTION_LENGTH) {
            return refused(400, "question_too_long", "the question is longer than "
                    + ArtifactActionService.MAX_QUESTION_LENGTH + " characters");
        }
        if (request.idempotencyKey() != null
                && request.idempotencyKey().length() > ArtifactActionService.MAX_IDEMPOTENCY_KEY_LENGTH) {
            return refused(400, "idempotency_key_too_long", "the idempotency key is longer than "
                    + ArtifactActionService.MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        if (!Boolean.TRUE.equals(request.consent())) {
            // the consent is the caller's own statement that a file may be written; a request
            // without it is refused before anything is read, let alone written
            return refused(403, "consent_required",
                    "the request must carry consent: true — the report is written to the "
                            + "server's output root, and the consent says so");
        }

        ArtifactActionService.Gate gate = actions.gate();
        if (!gate.allowed()) {
            int status = "local_only_action".equals(gate.code()) ? 403 : 503;
            return refused(status, gate.code(), gate.detail());
        }

        if (!workbench.workspaces().contains(workspaceId)) {
            return refused(404, "workspace_not_found", "no workspace named " + workspaceId);
        }

        String identity = actions.identity(workspaceId, request.idempotencyKey(),
                request.question());
        String reportName = request.reportName();
        if (reportName == null || reportName.isBlank()) {
            reportName = actions.defaultReportName(identity);
        } else if (!plainReportName(reportName)) {
            return refused(400, "invalid_report_name",
                    "the report name must be a plain file name ending in .md, without a "
                            + "directory part, a parent reference or a reserved prefix");
        }

        AbstractionLevel level = parseLevel(request.level());

        // ── THE PLAN — the kernel chooses a method and says why ──────────────
        //
        // P2. The route does not decide how the report is produced. Before the workspace
        // is asked, the kernel's selector chooses a method for the request from the
        // registry it owns, and the decision says why. The plan is a PRECONDITION and not
        // an authority: it cannot widen the consent, the output-root confinement or the
        // writer's lease, and it cannot make a request servable that the checks above
        // refused. A request the kernel has no method for is refused here, with a reason —
        // the route does not fall back to "no method chosen" silently.
        if (planner == null) {
            // a route without the kernel's planner has no method to choose; that is a
            // refusal with a reason, not a silent fall back to "no method chosen"
            return refused(422, "no_method_for_request", "the kernel's planner is not "
                    + "available, so no method could be chosen for this request");
        }
        String episodeId = ReportJourneyTrace.episodeId(identity);
        CognitiveStore traceStore = traceStore();
        ReportJourney.Retained retained = traceStore == null || !consultRetainedTrace ? null
                : ReportJourneyTrace.retained(traceStore, episodeId);
        String fingerprint = workspaceFingerprint(workspaceId);
        Set<String> facts = journeyFacts(identity, retained, fingerprint, reportName,
                request.question(), level);

        ReportJourney.Problem problem = new ReportJourney.Problem(workspaceId, request.question(),
                identity, reportName, level, workbench, actions.writer(), retained, fingerprint);
        ProblemRequest planRequest = new ProblemRequest(AlgorithmFamilies.KNOWLEDGE_REPORT, problem,
                facts, ComputeBudget.unbounded());
        ReportJourneyPlanner.Plan plan = planner.plan(planRequest, forcedMethodId);
        if (plan.refused()) {
            return refused(422, "no_method_for_request", plan.reason());
        }

        // ── THE ACTION — the permissioned write, measured ───────────────────
        ReportJourney.Result result;
        try {
            result = planner.measure(plan.method().capability().id(),
                    () -> plan.method().execute(problem, ComputeBudget.unbounded()));
        } catch (AlgorithmRefusalException refused) {
            // the method refused: its own preconditions do not hold for this request. A
            // forced method is refused exactly like a chosen one — forcing chooses what
            // runs, it does not make the run possible.
            return refused(422, "method_refused", refused.getMessage());
        } catch (IllegalStateException unusable) {
            return refused(503, "output_root_unavailable", unusable.getMessage());
        }
        ArtifactWriter.Result written = result.write();
        if (!written.written()) {
            return refused(statusFor(written.disposition()), written.disposition().name(),
                    refusalExplanation(written.disposition()));
        }

        // ── THE OBSERVATION — read by a reader that did not write ────────────
        //
        // The action's own report of success is not the observation. The artifact is
        // measured on its bytes and the append-only index is read, by a reader that
        // shares nothing with the write path but the root. A disagreement between the
        // action's report and the observation is a FAILED effect, and it is recorded as
        // such rather than smoothed over.
        ArtifactObserver.Observation observation;
        try {
            observation = ArtifactObserver.observe(actions.writer(), identity, written.fileName(),
                    written.sha256());
        } catch (IOException unreadable) {
            return refused(503, "output_root_unavailable",
                    "the artifact could not be read back: " + unreadable.getMessage());
        }

        // ── THE TRACE — what happened, kept where the next task can read it ─
        List<String> traceFailures = new ArrayList<>();
        List<CognitiveStore.EventRecord> steps = new ArrayList<>();
        String planDetail = plan.method().capability().id() + ": " + plan.reason();
        steps.add(new CognitiveStore.EventRecord(ReportJourneyTrace.PHASE_PLAN,
                ReportJourneyTrace.ACTOR_PLANNER, planDetail));
        steps.add(new CognitiveStore.EventRecord(ReportJourneyTrace.PHASE_PERMISSION,
                ReportJourneyTrace.ACTOR_ROUTE,
                "the caller consented, the gate was open and the output root is the "
                        + "server's own; the plan did not widen either; "
                        + (retained == null ? "no retained journey was found"
                                : "a retained journey was found and consulted")));
        steps.add(new CognitiveStore.EventRecord(ReportJourneyTrace.PHASE_ACTION,
                ReportJourneyTrace.ACTOR_ACTION,
                ArtifactAction.NAME + " on " + identity + " produced " + written.disposition().name()
                        + " for " + written.fileName() + " with digest " + written.sha256()));
        steps.add(new CognitiveStore.EventRecord(ReportJourneyTrace.PHASE_OBSERVE,
                ReportJourneyTrace.ACTOR_OBSERVER,
                observation.detail() + "; the index holds " + observation.stagedRecords()
                        + " STAGED and " + observation.committedRecords() + " COMMITTED record(s)"));
        steps.add(new CognitiveStore.EventRecord(ReportJourneyTrace.PHASE_VERIFY,
                ReportJourneyTrace.ACTOR_OBSERVER,
                observation.verified()
                        ? "the effect is verified: the artifact is there, its digest is the one "
                                + "that was reported, and the index holds exactly one committed "
                                + "record for the identity"
                        : "the effect FAILED: the artifact and the index do not agree with what "
                                + "the action reported"));
        steps.add(new CognitiveStore.EventRecord(ReportJourneyTrace.PHASE_RESULT,
                ReportJourneyTrace.ACTOR_ROUTE,
                "the method " + plan.method().capability().id() + " produced "
                        + written.disposition().name()
                        + (result.replayed() ? " by replaying the retained journey" : "")));
        trace(traceFailures, traceStore, episodeId, steps);

        ReportResponse response;
        try {
            response = result.replayed()
                    ? replayedResponse(retained, written, traceFailures)
                    : response(result, written, traceFailures);
        } catch (IllegalStateException unreadable) {
            return refused(503, "output_root_unavailable", unreadable.getMessage());
        }
        trace(traceFailures, traceStore, episodeId, List.of(new CognitiveStore.EventRecord(
                ReportJourneyTrace.PHASE_RETAINED, ReportJourneyTrace.ACTOR_ROUTE,
                result.replayed() ? "the retained journey was served; the trace is unchanged"
                        : ReportJourneyTrace.payload(new ReportJourney.Retained(
                                plan.method().capability().id(), request.question(), level.name(),
                                fingerprint, written.sha256(),
                                written.fileName(), json(response))))));

        int status = written.disposition() == ArtifactWriter.Disposition.REPLAYED ? 200 : 201;
        return Response.status(status).entity(response).build();
    }

    /**
     * The outcome of a journey, measured from what the caller and the file system can see.
     *
     * <p>The measurement is read from the sources a reader would look at: the response the
     * caller received, the artifact on the disk, the writer's index and the trace. The
     * method that ran and the reason it was chosen come from the trace's own plan step —
     * the trace is where the journey recorded why it did what it did, and the outcome is
     * read from there rather than from the code that ran it.</p>
     *
     * @param workspaceId the workspace the question was addressed to
     * @param request the request
     * @param response the response the journey served
     * @return what the journey produced
     */
    private ReportJourneyOutcome outcomeOf(String workspaceId, ReportRequest request,
            Response response) {
        String identity = actions.identity(workspaceId, request.idempotencyKey(),
                request.question());
        String episodeId = ReportJourneyTrace.episodeId(identity);
        CognitiveStore store = traceStore();
        List<io.matrix.cognitive.LearningTrace.Step> steps = store == null ? List.of()
                : ReportJourneyTrace.steps(store, episodeId);
        String planDetail = "";
        for (io.matrix.cognitive.LearningTrace.Step step : steps) {
            if (ReportJourneyTrace.PHASE_PLAN.equals(step.phase())) {
                planDetail = step.detail();
            }
        }
        String methodId = "";
        String reason = "";
        int colon = planDetail.indexOf(": ");
        if (colon > 0) {
            methodId = planDetail.substring(0, colon);
            reason = planDetail.substring(colon + 2);
        }
        List<String> tracePhases = steps.stream()
                .map(io.matrix.cognitive.LearningTrace.Step::phase).toList();

        Object entity = response.getEntity();
        if (!(entity instanceof ReportResponse body)) {
            return new ReportJourneyOutcome(identity, methodId, false, reason,
                    "REFUSED", "", "", "", "", List.of(), tracePhases, false,
                    "the journey was refused and left no artifact", "", List.of(), null, 0, 0,
                    List.of(), List.of(String.valueOf(entity)));
        }
        String bytesDigest = "";
        boolean verified = false;
        String observationDetail = "the artifact could not be read back";
        try {
            ArtifactObserver.Observation observation = ArtifactObserver.observe(actions.writer(),
                    identity, body.artifactName(), body.sha256());
            bytesDigest = observation.sha256() == null ? "" : observation.sha256();
            verified = observation.verified();
            observationDetail = observation.detail();
        } catch (IOException unreadable) {
            observationDetail = "the artifact could not be read back: " + unreadable.getMessage();
        }
        List<String> indexStatuses = new ArrayList<>();
        try {
            for (ArtifactWriter.Record record : actions.writer().readIndex()) {
                if (record.actionId().equals(identity)) {
                    indexStatuses.add(record.status());
                }
            }
        } catch (IOException unreadable) {
            indexStatuses.add("the index could not be read");
        }
        List<String> claimIds = body.evidence().stream()
                .map(EvidenceReference::claimId).toList();
        String premiseStatus = body.premise() == null ? "" : body.premise().status();
        return new ReportJourneyOutcome(identity, methodId, reason.contains("FORCED"), reason,
                body.disposition(), body.artifactName(), body.artifactLocator(), body.sha256(),
                bytesDigest, indexStatuses, tracePhases, verified, observationDetail,
                body.epistemicDisposition(), claimIds, premiseStatus, body.conflicts().size(),
                body.readings().size(), body.limitations(), body.refusalReasons());
    }

    /**
     * The response of a journey and what the journey produced.
     *
     * @param response the response the caller received
     * @param outcome what the journey produced
     */
    public record JourneyResult(Response response, ReportJourneyOutcome outcome) {
    }

    /**
     * The response of a journey that asked the workspace and wrote the report.
     *
     * @param workspaceId the workspace the question was asked over
     * @param question    the question
     * @param result      what the chosen method produced
     * @param written     the writer's own result
     * @return the response
     */
    private ReportResponse response(ReportJourney.Result result, ArtifactWriter.Result written,
            List<String> traceFailures) {
        Answer answer = result.answer();
        List<EvidenceReference> evidence = new ArrayList<>();
        Map<String, String> sourceTitles = new LinkedHashMap<>();
        for (Source source : answer.sources()) {
            sourceTitles.put(source.id(), source.title());
        }
        for (Claim claim : answer.claims()) {
            evidence.add(new EvidenceReference(claim.id(), claim.proposition(),
                    String.valueOf(claim.status()), claim.introducedBySourceId(),
                    sourceTitles.get(claim.introducedBySourceId())));
        }

        List<String> limitations = new ArrayList<>(answer.limitations());
        limitations.add("a written report is a record of what the workspace said; it is not "
                + "independent evidence for the propositions it repeats");
        limitations.addAll(traceFailures);
        if (!answer.answered()) {
            limitations.add("the question was not answered; the absence of an answer is the "
                    + "epistemic result, not a defect of the file action");
        }
        List<String> refusals = List.of();
        if (written.disposition() == ArtifactWriter.Disposition.WRITTEN_TRACE_INCOMPLETE) {
            // the artifact IS committed; only its trace line is missing. The caller has to
            // know that the trace is incomplete, because the trace is what a later request
            // replays against.
            refusals = List.of("the report was written, but the trace line that records it "
                    + "could not be appended; the artifact's digest is the record of it");
            limitations.add("the trace of this write is incomplete: the artifact exists and "
                    + "carries the digest below, but the index does not yet name it");
        }

        return new ReportResponse(
                written.actionId(),
                ArtifactAction.NAME,
                ArtifactAction.VERSION,
                written.disposition().name(),
                written.fileName(),
                written.fileName(),
                written.sha256(),
                evidence,
                answer.answered() ? "ANSWERED" : "ABSTAINED",
                "artifact-index:" + written.actionId(),
                limitations,
                refusals,
                premiseStatus(answer),
                conflicts(answer),
                readings(answer));
    }

    /**
     * The response of a journey that replayed a retained one.
     *
     * <p>The answer is the one the earlier journey gave, and the artifact is the one that
     * was committed then: the workspace was not asked again, and the file was read from
     * the output root and checked against the digest the trace recorded. The disposition
     * says REPLAYED, because that is what the writer's own rule says about a second
     * write under one identity — one identity, one artifact.</p>
     *
     * @param result   what the replay method produced
     * @param retained what the earlier journey left behind
     * @param written  the writer's own result
     * @return the response
     */
    private ReportResponse replayedResponse(ReportJourney.Retained retained,
            ArtifactWriter.Result written, List<String> traceFailures) {
        ReportResponse previous = read(retained.responseJson());
        List<String> limitations = new ArrayList<>(previous.limitations());
        limitations.add("the journey replayed the retained one: the workspace was not asked "
                + "again, and the artifact was read from the output root and checked against "
                + "the digest the trace recorded");
        limitations.addAll(traceFailures);
        return new ReportResponse(previous.taskId(), previous.actionName(),
                previous.actionVersion(), written.disposition().name(), written.fileName(),
                written.fileName(), written.sha256(), previous.evidence(),
                previous.epistemicDisposition(), previous.traceReference(), limitations,
                previous.refusalReasons(), previous.premise(), previous.conflicts(),
                previous.readings());
    }

    /**
     * The facts the request establishes about itself.
     *
     * <p>They are the applicability domain of the replay method, and the selector
     * enforces them: a request that does not establish the domain cannot select the
     * replay, and the reference method takes over.</p>
     *
     * @param identity    the action identity
     * @param retained    what the last journey for this identity left behind
     * @param fingerprint the fingerprint of the workspace as it is now
     * @param reportName  the requested report name
     * @return the facts
     */
    private Set<String> journeyFacts(String identity, ReportJourney.Retained retained,
            String fingerprint, String reportName, String question, AbstractionLevel level) {
        Set<String> facts = new LinkedHashSet<>();
        if (retained != null && ReportJourneyMethod.committed(actions.writer(), identity)) {
            facts.add(ReportJourneyMethod.FACT_IDENTITY_COMMITTED);
        }
        if (retained != null && retained.workspaceFingerprint().equals(fingerprint)) {
            facts.add(ReportJourneyMethod.FACT_WORKSPACE_UNCHANGED);
        }
        if (retained != null && retained.fileName().equals(reportName)) {
            facts.add(ReportJourneyMethod.FACT_NAME_UNCHANGED);
        }
        // the retained answer answers one question at one level. The facts are the
        // request's own comparison with the retained record, so the replay is admitted
        // only inside the domain it can state — a different question or a different level
        // is a different request, and the retained answer does not answer it
        if (retained != null && retained.question() != null
                && retained.question().equals(question)) {
            facts.add(ReportJourneyMethod.FACT_QUESTION_UNCHANGED);
        }
        if (retained != null && retained.level() != null
                && retained.level().equals(level.name())) {
            facts.add(ReportJourneyMethod.FACT_LEVEL_UNCHANGED);
        }
        return facts;
    }

    /**
     * The fingerprint of the workspace as it is now.
     *
     * <p>It covers the fields of every claim the workspace holds that can change WHAT the
     * workspace would answer: the claim's identifier, the concept it belongs to, its
     * proposition, its scope, its context, its status and its version. A workspace whose
     * scope or context moved has moved the answer even when the proposition did not, so
     * a journey that read the older one must not be replayed over it.</p>
     *
     * <p>The fields are read one by one rather than through reflection, so that a field
     * added to the claim later cannot silently widen or narrow the fingerprint.</p>
     *
     * @param workspaceId the workspace
     * @return the fingerprint
     */
    private String workspaceFingerprint(String workspaceId) {
        return WorkspaceFingerprint.of(workbench.workspace(workspaceId));
    }

    /**
     * The kernel's store, where the journey's trace is written.
     *
     * <p>It is the store the kernel writes its own cycles into, over the knowledge
     * database the workbench already uses. When it cannot be opened the journey still
     * runs — and the response says that the trace was not kept, because an unkept trace
     * is a fact the caller has to be told.</p>
     *
     * @return the store, or null when it cannot be opened
     */
    private synchronized CognitiveStore traceStore() {
        if (cognitive == null) {
            return null;
        }
        try {
            return cognitive.store();
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    /**
     * Appends the journey's steps to its trace.
     *
     * <p>The steps are written in ONE durable write: a trace is wholly there or wholly
     * absent, and the write costs what it costs once rather than once per step. When the
     * store is not available the failure is collected and told to the caller — an unkept
     * trace is a fact the caller has to be told, not a detail the route may swallow.</p>
     *
     * @param failures  the failures collected so far
     * @param store     the kernel's store, or null when there is none
     * @param episodeId the episode the journey belongs to
     * @param steps     the steps, in order
     */
    private void trace(List<String> failures, CognitiveStore store, String episodeId,
            List<CognitiveStore.EventRecord> steps) {
        if (store == null) {
            failures.add("the journey's trace could not be written: the kernel's store is not "
                    + "available, and nothing was retained about this run");
            return;
        }
        try {
            ReportJourneyTrace.appendAll(store, episodeId, steps);
        } catch (RuntimeException unwritable) {
            failures.add("the journey's trace could not be written: " + unwritable.getMessage());
        }
    }

    /**
     * The response, as the retained payload keeps it.
     *
     * @param response the response
     * @return the JSON
     */
    private static String json(ReportResponse response) {
        try {
            return JSON.writeValueAsString(response);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write the response as JSON", e);
        }
    }

    /**
     * The response the retained payload holds.
     *
     * @param payload the JSON
     * @return the response
     */
    private static ReportResponse read(String payload) {
        try {
            return JSON.readValue(payload, ReportResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read the retained response", e);
        }
    }

    /**
     * The epistemic status of the question's premise, as the workspace made of it.
     *
     * <p>The claim and source identifiers that refute the premise are named here, because a
     * refusal that does not say WHAT refuted it is a refusal the caller cannot act on.
     *
     * @param answer the answer the workspace gave
     * @return the status, or null when the question asserted no premise
     */
    private PremiseStatus premiseStatus(Answer answer) {
        PremiseSemantics.Disposition disposition = answer.premise();
        if (disposition == null || disposition.status() == PremiseSemantics.Status.NO_PREMISE) {
            return null;
        }
        List<String> claimIds = new ArrayList<>();
        List<String> sourceIds = new ArrayList<>();
        for (PremiseAssessment.EvidenceRef reference : disposition.evidence()) {
            if (!"CONTRADICTS".equals(reference.relation())) {
                continue;
            }
            claimIds.add(reference.claimId());
            if (reference.sourceId() != null) {
                sourceIds.add(reference.sourceId());
            }
        }
        return new PremiseStatus(disposition.status().name(), disposition.kind().name(),
                disposition.premise(), disposition.reason(), claimIds, sourceIds);
    }

    /**
     * The conflicts the cited evidence holds, both sides with their provenance.
     *
     * @param answer the answer the workspace gave
     * @return the conflicts
     */
    private List<ConflictReport> conflicts(Answer answer) {
        PremiseSemantics.Disposition disposition = answer.premise();
        if (disposition == null || disposition.conflicts().isEmpty()) {
            return List.of();
        }
        List<ConflictReport> reports = new ArrayList<>();
        for (ClaimConflictDetector.Conflict conflict : disposition.conflicts()) {
            List<ConflictSide> sides = new ArrayList<>();
            for (ClaimConflictDetector.Side side : conflict.sides()) {
                sides.add(new ConflictSide(side.claimId(), side.proposition(), side.sourceId(),
                        side.sourceTitle(),
                        side.sourceLocator() == null ? null : actions.redact(side.sourceLocator()),
                        side.detail()));
            }
            reports.add(new ConflictReport(conflict.reason(), sides));
        }
        return reports;
    }

    /**
     * The readings of a question that admits several, each with its provenance.
     *
     * @param answer the answer the workspace gave
     * @return the readings
     */
    private List<ReadingReport> readings(Answer answer) {
        PremiseSemantics.Disposition disposition = answer.premise();
        if (disposition == null || disposition.readings().isEmpty()) {
            return List.of();
        }
        List<ReadingReport> reports = new ArrayList<>();
        for (PremiseSemantics.Reading reading : disposition.readings()) {
            List<EvidenceReference> evidence = new ArrayList<>();
            for (PremiseAssessment.EvidenceRef reference : reading.evidence()) {
                evidence.add(new EvidenceReference(reference.claimId(), reference.proposition(),
                        reference.claimStatus() == null ? null : reference.claimStatus().name(),
                        reference.sourceId(), reference.sourceTitle()));
            }
            reports.add(new ReadingReport(reading.label(), evidence));
        }
        return reports;
    }

    /**
     * Whether a requested report name is a plain file name.
     *
     * <p>The writer refuses such names too; the route refuses them earlier, so that a
     * traversal attempt is answered as a bad request instead of a write failure.</p>
     */
    private static boolean plainReportName(String name) {
        if (name.isBlank() || name.length() > MAX_REPORT_NAME_LENGTH) {
            return false;
        }
        if (name.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            return false;
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            return false;
        }
        if (name.startsWith(".")) {
            return false;
        }
        return name.endsWith(".md");
    }

    /** The HTTP status a refusal of the writer carries. */
    private static int statusFor(ArtifactWriter.Disposition disposition) {
        return switch (disposition) {
            case REFUSED_IDENTITY, REFUSED_WRITER_CONFLICT -> 409;
            case REFUSED_BUDGET -> HTTP_INSUFFICIENT_STORAGE;
            case REFUSED_OUTSIDE_ROOT, REFUSED_TRAVERSAL, REFUSED_SYMLINK_ESCAPE,
                    REFUSED_UNSUPPORTED_TYPE, REFUSED_TOO_LARGE, REFUSED_INVALID_CONTENT,
                    REFUSED_EMPTY_NAME -> 400;
            case REFUSED_INDEX_CORRUPT, REFUSED_ROOT_REPLACED, REFUSED_WRITE_FAILED -> 500;
            default -> 500;
        };
    }

    /** The level a request asks for. */
    private static AbstractionLevel parseLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return AbstractionLevel.INTRODUCTORY;
        }
        try {
            return AbstractionLevel.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return AbstractionLevel.INTRODUCTORY;
        }
    }

    /** A refusal, in the shape the route's clients read. */
    private Response refused(int status, String code, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        // the server's filesystem paths are the server's business: a refusal names the
        // file system it refused to write to, and that path does not leave the process
        body.put("detail", actions.redact(detail));
        return Response.status(status).entity(body).build();
    }

    /**
     * The route's own explanation of a refusal of the bounded action.
     *
     * <p>The writer's reason names the file system it refused to write to; the route
     * states what the refusal MEANS and keeps the path out of the response.</p>
     */
    private String refusalExplanation(ArtifactWriter.Disposition disposition) {
        return switch (disposition) {
            case REFUSED_IDENTITY -> "the report name is already bound to another action "
                    + "identity; one idempotency key names one report";
            case REFUSED_WRITER_CONFLICT -> "another writer holds this output root at this moment";
            case REFUSED_BUDGET -> "the output root's own budget is exhausted";
            case REFUSED_OUTSIDE_ROOT -> "the report name does not address a file inside the "
                    + "authorized root";
            case REFUSED_TRAVERSAL -> "the report name is not a plain file name";
            case REFUSED_SYMLINK_ESCAPE -> "the report name or the trace is a link, and the writer "
                    + "never writes through one";
            case REFUSED_UNSUPPORTED_TYPE -> "the report name must end in .md";
            case REFUSED_TOO_LARGE -> "the report is larger than the writer's bound";
            case REFUSED_INVALID_CONTENT -> "the report's content does not pass the writer's "
                    + "validation";
            case REFUSED_EMPTY_NAME -> "the report name is required";
            case REFUSED_INDEX_CORRUPT -> "the output root's trace is not a readable append-only "
                    + "log, and the writer refuses to act on an unsound trace";
            case REFUSED_ROOT_REPLACED -> "the authorized output root was replaced";
            case REFUSED_WRITE_FAILED -> "the report could not be written";
            default -> "the bounded action refused the write";
        };
    }
}
