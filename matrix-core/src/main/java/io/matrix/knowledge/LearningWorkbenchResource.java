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
                DocumentImporter.ImportResult result = new DocumentImporter(workbench.store())
                        .importDocument(workspaceId, temporary, filename,
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
                return Response.ok(report).build();
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
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
