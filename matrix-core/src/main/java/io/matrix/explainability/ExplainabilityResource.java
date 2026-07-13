package io.matrix.explainability;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST API endpoint for neuron decision explainability.
 *
 * <p>Provides access to full decision explanations, counterfactual analysis,
 * and audit log queries.
 *
 * <p>Ref: arXiv:2605.11595 — "Native Explainability for BCPNN"
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /api/v1/explain/{decisionId}} — full explanation</li>
 *   <li>{@code GET /api/v1/explain/{decisionId}/counterfactual} — what-if analysis</li>
 *   <li>{@code GET /api/v1/explain/audit} — audit log query</li>
 * </ul>
 */
@Path("/api/v1/explain")
@Produces(MediaType.APPLICATION_JSON)
public class ExplainabilityResource {

    @Inject
    AuditLogger auditLogger;

    /**
     * Returns the full explanation for a decision.
     *
     * @param decisionId the unique decision identifier
     * @return the decision provenance with all explanation results
     */
    @GET
    @Path("/{decisionId}")
    public Uni<DecisionProvenance> getExplanation(@PathParam("decisionId") String decisionId) {
        return auditLogger.findById(decisionId)
                .onItem().ifNull().failWith(() ->
                        new NotFoundException("Decision not found: " + decisionId));
    }

    /**
     * Returns counterfactual (what-if) analysis for a decision.
     *
     * <p>Re-generates the what-if analysis from the stored provenance,
     * focusing on the counterfactual primitives (P11-P13).
     *
     * @param decisionId the unique decision identifier
     * @return map of counterfactual scenarios and their outcomes
     */
    @GET
    @Path("/{decisionId}/counterfactual")
    public Uni<Map<String, Object>> getCounterfactual(
            @PathParam("decisionId") String decisionId) {
        return auditLogger.findById(decisionId)
                .onItem().ifNull().failWith(() ->
                        new NotFoundException("Decision not found: " + decisionId))
                .onItem().ifNotNull().transform(provenance -> {
                    Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("decisionId", provenance.decisionId());
                    result.put("neuronId", provenance.neuronId());
                    result.put("originalOutput", provenance.output());
                    result.put("explanations", provenance.explanationResults());
                    result.put("primitives", provenance.explanationPrimitives().stream()
                            .map(ExplanationPrimitive::name)
                            .toList());
                    return result;
                });
    }

    /**
     * Queries the audit log with optional filters.
     *
     * @param neuronId optional neuron ID filter
     * @param from     optional start timestamp (ISO-8601)
     * @param to       optional end timestamp (ISO-8601)
     * @param output   optional output value filter (true/false)
     * @param limit    maximum results (default 50, max 500)
     * @return list of matching provenance records
     */
    @GET
    @Path("/audit")
    public Uni<List<DecisionProvenance>> queryAuditLog(
            @QueryParam("neuronId") String neuronId,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("output") Boolean output,
            @QueryParam("limit") Integer limit) {

        int effectiveLimit = (limit != null) ? Math.min(limit, 500) : 50;

        if (neuronId != null && !neuronId.isBlank()) {
            return auditLogger.findByNeuronId(neuronId, effectiveLimit);
        }

        if (from != null && to != null) {
            Instant fromTime = Instant.parse(from);
            Instant toTime = Instant.parse(to);
            return auditLogger.findByTimeRange(fromTime, toTime, effectiveLimit);
        }

        if (output != null) {
            return auditLogger.findByOutput(output, effectiveLimit);
        }

        // Default: return recent entries by time range (last 24h)
        Instant now = Instant.now();
        Instant dayAgo = now.minus(java.time.Duration.ofHours(24));
        return auditLogger.findByTimeRange(dayAgo, now, effectiveLimit);
    }

    /**
     * Health check for the explainability subsystem.
     */
    @GET
    @Path("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "component", "explainability",
                "primitives", ExplanationPrimitive.values().length,
                "categories", ExplanationPrimitive.Category.values().length
        );
    }
}
