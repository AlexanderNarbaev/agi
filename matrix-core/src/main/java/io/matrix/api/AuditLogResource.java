package io.matrix.api;

import io.matrix.privacy.MockNoosphere;
import io.matrix.privacy.PrivacyService;
import io.matrix.privacy.Tombstone;
import io.matrix.privacy.TombstoneService;
import io.matrix.privacy.CascadeRegistrar;
import io.matrix.privacy.CascadeTombstoneService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * HTTP/REST endpoint for the {@link PrivacyService} — exposes GDPR
 * erasure, cascade, and audit-log operations over JSON.
 *
 * <p>Endpoints:
 * <pre>
 *   POST /api/v1/privacy/erase
 *        body: { "subjectId": "...", "resourceType": "FnlPackage", "resourceId": "fnl-1",
 *                "reasonCode": "gdpr" | "subject_request" | "legal_hold" | "operational" | "custom",
 *                "customReason": "...", "cascade": true|false }
 *
 *   GET  /api/v1/privacy/audit?format=text|jsonl
 *        — Export the full audit log (compliance reports)
 *
 *   GET  /api/v1/privacy/tombstones?reason=gdpr.&subject=u-1
 *        — List tombstones, optionally filtered
 *
 *   GET  /api/v1/privacy/count
 *        — Total tombstones + counters (erasures / cascades)
 * </pre>
 *
 * <p>Ref: L6 §6.7 (GDPR), L12 §4, Wave 32.
 */
@Path("/api/v1/privacy")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuditLogResource {

    private final PrivacyService privacy;

    @Inject
    public AuditLogResource() {
        this(buildDefault());
    }

    /** Test-injectable constructor. */
    public AuditLogResource(PrivacyService privacy) {
        this.privacy = privacy;
    }

    private static PrivacyService buildDefault() {
        TombstoneService tombstones = new TombstoneService();
        MockNoosphere noosphere = new MockNoosphere();
        CascadeTombstoneService cascade = CascadeRegistrar.registerAll(tombstones,
                new CascadeRegistrar.Resolvers(
                        (id, d) -> noosphere.neuronsForAgent(id),
                        (id, d) -> noosphere.snapshotsForAgent(id),
                        (id, d) -> noosphere.truthTableForNeuron(id),
                        (id, d) -> noosphere.knowledgeIndexForPackage(id)));
        return new PrivacyService(tombstones, cascade);
    }

    public PrivacyService privacy() { return privacy; }

    // ── Erase ──

    @POST
    @Path("/erase")
    public Response erase(@Valid EraseRequest req) {
        if (req == null || req.subjectId == null || req.resourceType == null
                || req.resourceId == null || req.reasonCode == null) {
            return Response.status(400)
                    .entity(new ErrorResponse("subjectId, resourceType, resourceId, reasonCode are required"))
                    .build();
        }
        String reasonCode = req.reasonCode.toLowerCase(java.util.Locale.ROOT);
        String customReason = req.customReason;

        boolean cascade = req.cascade;
        Object result;
        switch (reasonCode) {
            case "gdpr" -> {
                if (cascade) {
                    result = privacy.eraseGdprAndCascade(req.subjectId, req.resourceType, req.resourceId);
                } else {
                    result = privacy.eraseGdpr(req.subjectId, req.resourceType, req.resourceId);
                }
            }
            case "subject_request" -> result = privacy.eraseSubjectRequest(
                    req.subjectId, req.resourceType, req.resourceId);
            case "legal_hold" -> result = privacy.legalHold(
                    req.subjectId, req.resourceType, req.resourceId);
            case "operational" -> result = privacy.operationalCleanup(
                    req.subjectId, req.resourceType, req.resourceId);
            case "custom" -> {
                if (customReason == null || customReason.isBlank()) {
                    return Response.status(400)
                            .entity(new ErrorResponse("customReason required when reasonCode=custom"))
                            .build();
                }
                result = privacy.erase(customReason, req.subjectId, req.resourceType, req.resourceId);
            }
            default -> {
                return Response.status(400)
                        .entity(new ErrorResponse("Unknown reasonCode: " + req.reasonCode))
                        .build();
            }
        }
        return Response.ok(result).build();
    }

    // ── Audit log export ──

    @GET
    @Path("/audit")
    public Response exportAudit(@QueryParam("format") @DefaultValue("text") String format) {
        if ("jsonl".equalsIgnoreCase(format)) {
            return Response.ok(privacy.exportJsonLines()).type(MediaType.TEXT_PLAIN).build();
        }
        return Response.ok(privacy.exportAuditLog()).type(MediaType.TEXT_PLAIN).build();
    }

    // ── Tombstone query ──

    @GET
    @Path("/tombstones")
    public Response listTombstones(@QueryParam("reason") String reason,
                                     @QueryParam("subject") String subject) {
        List<Tombstone> list;
        if (reason != null && !reason.isBlank()) {
            list = privacy.findByReason(reason);
        } else if (subject != null && !subject.isBlank()) {
            list = privacy.findBySubject(subject);
        } else {
            list = privacy.all();
        }
        return Response.ok(new TombstoneListResponse(list.size(), list)).build();
    }

    @GET
    @Path("/count")
    public Response count() {
        return Response.ok(new CountResponse(
                privacy.count(),
                privacy.totalErasures(),
                privacy.totalCascades())).build();
    }

    // ── DTOs ──

    public static class EraseRequest {
        @NotBlank
        public String subjectId;
        @NotBlank
        public String resourceType;
        @NotBlank
        public String resourceId;
        @NotBlank
        public String reasonCode;
        public String customReason;
        public boolean cascade = false;
    }

    public record TombstoneListResponse(int size, List<Tombstone> tombstones) {}

    public record CountResponse(int totalTombstones, long totalErasures, long totalCascades) {}

    public record ErrorResponse(String error) {}
}