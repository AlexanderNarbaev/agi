package io.matrix.knowledge;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import io.matrix.knowledge.acquisition.ClaimValidator;
import io.matrix.knowledge.acquisition.DocumentImporter;

/**
 * The HTTP surface of the MATRIX Learning Workbench
 * (owner directive §18, §19, §33).
 *
 * <p>Every endpoint here performs the real operation. There is no endpoint
 * that returns a placeholder, and no screen in the interface that is
 * connected to something that does not work.
 *
 * <p>The workflows the owner directive names map onto the endpoints as
 * follows:
 *
 * <ul>
 *   <li>Workflow A, create a workspace — {@code POST /workspaces/{id}/load}
 *       and {@code GET /workspaces/{id}/concepts}</li>
 *   <li>Workflow B, provide knowledge — the package import, and the claims
 *       and sources endpoints</li>
 *   <li>Workflow C, investigate — {@code GET /workspaces/{id}/gaps}</li>
 *   <li>Workflow D, ask questions — {@code POST /workspaces/{id}/ask}</li>
 *   <li>Workflow E, test knowledge — the assessment endpoints</li>
 *   <li>Workflow F, review learning — {@code GET /workspaces/{id}/learning-state}</li>
 *   <li>Workflow H, stop and restart — {@code GET /status}, read from the
 *       store on disk</li>
 * </ul>
 */
@Path("/v1/workbench")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LearningWorkbenchResource {

    @Inject
    KnowledgeWorkbench workbench;

    /** The body of an ask request. */
    public record AskRequest(String question, String level) {
    }

    /** The body of an assessment request. */
    public record AssessRequest(String itemId, String answer, Double confidence, String sessionId) {
    }

    /** The body of a teach-back request. */
    public record TeachBackRequest(String conceptId, String level, Double expectedGain) {
    }

    /** The body of a goal request. */
    public record GoalRequest(
            String description,
            List<String> successCriteria,
            List<String> prerequisiteConceptIds,
            List<SubtaskRequest> subtasks,
            Integer budgetUnits,
            String recoveryInstructions) {
    }

    /** One subtask of a goal. */
    public record SubtaskRequest(
            String id,
            String description,
            Boolean required,
            List<String> prerequisiteSubtaskIds) {
    }

    /** The body of a subtask completion. */
    public record SubtaskCompletion(List<String> evidenceIds, String note) {
    }

    /** The body of a checkpoint. */
    public record CheckpointRequest(String checkpoint, String remainingWork) {
    }

    /** The body of a teach-back experiment request. */
    public record TeachBackExperimentRequest(String conceptId, Double expectedGain) {
    }

    /** The body of a refinement request. */
    public record RefineRequest(
            String conceptId,
            String supersededClaimId,
            String proposition,
            String scope,
            String context,
            String status,
            Double confidence,
            String sourceKind,
            String sourceTitle,
            String sourceLocator,
            String sourceExcerpt,
            Integer sourceAuthority,
            Integer sourceFreshness,
            String revisionKind,
            String summary,
            List<String> addedFacts,
            List<String> stillValidAssumptions,
            List<String> noLongerValidAssumptions,
            List<String> newlyExplainedCases,
            String refinedRepresentation,
            String refinedRepresentationLevel) {
    }

    /** The storage and workspace status. */
    @GET
    @Path("/status")
    public Map<String, Object> status() {
        return workbench.status();
    }

    /** The workspaces that exist. */
    @GET
    @Path("/workspaces")
    public List<String> workspaces() {
        return workbench.workspaces();
    }

    /**
     * Load the bundled package into a workspace.
     *
     * <p>The import replaces the workspace, so it is also the way to start
     * over — deliberately, and visibly.
     */
    @POST
    @Path("/workspaces/{id}/load")
    public Response load(@PathParam("id") String workspaceId) {
        if (!KnowledgeWorkbench.DEFAULT_WORKSPACE.equals(workspaceId)
                && !KnowledgeWorkbench.LOGIC_WORKSPACE.equals(workspaceId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "no bundled package for workspace " + workspaceId))
                    .build();
        }
        KnowledgeSnapshot snapshot = workbench.loadBundledPackage(workspaceId);
        return Response.ok(Map.of(
                "workspace", snapshot.workspaceId(),
                "concepts", snapshot.concepts().size(),
                "claims", snapshot.claims().size(),
                "sources", snapshot.sources().size(),
                "evidence", snapshot.evidence().size(),
                "representations", snapshot.representations().size(),
                "revisions", snapshot.revisions().size(),
                "derivations", snapshot.derivations().size())).build();
    }

    /**
     * Imports a document into a workspace.
     *
     * <p>The mission's §5: a document becomes knowledge through the
     * extraction pipeline, and every claim it produces carries its source,
     * its excerpt and its location. The document travels as a multipart
     * body; nothing is fetched from the network, and the file's size is
     * bounded before it is read.
     */
    @POST
    @Path("/workspaces/{id}/documents")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importDocument(@PathParam("id") String workspaceId,
                                   @RestForm("file") FileUpload upload,
                                   @RestForm("language") String language) {
        if (upload == null || upload.uploadedFile() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "a file is required"))
                    .build();
        }
        String filename = upload.fileName() == null ? "document.txt" : upload.fileName();
        try {
            java.nio.file.Path temporary = Files.createTempFile("matrix-import-", "-" + filename);
            try {
                Files.copy(upload.uploadedFile(), temporary, StandardCopyOption.REPLACE_EXISTING);
                // The workbench does the import, not the resource: the
                // workbench also GENERATES the assessment items for the
                // concepts the document brought in, and the resource used
                // to call the importer directly - so the imported concept
                // was never practisable over HTTP, although it was
                // practisable in the tests.
                DocumentImporter.ImportResult result = workbench.importDocument(workspaceId,
                        temporary, filename,
                        language == null || language.isBlank() ? null : language);
                Map<String, Object> report = new LinkedHashMap<>();
                report.put("workspace", result.workspaceId());
                report.put("sourceId", result.source().sourceId());
                report.put("filename", result.source().filename());
                report.put("format", result.source().format().name());
                report.put("contentHash", result.source().contentHash());
                report.put("language", result.source().language());
                report.put("author", result.source().author());
                report.put("publicationDate", result.source().publicationDate());
                report.put("extractionQuality", result.source().extractionQuality());
                report.put("segments", result.segments());
                report.put("claimsProposed", result.claimsProposed());
                report.put("conceptsCreated", result.conceptsCreated());
                report.put("representationsCreated", result.representationsCreated());
                // The assessment items generated for the concepts the
                // document brought in: the concept has to be PRACTISABLE,
                // not only explainable.
                AssessmentGenerator.Result generated = workbench.generateAssessment(workspaceId);
                report.put("theAssessmentItemsGenerated", generated.items().size());
                report.put("theAssessmentConcepts", generated.concepts());
                // The validation stage of the pipeline: the claims the
                // document contributed are measured against the claims the
                // workspace already held, and a claim that two
                // INDEPENDENT sources state is promoted.
                ClaimValidator.Result validation = result.validation();
                report.put("theClaimsPromoted", validation.promoted());
                report.put("theClaimsSuperseded", validation.superseded());
                report.put("theValidationRefusals", validation.refusals());
                return Response.ok(report).build();
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (java.io.UncheckedIOException e) {
            // The extractor wraps a refusal in an UncheckedIOException: a
            // document that cannot be read is the CALLER's problem, not a
            // server fault. The mission's §13.2 names the malformed and
            // the corrupted documents, and a refusal must be a clear
            // refusal - not a 500 with a stack trace.
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getCause() == null
                            ? e.getMessage() : e.getCause().getMessage()))
                    .build();
        }
    }

    /** The concepts of a workspace. */
    @GET
    @Path("/workspaces/{id}/concepts")
    public List<Concept> concepts(@PathParam("id") String workspaceId) {
        return workbench.workspace(workspaceId).concepts();
    }

    /** The claims of a workspace. */
    @GET
    @Path("/workspaces/{id}/claims")
    public List<Claim> claims(@PathParam("id") String workspaceId) {
        return workbench.workspace(workspaceId).claims();
    }

    /** The sources of a workspace. */
    @GET
    @Path("/workspaces/{id}/sources")
    public List<Source> sources(@PathParam("id") String workspaceId) {
        return workbench.workspace(workspaceId).sources();
    }

    /** The evidence links of a workspace. */
    @GET
    @Path("/workspaces/{id}/evidence")
    public List<Evidence> evidence(@PathParam("id") String workspaceId) {
        return workbench.workspace(workspaceId).evidence();
    }

    /** The representations of a workspace, with their refinement chain. */
    @GET
    @Path("/workspaces/{id}/representations")
    public List<Representation> representations(@PathParam("id") String workspaceId) {
        return workbench.workspace(workspaceId).representations();
    }

    /** The revision history of a workspace. */
    @GET
    @Path("/workspaces/{id}/revisions")
    public List<KnowledgeRevision> revisions(@PathParam("id") String workspaceId) {
        return workbench.workspace(workspaceId).revisions();
    }

    /** Ask a question at a chosen level. */
    @POST
    @Path("/workspaces/{id}/ask")
    public Response ask(@PathParam("id") String workspaceId, AskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "a question is required"))
                    .build();
        }
        AbstractionLevel level = parseLevel(request.level());
        Answer answer = workbench.ask(workspaceId, request.question(), level);
        return Response.ok(answer).build();
    }

    /**
     * Reasons over a workspace and answers a question.
     *
     * <p>The answer carries the stages of the pipeline, so the user can see
     * how the question was read, what was retrieved, which rules were
     * checked and what the answer rests on.
     */
    @POST
    @Path("/workspaces/{id}/reason")
    public Response reason(@PathParam("id") String workspaceId, AskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "a question is required"))
                    .build();
        }
        AbstractionLevel level = parseLevel(request.level());
        ReasoningEngine.Result result = workbench.reason(workspaceId, request.question(), level);
        return Response.ok(result).build();
    }

    // ── W43.2: the counterexample cycle, over HTTP ───────────────────────────

    /** The body of an example request. */
    public record ExampleRequest(String observation, String condition, Boolean contradicts) {
    }

    /** The body of a refinement request. */
    public record RefinementRequest(String proposition, String reason) {
    }

    /**
     * Records an example against a claim, or a counterexample against it.
     *
     * <p>The observation is stored as an OBSERVATION source with the
     * condition it was made under, and the evidence link says how it bears
     * on the claim. Nothing is promoted and nothing is decided here: the
     * assessment is a separate question, and the user asks it.
     */
    @POST
    @Path("/workspaces/{id}/claims/{claimId}/examples")
    public Response example(@PathParam("id") String workspaceId,
                             @PathParam("claimId") String claimId,
                             ExampleRequest request) {
        if (request == null || request.observation() == null || request.observation().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "an observation is required"))
                    .build();
        }
        boolean contradicts = request.contradicts() != null && request.contradicts();
        try {
            Evidence evidence = workbench.recordExample(workspaceId, claimId,
                    request.observation(), request.condition(), contradicts);
            return Response.ok(Map.of(
                    "evidenceId", evidence.id(),
                    "claimId", evidence.claimId(),
                    "sourceId", evidence.sourceId(),
                    "relation", evidence.relation().name(),
                    "excerpt", evidence.excerpt())).build();
        } catch (IllegalArgumentException unknown) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", unknown.getMessage()))
                    .build();
        }
    }

    /**
     * The assessment of a claim: does it still hold, on what evidence, and
     * with what left unresolved.
     */
    @GET
    @Path("/workspaces/{id}/claims/{claimId}/assessment")
    public Response assessment(@PathParam("id") String workspaceId,
                                @PathParam("claimId") String claimId) {
        try {
            return Response.ok(workbench.assessClaim(workspaceId, claimId).asMap()).build();
        } catch (IllegalArgumentException unknown) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", unknown.getMessage()))
                    .build();
        }
    }

    /**
     * Accepts a proposed refinement.
     *
     * <p>The original claim is kept and becomes SUPERSEDED; the narrower
     * claim and the revision that links them are stored alongside it.
     */
    @POST
    @Path("/workspaces/{id}/claims/{claimId}/refine/accept")
    public Response acceptRefinement(@PathParam("id") String workspaceId,
                                      @PathParam("claimId") String claimId,
                                      RefinementRequest request) {
        if (request == null || request.proposition() == null || request.proposition().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "the refined proposition is required"))
                    .build();
        }
        try {
            KnowledgeRevision revision = workbench.acceptRefinement(workspaceId, claimId,
                    request.proposition());
            return Response.ok(Map.of(
                    "revisionId", revision.id(),
                    "claimId", revision.targetId(),
                    "kind", revision.kind().name(),
                    "summary", revision.summary())).build();
        } catch (IllegalArgumentException unknown) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", unknown.getMessage()))
                    .build();
        }
    }

    /** Rejects a proposed refinement; the original claim stands. */
    @POST
    @Path("/workspaces/{id}/claims/{claimId}/refine/reject")
    public Response rejectRefinement(@PathParam("id") String workspaceId,
                                     @PathParam("claimId") String claimId,
                                     RefinementRequest request) {
        try {
            KnowledgeRevision revision = workbench.rejectRefinement(workspaceId, claimId,
                    request == null ? null : request.reason());
            return Response.ok(Map.of(
                    "revisionId", revision.id(),
                    "claimId", revision.targetId(),
                    "kind", revision.kind().name(),
                    "summary", revision.summary())).build();
        } catch (IllegalArgumentException unknown) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", unknown.getMessage()))
                    .build();
        }
    }

    /** The recorded inferences of a workspace. */
    @GET
    @Path("/workspaces/{id}/derivations")
    public List<Derivation> derivations(@PathParam("id") String workspaceId) {
        return workbench.derivations(workspaceId);
    }

    /** The recorded conflicts of a workspace. */
    @GET
    @Path("/workspaces/{id}/contradictions")
    public List<Contradiction> contradictions(@PathParam("id") String workspaceId) {
        return workbench.contradictions(workspaceId);
    }

    /** The goals of a workspace. */
    @GET
    @Path("/workspaces/{id}/goals")
    public List<LearningGoal> goals(@PathParam("id") String workspaceId) {
        return workbench.goals(workspaceId);
    }

    /** The goals that were under way, for a restarted process to pick up. */
    @GET
    @Path("/workspaces/{id}/goals/resumable")
    public List<LearningGoal> resumableGoals(@PathParam("id") String workspaceId) {
        return workbench.resumableGoals(workspaceId);
    }

    /**
     * The conversation of a workspace.
     *
     * <p>The mission's §6: the dialogue subsystem keeps a bounded history
     * of what was said, in what class, and about which concepts.
     */
    @GET
    @Path("/workspaces/{id}/dialogue")
    public List<DialogueContext.Turn> dialogue(@PathParam("id") String workspaceId) {
        return workbench.context(workspaceId).turns();
    }

    /**
     * The class of an utterance.
     *
     * <p>The mission's §5 names the classes a dialogue must distinguish:
     * question, correction, observation, hypothesis, teach-request, example,
     * failed-explanation, experiment-result.
     */
    @GET
    @Path("/workspaces/{id}/dialogue/classify")
    public Map<String, Object> classify(@PathParam("id") String workspaceId,
                                        @QueryParam("utterance") String utterance) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("utterance", utterance);
        result.put("act", DialogueAct.classify(utterance).name());
        result.put("language", TextProfiles.detect(utterance));
        result.put("referential", DialogueContext.isReferential(utterance));
        return result;
    }

    /**
     * Runs the retention baseline experiment.
     *
     * <p>The mission's §14 asks that the delayed retention be measured
     * against a no-consolidation baseline. The two arms are the same
     * learner under two selection policies, so the difference is the
     * policy and not the learner.
     *
     * <p>The arms run over their own workspaces: the first version ran
     * both over one, and the second arm inherited the first arm's
     * outcomes.
     */
    @GET
    @Path("/workspaces/{id}/retention-experiment")
    public Map<String, Object> retentionExperiment(@PathParam("id") String workspaceId,
                                                    @QueryParam("maxSessions") Integer maxSessions) {
        int max = maxSessions == null || maxSessions < 1 ? 20 : Math.min(maxSessions, 100);
        java.nio.file.Path directory;
        try {
            directory = java.nio.file.Files.createTempDirectory("matrix-retention");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to create the experiment's directory", e);
        }
        RetentionExperiment.Report report = RetentionExperiment.run(
                () -> KnowledgeWorkbench.at(directory.resolve(
                        "arm-" + java.util.UUID.randomUUID() + ".sqlite").toString()),
                workspaceId, max);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspace", workspaceId);
        result.put("itemsInTheDataset", report.itemsInTheDataset());
        result.put("whatWasMeasured", report.whatWasMeasured());
        result.put("limits", report.limits());
        result.put("baseline", arm(report.baseline()));
        result.put("consolidation", arm(report.consolidation()));
        result.put("theRetentionStageOfTheBaseline", report.baseline().retentionStage());
        result.put("theRetentionStageOfTheConsolidation", report.consolidation().retentionStage());
        return result;
    }

    /** The arm's result as a map. */
    private Map<String, Object> arm(RetentionExperiment.ArmResult arm) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policy", arm.policy().name());
        result.put("sessions", arm.sessions());
        result.put("itemsAsked", arm.itemsAsked());
        result.put("itemsWrongAtTheEnd", arm.itemsWrongAtTheEnd());
        result.put("theMeanScore", arm.meanScore());
        result.put("theWeakestStage", arm.weakestStage().name());
        result.put("theRetentionStage", arm.retentionStage());
        result.put("theRetentionEvidence", arm.retentionEvidence());
        return result;
    }

    /**
     * Consolidates a workspace.
     *
     * <p>The mission's §8 describes a bounded consolidation in which
     * forgetting is marked and never destructive. The run is bounded by
     * its budget, it is idempotent, and it does not change any claim:
     * a claim's truth comes from its evidence and not from how often it
     * was asked about.
     *
     * <p>The instant of the run is supplied by the caller, because the
     * CONSTITUTION's first article forbids a clock in a decision path.
     */
    @POST
    @Path("/workspaces/{id}/consolidate")
    public Map<String, Object> consolidate(@PathParam("id") String workspaceId,
                                           @QueryParam("maxForgotten") Integer maxForgotten,
                                           @QueryParam("ageWindowDays") Integer ageWindowDays,
                                           @QueryParam("nowMs") Long nowMs) {
        int max = maxForgotten == null || maxForgotten < 0 ? 100 : Math.min(maxForgotten, 1000);
        long window = ageWindowDays == null || ageWindowDays < 0
                ? 30L * 24 * 60 * 60 * 1000
                : ageWindowDays * 24L * 60 * 60 * 1000;
        long now = nowMs == null ? System.currentTimeMillis() : nowMs;
        KnowledgeConsolidator.Report report = KnowledgeConsolidator.consolidate(workbench,
                workspaceId, now, new KnowledgeConsolidator.Budget(max, window));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspace", workspaceId);
        result.put("examined", report.examined());
        result.put("candidates", report.candidates());
        result.put("forgotten", report.forgotten());
        result.put("idempotent", report.idempotent());
        result.put("invariants", report.invariants());
        List<Map<String, Object>> marked = new ArrayList<>();
        for (KnowledgeConsolidator.Candidate candidate : report.marked()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("question", candidate.question());
            entry.put("kind", candidate.kind().name());
            entry.put("reason", candidate.reason());
            marked.add(entry);
        }
        result.put("marked", marked);
        // THE ACCEPTANCE CRITERION.
        //
        // The consolidation PROPOSES the forgetting and keeps the proposal
        // only if the knowledge's answerability on the held-out items did
        // not fall - the sleep consolidator's rule, wired into the
        // knowledge domain.
        KnowledgeConsolidator.Acceptance acceptance = report.acceptance();
        if (acceptance != null) {
            Map<String, Object> probe = new LinkedHashMap<>();
            probe.put("theItemsProbed", acceptance.probed());
            probe.put("theAnsweredBefore", acceptance.answeredBefore());
            probe.put("theAnsweredAfter", acceptance.answeredAfter());
            probe.put("theProposalAccepted", acceptance.accepted());
            probe.put("theRolledBack", acceptance.rolledBack());
            result.put("theAcceptance", probe);
        }
        // THE ERROR-DRIVEN DECAY, VISIBLE.
        if (report.decay() != null) {
            Map<String, Object> decay = new LinkedHashMap<>();
            decay.put("theConcepts", report.decay().concepts());
            decay.put("theVerifiedErrors", report.decay().errors());
            decay.put("theStrengthBefore", report.decay().strengthBefore());
            decay.put("theStrengthAfter", report.decay().strengthAfter());
            decay.put("theActiveBefore", report.decay().activeBefore());
            decay.put("theActiveAfter", report.decay().activeAfter());
            result.put("theDecay", decay);
        }
        // THE ERROR-DRIVEN INTEGRATION, VISIBLE.
        //
        // The consolidation's phase that records the VERIFIED learning
        // errors was not reported over HTTP, so the phase was invisible to
        // anyone who used the API - and an invisible phase is one nobody
        // checks.
        ErrorIntegrator.Result integration = report.integration();
        if (integration != null) {
            Map<String, Object> phase = new LinkedHashMap<>();
            phase.put("theCandidates", integration.candidates());
            phase.put("theVerified", integration.verified());
            phase.put("theRecorded", integration.recorded());
            phase.put("theUnverified", integration.unverified());
            List<Map<String, Object>> errors = new ArrayList<>();
            for (LearningError error : integration.errors()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("theItem", error.itemId());
                entry.put("theConcept", error.conceptId());
                entry.put("theDimension", error.dimension().name());
                entry.put("verified", error.verified());
                errors.add(entry);
            }
            phase.put("theErrors", errors);
            result.put("theIntegration", phase);
        }
        return result;
    }

    /**
     * The experiment dashboard: the measured instrument, not a claim.
     *
     * <p>The mission's §9 names an Experiment Dashboard among the views.
     * This one shows the numbers the same instrument produces every time:
     * the W42 benchmark over this workspace, by category and by language,
     * and the workspace's own coverage. The numbers are measured here and
     * now, from the workspace as it stands — not copied from a report.
     */
    @GET
    @Path("/workspaces/{id}/experiments")
    public Map<String, Object> experiments(@PathParam("id") String workspaceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workspace", workspaceId);
        try {
            List<io.matrix.knowledge.benchmark.BenchmarkItem> items =
                    io.matrix.knowledge.benchmark.BenchmarkItem.load("benchmark/w42-benchmark-v1.json");
            io.matrix.knowledge.benchmark.BenchmarkRunner.Report report =
                    new io.matrix.knowledge.benchmark.BenchmarkRunner(workbench).run(items);
            body.put("theItems", report.results().size());
            body.put("theAnswerable", report.answerableCount());
            body.put("theAnsweredCorrectly", report.answeredCorrectly());
            body.put("theUnanswerable", report.unanswerableCount());
            body.put("theRefusedCorrectly", report.refusedCorrectly());
            body.put("theAnswerAccuracy", report.answerAccuracy());
            body.put("theAbstentionQuality", report.abstentionQuality());
            body.put("theBm25Overlap", report.meanBm25Overlap());
            List<Map<String, Object>> categories = new ArrayList<>();
            for (Map.Entry<String, io.matrix.knowledge.benchmark.BenchmarkRunner.Report.Group> entry
                    : report.byCategory().entrySet()) {
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("theCategory", entry.getKey());
                group.put("theItems", entry.getValue().items());
                group.put("theCorrect", entry.getValue().correct());
                group.put("theRefusedCorrectly", entry.getValue().refusedCorrectly());
                categories.add(group);
            }
            body.put("theCategories", categories);
            List<Map<String, Object>> languages = new ArrayList<>();
            for (Map.Entry<String, io.matrix.knowledge.benchmark.BenchmarkRunner.Report.Group> entry
                    : report.byLanguage().entrySet()) {
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("theLanguage", entry.getKey());
                group.put("theItems", entry.getValue().items());
                group.put("theCorrect", entry.getValue().correct());
                languages.add(group);
            }
            body.put("theLanguages", languages);
        } catch (RuntimeException e) {
            body.put("theError", "The benchmark could not be run: " + e.getMessage());
        }
        body.put("theNote", "These numbers are measured here and now, from this workspace, with "
                + "the same instrument since W42.0. The support score is not a probability and is "
                + "not calibrated; the BM25 overlap says how much of the retrieval the lexical "
                + "baseline already explained.");
        return body;
    }

    /**
     * The sessions of practice in a workspace.
     *
     * <p>The mission's §9 names a Learning Sessions view. The learner
     * practised in sessions, and a session is evidence about the learner
     * that a single item is not: how much was attempted, how much came
     * right, and which concepts the session touched.
     */
    @GET
    @Path("/workspaces/{id}/sessions")
    public List<KnowledgeWorkbench.LearningSession> sessions(@PathParam("id") String workspaceId) {
        return workbench.learningSessions(workspaceId);
    }

    /**
     * The synthetic typo benchmark: the intent preservation.
     *
     * <p>The mission's §14 names the row: "≥ 95 % intent preservation on a
     * synthetic typo benchmark". The cases are synthetic — the misspellings
     * are generated from the workspace's own concept names by a fixed seed
     * — and every case carries the question that was MEANT, so the
     * measurement is about the intent and not about the spelling.
     */
    @GET
    @Path("/workspaces/{id}/typo-benchmark")
    public Map<String, Object> typoBenchmark(@PathParam("id") String workspaceId) {
        io.matrix.knowledge.benchmark.TypoBenchmarkGenerator.Benchmark benchmark =
                io.matrix.knowledge.benchmark.TypoBenchmarkRunner.benchmark(workbench, workspaceId);
        io.matrix.knowledge.benchmark.TypoBenchmarkGenerator.Result result =
                io.matrix.knowledge.benchmark.TypoBenchmarkRunner.run(workbench, workspaceId,
                        benchmark);
        return io.matrix.knowledge.benchmark.TypoBenchmarkRunner.asMap(result, benchmark);
    }

    /**
     * The knowledge graph of a workspace, as a projection.
     *
     * <p>The decision D-003 of the reconnaissance: the graph store is a
     * read-only PROJECTION of the workspace, not a second copy of the
     * knowledge. The projection is built from the snapshot and thrown
     * away, so there is no fourth knowledge system that could drift from
     * the first.
     */
    @GET
    @Path("/workspaces/{id}/graph")
    public Map<String, Object> graph(@PathParam("id") String workspaceId) {
        KnowledgeGraphStore graph =
                KnowledgeGraphProjector.project(workbench.workspace(workspaceId));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workspace", workspaceId);
        body.put("theEntities", graph.entityCount());
        body.put("theRelations", graph.relationCount());
        body.put("theCentral", graph.topCentral(10));
        List<Map<String, Object>> entities = new ArrayList<>();
        for (KnowledgeGraphStore.Entity entity : graph.entities()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("theId", entity.id());
            entry.put("theType", entity.type());
            entry.put("theProperties", entity.properties());
            entities.add(entry);
        }
        body.put("theEntityList", entities);
        List<Map<String, Object>> relations = new ArrayList<>();
        for (KnowledgeGraphStore.Relation relation : graph.allRelations()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("theFrom", relation.fromId());
            entry.put("theTo", relation.toId());
            entry.put("thePredicate", relation.predicate());
            entry.put("theWeight", relation.weight());
            relations.add(entry);
        }
        body.put("theRelationList", relations);
        body.put("theNote", "This graph is a read-only projection of the workspace: the concepts, "
                + "the claims and the sources, with the relations between them. It holds no state "
                + "of its own. The weight of a relation is the claim's own authored confidence - "
                + "not a probability, and the support score remains uncalibrated.");
        return body;
    }

    /**
     * The corpus registry of a workspace.
     *
     * <p>The mission's §5.5 asks for a corpus manager: the record of what
     * the workspace knows, where it came from, and how much of it is
     * independent. The independent confirmations are counted by the
     * distinct content hashes of the sources behind a claim - two
     * documents with the same content are one source, and ten copies of
     * one source are one confirmation.
     */
    @GET
    @Path("/workspaces/{id}/corpus")
    public Map<String, Object> corpus(@PathParam("id") String workspaceId) {
        io.matrix.knowledge.acquisition.CorpusManager.Registry registry =
                io.matrix.knowledge.acquisition.CorpusManager.registry(workbench, workspaceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workspace", workspaceId);
        body.put("theSources", registry.sources());
        body.put("theImported", registry.imported());
        body.put("theAuthored", registry.authored());
        body.put("theDocuments", registry.documents());
        body.put("theClaims", registry.claims());
        body.put("theIndependentConfirmations", registry.independentConfirmations());
        body.put("theNotes", registry.notes());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (io.matrix.knowledge.acquisition.CorpusManager.Entry entry : registry.entries()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("theSource", entry.sourceId());
            item.put("theKind", entry.kind().name());
            item.put("theTitle", entry.title());
            item.put("theLocator", entry.locator());
            item.put("theContentHash", entry.contentHash());
            item.put("theLanguage", entry.language());
            item.put("theFormat", entry.format());
            item.put("theClaimsSupported", entry.claimsSupported());
            item.put("theIndependentConfirmations", entry.independentConfirmations());
            item.put("theImported", entry.imported());
            item.put("theNote", entry.note());
            entries.add(item);
        }
        body.put("theRegistry", entries);
        // THE VERSIONS, GROUPED BY TITLE.
        //
        // The workspace can now say how many versions of a title it holds
        // and which one carries the latest authored publication date.
        List<Map<String, Object>> titles = new ArrayList<>();
        for (io.matrix.knowledge.acquisition.CorpusManager.TitleGroup group : registry.titles()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("theTitle", group.title());
            item.put("theVersions", group.versionCount());
            item.put("theLatestPublishedAt", group.latestPublishedAt());
            item.put("theLatestSource", group.latestSourceId());
            item.put("theNote", group.note());
            List<Map<String, Object>> versions = new ArrayList<>();
            for (io.matrix.knowledge.acquisition.CorpusManager.Entry version : group.versions()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("theSource", version.sourceId());
                entry.put("theContentHash", version.contentHash());
                entry.put("theFormat", version.format());
                entry.put("theClaimsSupported", version.claimsSupported());
                entry.put("theImported", version.imported());
                versions.add(entry);
            }
            item.put("theVersionList", versions);
            titles.add(item);
        }
        body.put("theTitles", titles);
        return body;
    }

    /**
     * Validates a workspace: the claims that several INDEPENDENT sources
     * state are promoted, and the duplicates are superseded.
     *
     * <p>The mission's §5.5: "Ten documents copying the same source do not
     * constitute ten independent confirmations." The independence is
     * counted by the distinct content hashes of the sources behind the
     * claim, not by the number of files.
     */
    @POST
    @Path("/workspaces/{id}/validate")
    public Map<String, Object> validate(@PathParam("id") String workspaceId) {
        io.matrix.knowledge.acquisition.ClaimValidator.Result result =
                workbench.validate(workspaceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workspace", workspaceId);
        body.put("theGroups", result.groups());
        body.put("thePromoted", result.promoted());
        body.put("theSuperseded", result.superseded());
        body.put("theRefusals", result.refusals());
        List<Map<String, Object>> promotions = new ArrayList<>();
        for (io.matrix.knowledge.acquisition.ClaimValidator.Promotion promotion
                : result.promotions()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("theClaim", promotion.claimId());
            entry.put("theProposition", promotion.proposition());
            entry.put("from", promotion.from().name());
            entry.put("to", promotion.to().name());
            entry.put("theIndependentSources", promotion.independentSources());
            entry.put("theReason", promotion.reason());
            promotions.add(entry);
        }
        body.put("thePromotions", promotions);
        return body;
    }

    /**
     * The verified learning errors of a workspace.
     *
     * <p>The mission's §9: the error-driven integration happens only after
     * the error is verified. An error the learner has made in one session
     * may be a slip, and it is not acted upon; an error the learner has
     * repeated in two sessions, and has not since answered correctly, is
     * a gap, and its items are what the selector prefers.
     */
    @POST
    @Path("/workspaces/{id}/integrate-errors")
    public Map<String, Object> integrateErrors(@PathParam("id") String workspaceId) {
        ErrorIntegrator.Result result = ErrorIntegrator.integrate(workbench, workspaceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workspace", workspaceId);
        body.put("theCandidates", result.candidates());
        body.put("theVerified", result.verified());
        body.put("theRecorded", result.recorded());
        body.put("theUnverified", result.unverified());
        List<Map<String, Object>> errors = new ArrayList<>();
        for (LearningError error : result.errors()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("item", error.itemId());
            entry.put("concept", error.conceptId());
            entry.put("dimension", error.dimension().name());
            entry.put("theLearnersAnswer", error.learnerAnswer());
            entry.put("theExpectedAnswer", error.expectedAnswer());
            entry.put("verified", error.verified());
            errors.add(entry);
        }
        body.put("theErrors", errors);
        return body;
    }

    /**
     * The next exercise for the learner.
     *
     * <p>The mission's §10.1 asks for an exercise generator and a learner
     * practice loop. RECON-W41 had neither: the caller had to supply the
     * item id. The selection comes from the learner's persisted state and
     * the workspace's gaps, and the item is one the dataset holds — the
     * selector never writes a question.
     */
    @GET
    @Path("/workspaces/{id}/exercises/next")
    public Map<String, Object> nextExercise(@PathParam("id") String workspaceId) {
        ExerciseSelector.Selection selection = workbench.nextExercise(workspaceId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (selection == null) {
            result.put("selection", null);
            result.put("reason", "there is nothing left to practise in this workspace");
            return result;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", selection.item().id());
        item.put("prompt", selection.item().prompt());
        item.put("dimension", selection.item().dimension().name());
        item.put("concept", selection.conceptName());
        item.put("difficulty", selection.item().difficulty());
        item.put("heldOut", selection.heldOut());
        item.put("alternatives", selection.item().alternatives().size());
        result.put("selection", item);
        result.put("reason", selection.reason());
        return result;
    }

    /**
     * The exercises for the next session.
     */
    @GET
    @Path("/workspaces/{id}/exercises")
    public List<ExerciseSelector.Selection> exercises(@PathParam("id") String workspaceId,
                                                      @QueryParam("count") Integer count) {
        int howMany = count == null || count < 1 ? 5 : Math.min(count, 50);
        return workbench.nextExercises(workspaceId, howMany);
    }

    /**
     * The next bounded learning action for every active goal.
     *
     * <p>The mission's §4.6: the planning subsystem inspects the active
     * goals, identifies the missing prerequisites and the knowledge gaps,
     * estimates the cost and selects an appropriate strategy. Until this
     * wave, nothing selected anything.
     */
    @GET
    @Path("/workspaces/{id}/plan")
    public List<LearningPlanner.Plan> plan(@PathParam("id") String workspaceId) {
        return workbench.plan(workspaceId);
    }

    /** Starts a goal. */
    @POST
    @Path("/workspaces/{id}/goals")
    public Response startGoal(@PathParam("id") String workspaceId, GoalRequest request) {
        if (request == null || request.description() == null || request.description().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "a description is required"))
                    .build();
        }
        List<LearningGoal.Subtask> subtasks = new ArrayList<>();
        if (request.subtasks() != null) {
            for (SubtaskRequest subtask : request.subtasks()) {
                subtasks.add(new LearningGoal.Subtask(
                        subtask.id(), subtask.description(),
                        subtask.required() == null || subtask.required(),
                        subtask.prerequisiteSubtaskIds() == null
                                ? List.of() : subtask.prerequisiteSubtaskIds(),
                        LearningGoal.Status.PENDING, List.of(), ""));
            }
        }
        LearningGoal goal = workbench.startGoal(workspaceId, request.description(),
                request.successCriteria() == null ? List.of() : request.successCriteria(),
                request.prerequisiteConceptIds() == null ? List.of() : request.prerequisiteConceptIds(),
                subtasks, request.budgetUnits() == null ? 10 : request.budgetUnits(),
                request.recoveryInstructions() == null
                        ? "reopen the workspace and continue with the first open subtask"
                        : request.recoveryInstructions());
        return Response.status(Response.Status.CREATED).entity(goal).build();
    }

    /** Records that a subtask was completed. */
    @POST
    @Path("/workspaces/{id}/goals/{goalId}/subtasks/{subtaskId}/complete")
    public Response completeSubtask(@PathParam("id") String workspaceId,
                                     @PathParam("goalId") String goalId,
                                     @PathParam("subtaskId") String subtaskId,
                                     SubtaskCompletion request) {
        List<String> evidence = request == null || request.evidenceIds() == null
                ? List.of() : request.evidenceIds();
        String note = request == null ? "" : request.note();
        return Response.ok(workbench.completeSubtask(workspaceId, goalId, subtaskId, evidence, note))
                .build();
    }

    /** Writes a checkpoint for a goal. */
    @POST
    @Path("/workspaces/{id}/goals/{goalId}/checkpoint")
    public Response checkpointGoal(@PathParam("id") String workspaceId,
                                   @PathParam("goalId") String goalId,
                                   CheckpointRequest request) {
        String checkpoint = request == null ? "" : request.checkpoint();
        String remaining = request == null ? "" : request.remainingWork();
        return Response.ok(workbench.checkpointGoal(workspaceId, goalId, checkpoint, remaining)).build();
    }

    /**
     * The rebuilt teach-back experiment.
     *
     * <p>Four phases over disjoint item sets, an independent closed-book
     * learner, a leakage audit and a calibration measurement.
     */
    @POST
    @Path("/workspaces/{id}/teach-back-experiment")
    public Response teachBackExperiment(@PathParam("id") String workspaceId,
                                         TeachBackExperimentRequest request) {
        if (request == null || request.conceptId() == null || request.conceptId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "a concept is required"))
                    .build();
        }
        double expectedGain = request.expectedGain() == null ? 0.2 : request.expectedGain();
        return Response.ok(workbench.teachBackExperiment(workspaceId, request.conceptId(), expectedGain))
                .build();
    }

    /** What the workspace does not know. */
    @GET
    @Path("/workspaces/{id}/gaps")
    public Map<String, Object> gaps(@PathParam("id") String workspaceId, @QueryParam("question") String question) {
        KnowledgeGapDetector detector = workbench.gapDetector(workspaceId);
        Map<String, Object> gaps = new LinkedHashMap<>();
        if (question != null && !question.isBlank()) {
            gaps.put("uncoveredTerms", detector.uncoveredTerms(question));
        }
        gaps.put("conceptsWithoutEstablishedClaims",
                detector.conceptsWithoutEstablishedClaims().stream().map(Concept::name).toList());
        Map<String, Object> result = new LinkedHashMap<>(gaps);
        result.put("knownConcepts", detector.snapshot().concepts().size());
        return result;
    }

    /** The assessment items of a workspace. */
    @GET
    @Path("/workspaces/{id}/assessment")
    public Map<String, Object> assessment(@PathParam("id") String workspaceId) {
        AssessmentDataset dataset = workbench.dataset(workspaceId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetId", dataset.id());
        result.put("version", dataset.version());
        result.put("description", dataset.description());
        result.put("items", dataset.items());
        return result;
    }

    /** Grade one answer and record the outcome. */
    @POST
    @Path("/workspaces/{id}/assess")
    public Response assess(@PathParam("id") String workspaceId, AssessRequest request) {
        if (request == null || request.itemId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "an item id is required"))
                    .build();
        }
        String answer = request.answer() == null ? "" : request.answer();
        double confidence = request.confidence() == null ? 0.0 : request.confidence();
        try {
            AssessmentOutcome outcome = workbench.assess(workspaceId, request.itemId(), answer,
                    confidence, request.sessionId());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("outcome", outcome);
            result.put("learningStates", workbench.learningStates(workspaceId));
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /** The learning state of every concept and dimension. */
    @GET
    @Path("/workspaces/{id}/learning-state")
    public Map<String, Object> learningState(@PathParam("id") String workspaceId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("states", workbench.learningStates(workspaceId));
        result.put("outcomeCount", workbench.outcomes(workspaceId).size());
        result.put("dimensions", List.of(LearningDimension.values()));
        result.put("stages", List.of(LearningStage.values()));
        return result;
    }

    /**
     * Every change of a capability's state, with the outcome that caused it.
     *
     * <p>The mission's §7: the lifecycle is revisable, a regression must be
     * observable and explained, and earlier mastery records are preserved.
     */
    @GET
    @Path("/workspaces/{id}/learning-transitions")
    public List<LearningTransition> learningTransitions(@PathParam("id") String workspaceId) {
        return workbench.learningTransitions(workspaceId);
    }

    /** The recorded assessment outcomes. */
    @GET
    @Path("/workspaces/{id}/outcomes")
    public List<AssessmentOutcome> outcomes(@PathParam("id") String workspaceId) {
        return workbench.outcomes(workspaceId);
    }

    /** Export the workspace as a knowledge package. */
    @GET
    @Path("/workspaces/{id}/export")
    public Map<String, Object> exportPackage(@PathParam("id") String workspaceId) {
        KnowledgeSnapshot snapshot = workbench.workspace(workspaceId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspace", snapshot.workspaceId());
        result.put("name", snapshot.workspaceName());
        result.put("exportedAt", snapshot.exportedAt());
        result.put("concepts", snapshot.concepts());
        result.put("sources", snapshot.sources());
        result.put("claims", snapshot.claims());
        result.put("evidence", snapshot.evidence());
        result.put("representations", snapshot.representations());
        result.put("prerequisites", snapshot.prerequisites());
        result.put("revisions", snapshot.revisions());
        return result;
    }

    /**
     * Hand the knowledge of this workspace to another one.
     *
     * <p>The identifiers are regenerated for the receiving workspace, and the
     * learner's history stays behind: the knowledge travels, the evidence
     * that a learner demonstrated it does not.
     */
    @POST
    @Path("/workspaces/{id}/transfer")
    public Response transfer(@PathParam("id") String workspaceId,
                             @QueryParam("to") String toWorkspace) {
        if (toWorkspace == null || toWorkspace.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "a receiving workspace id is required"))
                    .build();
        }
        try {
            return Response.ok(workbench.transferKnowledge(workspaceId, toWorkspace)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Where the learner stands on the ladder of §9 for every assessed
     * concept.
     */
    @GET
    @Path("/workspaces/{id}/capability")
    public Response capabilityStates(@PathParam("id") String workspaceId) {
        return Response.ok(workbench.capabilityStates(workspaceId)).build();
    }

    /**
     * Where the learner stands on the ladder for one concept, with the
     * teach-back when it is asked for.
     *
     * <p>The teach-back is the evidence the highest stage needs: without it,
     * a learner can be understood but not yet teachable.
     */
    @GET
    @Path("/workspaces/{id}/capability/{conceptId}")
    public Response capabilityState(@PathParam("id") String workspaceId,
                                    @PathParam("conceptId") String conceptId,
                                    @QueryParam("teachBack") boolean teachBack) {
        return Response.ok(workbench.capabilityState(workspaceId, conceptId, teachBack)).build();
    }

    /**
     * Register what the system has learned about the learner as knowledge.
     *
     * <p>The rule the acceptance model induced becomes a claim with a source
     * and evidence, in the same store as everything else, so that it can be
     * read and later superseded.
     */
    @POST
    @Path("/workspaces/{id}/learned-rule")
    public Response registerLearnedRule(@PathParam("id") String workspaceId,
                                        @QueryParam("conceptId") String conceptId) {
        if (conceptId == null || conceptId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "a concept id is required"))
                    .build();
        }
        return Response.ok(workbench.registerLearnedRule(workspaceId, conceptId)).build();
    }

    /**
     * The metrics of this workspace.
     *
     * <p>The report carries the dataset version, the software version, the
     * test procedure and the sample size of every number, and it ends with
     * the list of the metrics that were NOT measured. There is no single
     * intelligence score: a single score would hide the differences that
     * matter.
     */
    @GET
    @Path("/workspaces/{id}/metrics")
    public Response metrics(@PathParam("id") String workspaceId) {
        return Response.ok(workbench.metrics(workspaceId).asMap()).build();
    }

    /**
     * The questions the workspace was asked and could not answer.
     *
     * <p>Two wordings of the same question collapse onto one record, so the
     * count is a count of questions asked, not of wordings used.
     */
    @GET
    @Path("/workspaces/{id}/questions")
    public Response questions(@PathParam("id") String workspaceId) {
        return Response.ok(workbench.askedQuestions(workspaceId)).build();
    }

    /**
     * Consolidate the episodic log of asked questions.
     */
    @POST
    @Path("/workspaces/{id}/questions/consolidate")
    public Response consolidateQuestions(@PathParam("id") String workspaceId) {
        return Response.ok(workbench.consolidateQuestions(workspaceId)).build();
    }

    /**
     * The supervised learner over the learner's own answers.
     *
     * <p>The measurement compares the learned rule with the deterministic
     * grader on the held-out items, and says which is in the production
     * path. The grader is: it is exact and needs no training.
     */
    @GET
    @Path("/workspaces/{id}/acceptance-model")
    public Response acceptanceModel(@PathParam("id") String workspaceId) {
        return Response.ok(workbench.acceptanceModel(workspaceId)).build();
    }

    /**
     * The teaching plan for a concept.
     *
     * <p>The plan is composed from the authored representations only; it
     * never sees the held-out assessment items that will measure it.
     */
    @GET
    @Path("/workspaces/{id}/teaching-plan/{conceptId}")
    public Response teachingPlan(@PathParam("id") String workspaceId,
                                  @PathParam("conceptId") String conceptId,
                                  @QueryParam("level") String level) {
        try {
            TeachingPlan plan = workbench.teachingPlan(workspaceId, conceptId, parseLevel(level));
            return Response.ok(plan).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Run the teaching experiment.
     *
     * <p>A second learner is taught the plan and is then measured on the
     * held-out items, which the teacher never saw. The expectation is
     * declared in the request, and the result says whether it was met.
     *
     * <p>The learner is a synthetic one, so this is a machine-to-machine
     * result and not evidence of human teaching effectiveness.
     */
    @POST
    @Path("/workspaces/{id}/teach-back")
    public Response teachBack(@PathParam("id") String workspaceId, TeachBackRequest request) {
        if (request == null || request.conceptId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "a concept id is required"))
                    .build();
        }
        double expectedGain = request.expectedGain() == null ? 0.0 : request.expectedGain();
        try {
            TeachBackEvaluator.TeachBackResult result = workbench.teachBack(
                    workspaceId, request.conceptId(), parseLevel(request.level()), expectedGain);
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Apply a correction to what is known.
     *
     * <p>The earlier claim is retained and marked superseded, the corrected
     * claim carries its source, and the revision records what changed and
     * why. Nothing is deleted.
     */
    @POST
    @Path("/workspaces/{id}/refine")
    public Response refine(@PathParam("id") String workspaceId, RefineRequest request) {
        if (request == null || request.conceptId() == null || request.proposition() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "a concept id and a proposition are required"))
                    .build();
        }
        try {
            KnowledgeRefiner.Refinement refinement = new KnowledgeRefiner.Refinement(
                    request.conceptId(),
                    request.supersededClaimId(),
                    request.proposition(),
                    request.scope(),
                    request.context(),
                    parseStatus(request.status()),
                    request.confidence() == null ? 0.5 : request.confidence(),
                    parseSourceKind(request.sourceKind()),
                    request.sourceTitle() == null ? "A human statement" : request.sourceTitle(),
                    request.sourceLocator(),
                    request.sourceExcerpt(),
                    request.sourceAuthority() == null ? 50 : request.sourceAuthority(),
                    request.sourceFreshness() == null ? 50 : request.sourceFreshness(),
                    parseRevisionKind(request.revisionKind()),
                    request.summary() == null ? "The claim was corrected." : request.summary(),
                    request.addedFacts(), request.stillValidAssumptions(),
                    request.noLongerValidAssumptions(), request.newlyExplainedCases(),
                    request.refinedRepresentation(), parseLevel(request.refinedRepresentationLevel()));
            KnowledgeChanges changes = workbench.refine(workspaceId, refinement);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("claims", changes.claims());
            result.put("sources", changes.sources());
            result.put("evidence", changes.evidence());
            result.put("representations", changes.representations());
            result.put("revisions", changes.revisions());
            return Response.ok(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    private static ClaimStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return ClaimStatus.SUPPORTED;
        }
        try {
            return ClaimStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ClaimStatus.SUPPORTED;
        }
    }

    private static SourceKind parseSourceKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return SourceKind.HUMAN_ASSERTION;
        }
        try {
            return SourceKind.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SourceKind.HUMAN_ASSERTION;
        }
    }

    private static RevisionKind parseRevisionKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return RevisionKind.CORRECTION;
        }
        try {
            return RevisionKind.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RevisionKind.CORRECTION;
        }
    }

    private static AbstractionLevel parseLevel(String raw) {        if (raw == null || raw.isBlank()) {
            return AbstractionLevel.INTRODUCTORY;
        }
        try {
            return AbstractionLevel.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AbstractionLevel.INTRODUCTORY;
        }
    }
}
