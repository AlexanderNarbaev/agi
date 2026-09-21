package io.matrix.api.resource;

import io.matrix.api.brain.BrainCycle;
import io.matrix.api.brain.ExplainTrace;
import io.matrix.api.brain.StubBrainCycle;
import io.matrix.api.dto.ExplainResponse;
import io.matrix.api.security.InputValidator;
import io.matrix.api.security.JwtAuthFilter;
import io.matrix.api.security.RbacChecker;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;

/**
 * WAVE T-02 — GET /v1/explain/{id}.
 *
 * <p>Retrieves the XAI breakdown for a previously-computed decision.</p>
 *
 * <p><b>Required role:</b> VIEWER or higher.</p>
 */
@Path("/v1/explain")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Explain", description = "XAI breakdown retrieval endpoint")
public final class ExplainResource {

    private final BrainCycle brain;
    private final JwtAuthFilter jwt;

    public ExplainResource() {
        this(new StubBrainCycle(), new JwtAuthFilter());
    }

    public ExplainResource(BrainCycle brain, JwtAuthFilter jwt) {
        this.brain = brain;
        this.jwt = jwt;
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieve XAI breakdown for a previous decision")
    @SecurityRequirement(name = "bearerAuth")
    public Response explain(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
                             @PathParam("id") String id) {
        JwtAuthFilter.Claims claims;
        try {
            claims = jwt.validate(authHeader);
        } catch (SecurityException se) {
            return Response.status(401)
                .entity("{\"error\":\"" + se.getMessage() + "\"}")
                .build();
        }
        RbacChecker.Principal principal = new RbacChecker.Principal(claims.sub(), claims.role());
        try {
            RbacChecker.require(principal, RbacChecker.Role.VIEWER);
        } catch (SecurityException se) {
            return Response.status(403)
                .entity("{\"error\":\"" + se.getMessage() + "\"}")
                .build();
        }

        try {
            InputValidator.sanitizeId(id);
        } catch (SecurityException se) {
            return Response.status(400)
                .entity("{\"error\":\"" + se.getMessage() + "\"}")
                .build();
        }

        ExplainTrace trace;
        try {
            trace = brain.buildExplain(id);
        } catch (IllegalArgumentException iae) {
            return Response.status(404)
                .entity("{\"error\":\"" + iae.getMessage() + "\"}")
                .build();
        }

        ExplainResponse response = new ExplainResponse();
        response.explainId = trace.explainId;
        response.steps = new ArrayList<>();
        for (BrainCycle.CycleResult r : trace.steps) {
            response.steps.add(new ExplainResponse.Step(
                r.reply(),
                "duration=" + r.durationMs() + "ms",
                r.durationMs()
            ));
        }
        response.modulatorSnapshot = new ExplainResponse.ModulatorSnapshot(
            trace.ethicalFilter,
            trace.safetyMonitor,
            trace.consistencyChecker,
            trace.lieDetector
        );
        response.hdcMemoryHits = trace.hdcMemoryHits;
        response.confidenceBreakdown = new ExplainResponse.ConfidenceBreakdown(
            trace.birConfidence,
            trace.hdcConfidence,
            trace.mctsConfidence,
            trace.aggregate
        );

        return Response.ok(response).build();
    }
}
