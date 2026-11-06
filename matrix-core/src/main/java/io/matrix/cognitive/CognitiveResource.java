package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.knowledge.Case;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Evidence;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W44 — the HTTP surface of the cognitive kernel.
 *
 * <p>The kernel is not a second application: it is reachable from the SAME Quarkus process that
 * serves the workbench, on a path beside it, and it writes into the SAME knowledge store. The
 * workbench's Epistemic panel and this endpoint therefore show the same claims, the same
 * evidence and the same cases.</p>
 *
 * <p>What the endpoint exposes is the cognitive trace: what was perceived, what was predicted,
 * which computation was chosen and why, what was observed, what changed, and where the system
 * refused. A caller can see WHY an answer was reached, not only WHAT it was.</p>
 */
@Path("/v1/cognitive")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CognitiveResource {

    @Inject
    CognitiveService service;

    /**
     * The body of an observation request.
     *
     * <p>The idempotency key is OPTIONAL and its name is part of the contract: a caller that
     * sends {@code idempotencyKey} names ONE logical request, and the endpoint will recognise
     * a repeated delivery of it. A caller that sends nothing gets a new request every time,
     * even when the assignment is byte-identical to an earlier one.</p>
     *
     * @param assignment     the assignment to reason about
     * @param idempotencyKey the key that names the logical request, or null
     */
    public record ObserveRequest(String assignment, String idempotencyKey) {

        /** A request that does not name itself. */
        public ObserveRequest(String assignment) {
            this(assignment, null);
        }
    }

    /**
     * Runs one cognitive cycle.
     *
     * <p>Without an idempotency key every call is a new request. With one, a repeated delivery
     * of the same key and the same assignment returns the ORIGINAL outcome and does no new
     * work, and a repeated delivery of the same key with a DIFFERENT assignment is a conflict:
     * {@code 409 Conflict}, with the first outcome left untouched.</p>
     *
     * @param request the assignment to reason about
     * @return the outcome, with the full trace
     */
    @POST
    @Path("/observe")
    public Response observe(ObserveRequest request) {
        if (request == null || request.assignment() == null || request.assignment().isBlank()) {
            return Response.status(400).entity(Map.of("error",
                    "the request needs an assignment, for example power=1,switch=0")).build();
        }
        try {
            CognitiveOutcome outcome = service.observe(request.assignment(), request.idempotencyKey());
            Response.Status status = switch (outcome.status()) {
                case COMPLETED -> Response.Status.OK;
                case REFUSED -> Response.Status.CONFLICT;
                // W45.1: a timeout and a controlled failure are TERMINAL answers, not normal
                // ones. The outcome is durable and replayable: the same key returns the same
                // terminal outcome and does no work.
                case RESOURCE_LIMITED -> Response.Status.SERVICE_UNAVAILABLE;
                case FAILED -> Response.Status.INTERNAL_SERVER_ERROR;
            };
            return Response.status(status).entity(describe(outcome)).build();
        } catch (CognitiveConflictException e) {
            // The request conflicts with the record: the same idempotency key was delivered
            // with a different assignment, or a fact of an episode was about to be rewritten.
            // Nothing is overwritten, and the caller is told what happened.
            return Response.status(409).entity(Map.of("error", e.getMessage())).build();
        } catch (CognitiveRequestFailure e) {
            // W45.1: the request was interrupted after it had committed durable work, so its
            // outcome cannot be established. The caller must NOT retry the same key blindly:
            // the effect may already have happened.
            return Response.status(409).entity(Map.of("error", e.getMessage(),
                    "state", e.kind().name())).build();
        }
    }

    /** The body of a solve request. */
    public record SolveRequest(
            int vertexCount,
            List<CognitiveSolver.EdgeSpec> edges,
            int source,
            int target,
            Boolean allowBounded) {
    }

    /**
     * Solves a problem with the algorithm portfolio.
     *
     * <p>The method is chosen by the selector from the preconditions that hold for the request
     * and from the measured costs, and the decision — including every rejected candidate and
     * the reason — is returned with the answer.</p>
     *
     * @param request the problem
     * @return the answer and the decision that chose the method
     */
    @POST
    @Path("/solve")
    public Response solve(SolveRequest request) {
        if (request == null || request.vertexCount() <= 0) {
            return Response.status(400).entity(Map.of("error",
                    "the request needs a graph with a vertex count")).build();
        }
        CognitiveSolver.SolveOutcome outcome = service.solver().solveGraph(
                new CognitiveSolver.GraphRequest(request.vertexCount(), request.edges(),
                        request.source(), request.target()),
                CognitiveSolver.costWeightOf(null),
                request.allowBounded() != null && request.allowBounded());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("family", outcome.family());
        body.put("selectedAlgorithmId", outcome.selectedAlgorithmId());
        body.put("answer", outcome.answer());
        body.put("decision", outcome.decision());
        body.put("notes", outcome.notes());
        return Response.status(outcome.decision() == null
                || outcome.decision().status() == AlgorithmDecision.Status.REFUSED ? 409 : 200)
                .entity(body).build();
    }

    /** What the kernel currently holds. */
    @GET
    @Path("/summary")
    public Map<String, Object> summary() {
        return service.kernel().summary();
    }

    /** The predictions the kernel made, with the basis each one names. */
    @GET
    @Path("/predictions")
    public List<Prediction> predictions() {
        return service.store().predictions(CognitiveKernel.WORKSPACE_ID);
    }

    /** The prediction errors the kernel recorded. */
    @GET
    @Path("/errors")
    public List<ErrorSignal> errors() {
        return service.store().errors(CognitiveKernel.WORKSPACE_ID);
    }

    /** The procedures the kernel promoted. */
    @GET
    @Path("/procedures")
    public List<Procedure> procedures() {
        return service.store().procedures(CognitiveKernel.WORKSPACE_ID);
    }

    /** The internal tasks the kernel generated for itself. */
    @GET
    @Path("/tasks")
    public List<CognitiveTask> tasks() {
        return service.store().tasks(CognitiveKernel.WORKSPACE_ID);
    }

    /** The claims the cognitive model carries. */
    @GET
    @Path("/claims")
    public List<Claim> claims() {
        return service.kernel().modelClaims();
    }

    /** The evidence recorded on the cognitive model. */
    @GET
    @Path("/evidence")
    public List<Evidence> evidence() {
        return service.kernel().modelEvidence();
    }

    /** The cases — the observations with their conditions — that the model was built from. */
    @GET
    @Path("/cases")
    public List<Case> cases() {
        return service.kernel().modelCases();
    }

    /**
     * The trace of one episode.
     *
     * @param episodeId the episode
     * @return the steps, in order
     */
    @GET
    @Path("/trace/{episodeId}")
    public List<LearningTrace.Step> trace(@PathParam("episodeId") String episodeId) {
        return service.store().events(episodeId);
    }

    private Map<String, Object> describe(CognitiveOutcome outcome) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("episodeId", outcome.episodeId());
        out.put("status", outcome.status().name());
        out.put("answer", outcome.answer());
        out.put("channel", outcome.percept() == null ? null : outcome.percept().channel().name());
        out.put("malformed", outcome.percept() != null && outcome.percept().malformed());
        out.put("prediction", outcome.prediction());
        out.put("error", outcome.error());
        out.put("decision", outcome.decision());
        out.put("trace", outcome.trace().steps());
        out.put("notes", outcome.notes());
        return out;
    }
}
