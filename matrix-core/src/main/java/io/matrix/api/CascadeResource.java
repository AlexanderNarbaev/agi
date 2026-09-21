package io.matrix.api;

import io.matrix.privacy.CascadeRegistrar;
import io.matrix.privacy.CascadeTombstoneService;
import io.matrix.privacy.MockNoosphere;
import io.matrix.privacy.Tombstone;
import io.matrix.privacy.TombstoneService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * HTTP/REST endpoint for GDPR cascade erasure — extends TombstoneService
 * with the standard {@link CascadeRegistrar} rules wired through a
 * {@link MockNoosphere}.
 *
 * <p>Endpoints:
 * <pre>
 *   POST /api/v1/privacy/cascade
 *        body: { "subjectId": "...", "sourceType": "Agent", "sourceId": "agent-7", "reason": "gdpr.erasure" }
 *        — Tombstone a resource + cascade to all dependents
 *
 *   GET  /api/v1/privacy/cascade/noosphere
 *        — Noosphere stats (entity counts)
 *
 *   GET  /api/v1/privacy/cascade/dependents/{type}/{id}
 *        — List dependents of a given resource (without tombstoning)
 * </pre>
 *
 * <p>For production, replace the in-memory {@link MockNoosphere} with a real
 * {@code NoosphereRegistry} wired through REST. For tests / dev, the mock
 * is the canonical implementation.
 *
 * <p>Ref: L6 §6.7, Wave 24, Wave 28.
 */
@Path("/api/v1/privacy/cascade")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CascadeResource {

    private final TombstoneService tombstones;
    private final MockNoosphere noosphere;
    private final CascadeTombstoneService cascade;

    @Inject
    public CascadeResource() {
        this(new TombstoneService(), new MockNoosphere());
    }

    /** Test-injectable constructor. */
    public CascadeResource(TombstoneService tombstones, MockNoosphere noosphere) {
        this.tombstones = tombstones;
        this.noosphere = noosphere;
        this.cascade = CascadeRegistrar.registerAll(tombstones, new CascadeRegistrar.Resolvers(
                (id, d) -> noosphere.neuronsForAgent(id),
                (id, d) -> noosphere.snapshotsForAgent(id),
                (id, d) -> noosphere.truthTableForNeuron(id),
                (id, d) -> noosphere.knowledgeIndexForPackage(id)));
    }

    /** Convenience for tests. */
    public CascadeResource(TombstoneService tombstones, MockNoosphere noosphere, CascadeTombstoneService cascade) {
        this.tombstones = tombstones;
        this.noosphere = noosphere;
        this.cascade = cascade;
    }

    public MockNoosphere noosphere() { return noosphere; }

    // ── Cascade erasure ──

    @POST
    public Response cascadeErase(@Valid CascadeRequest req) {
        if (req == null || req.subjectId == null || req.sourceType == null
                || req.sourceId == null || req.reason == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("subjectId, sourceType, sourceId, reason are required"))
                    .build();
        }
        String requesterId = req.requesterId == null || req.requesterId.isBlank()
                ? "CascadeResource" : req.requesterId;
        List<Tombstone> tombstones = cascade.tombstoneAndCascade(
                req.subjectId, req.sourceType, req.sourceId, req.reason, requesterId);
        return Response.ok(new CascadeResponse(
                tombstones.size(),
                req.sourceType, req.sourceId,
                cascade.cascadeCount(),
                tombstones)).build();
    }

    // ── Noosphere stats ──

    @GET
    @Path("/noosphere")
    public Response noosphereStats() {
        return Response.ok(new NoosphereStatsResponse(
                noosphere.totalEntities(),
                noosphere.countByType())).build();
    }

    // ── Dependency lookup (read-only) ──

    @GET
    @Path("/dependents/{type}/{id}")
    public Response dependents(@PathParam("type") String type,
                                @PathParam("id") String id) {
        if (type == null || id == null) {
            return Response.status(400).entity(new ErrorResponse("type and id required")).build();
        }
        List<String> deps = switch (type) {
            case "Agent" -> {
                // For Agent, both neurons AND snapshots are dependents.
                var ns = noosphere.neuronsForAgent(id);
                var ss = noosphere.snapshotsForAgent(id);
                var combined = new java.util.ArrayList<>(ns);
                combined.addAll(ss);
                yield combined;
            }
            case "Neuron" -> noosphere.truthTableForNeuron(id);
            case "FnlPackage" -> noosphere.knowledgeIndexForPackage(id);
            default -> List.of();
        };
        return Response.ok(new DependentsResponse(type, id, deps.size(), deps)).build();
    }

    // ── DTOs ──

    public static class CascadeRequest {
        @NotBlank
        public String subjectId;
        @NotBlank
        public String sourceType;
        @NotBlank
        public String sourceId;
        @NotBlank
        public String reason;
        public String requesterId;
    }

    public record CascadeResponse(
            int totalTombstoned,
            String sourceType,
            String sourceId,
            long cascadeCount,
            List<Tombstone> tombstones) {}

    public record NoosphereStatsResponse(int totalEntities,
                                          java.util.Map<String, Integer> countByType) {}

    public record DependentsResponse(String type, String id, int size, List<String> dependents) {}

    public record ErrorResponse(String error) {}
}