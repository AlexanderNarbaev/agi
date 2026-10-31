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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        if (!KnowledgeWorkbench.DEFAULT_WORKSPACE.equals(workspaceId)) {
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
                "revisions", snapshot.revisions().size())).build();
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
