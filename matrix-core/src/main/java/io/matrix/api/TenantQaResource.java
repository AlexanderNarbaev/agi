package io.matrix.api;

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
 * Tenant-scoped QA retrieval resource (RUN 33).
 *
 * <p>Exposes per-tenant retrieval over {@link TenantQaIndex}. Each
 * tenant has its own logical namespace; cross-tenant leakage is
 * impossible by construction (see TenantQaIndex tests).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /v1/tenant/{tenantId}/search?q=...&k=5} — search</li>
 *   <li>{@code POST /v1/tenant/{tenantId}/learn} — add entry to tenant</li>
 *   <li>{@code GET  /v1/tenant/{tenantId}/stats} — diagnostics</li>
 * </ul>
 *
 * <p>Tenant IDs are case-sensitive and must be non-blank.
 *
 * <p>No auth: this is internal logic for now. Production deployment
 * would add a tenant-resolution middleware that validates the caller's
 * JWT against the requested tenant ID.
 */
@Path("/v1/tenant")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TenantQaResource {

    @Inject
    QaCorpusIndex baseIndex;

    private TenantQaIndex tenantIndex;

    /** Lazy-init because CDI proxying doesn't play well with constructor injection here. */
    private TenantQaIndex tenants() {
        if (tenantIndex == null) {
            tenantIndex = new TenantQaIndex(baseIndex);
        }
        return tenantIndex;
    }

    /**
     * GET /v1/tenant/{tenantId}/search?q=...&k=5
     */
    @GET
    @Path("/{tenantId}/search")
    public Response search(@PathParam("tenantId") String tenantId,
                           @QueryParam("q") String query,
                           @QueryParam("k") Integer topK) {
        if (tenantId == null || tenantId.isBlank()) {
            return Response.status(400)
                    .entity(Map.of("error", "tenantId is required"))
                    .build();
        }
        int k = topK == null ? 5 : Math.max(1, Math.min(20, topK));
        List<QaCorpusIndex.Entry> results = tenants().searchForTenant(tenantId, query, k);
        return Response.ok(Map.of(
                "tenantId", tenantId,
                "query", query == null ? "" : query,
                "topK", k,
                "results", results
        )).build();
    }

    /**
     * POST /v1/tenant/{tenantId}/learn
     * Body: {"question": "...", "answer": "...", "category": "...", "source": "..."}
     */
    @POST
    @Path("/{tenantId}/learn")
    public Response learn(@PathParam("tenantId") String tenantId, LearnRequest req) {
        if (tenantId == null || tenantId.isBlank()) {
            return Response.status(400).entity(Map.of("error", "tenantId is required")).build();
        }
        if (req == null || req.question == null || req.question.isBlank()
                || req.answer == null || req.answer.isBlank()) {
            return Response.status(400).entity(Map.of("error", "question and answer are required")).build();
        }
        // Add to base corpus (so search() can find it), then tag with tenant.
        QaCorpusIndex.Entry entry = baseIndex.add(
                req.question, req.answer,
                req.category == null ? "tenant" : req.category,
                req.source == null ? "tenant:" + tenantId : req.source);
        boolean added = tenants().add(tenantId, entry);
        if (!added) {
            return Response.status(500).entity(Map.of("error", "failed to tag entry with tenant")).build();
        }
        return Response.ok(Map.of(
                "ok", true,
                "tenantId", tenantId,
                "entryId", entry.id()
        )).build();
    }

    /**
     * GET /v1/tenant/{tenantId}/stats
     */
    @GET
    @Path("/{tenantId}/stats")
    public Response stats(@PathParam("tenantId") String tenantId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("entryCount", tenants().sizeForTenant(tenantId));
        body.put("totalTenants", tenants().tenantCount());
        body.put("totalEntries", tenants().totalEntries());
        return Response.ok(body).build();
    }

    /** Request body for /learn. */
    public static class LearnRequest {
        public String question;
        public String answer;
        public String category;
        public String source;
    }
}
