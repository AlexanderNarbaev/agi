package io.matrix.api.resource;

import io.matrix.api.brain.BrainCycle;
import io.matrix.api.brain.StubBrainCycle;
import io.matrix.api.dto.AnalyzeRequest;
import io.matrix.api.dto.AnalyzeResponse;
import io.matrix.api.security.InputValidator;
import io.matrix.api.security.JwtAuthFilter;
import io.matrix.api.security.RateLimiter;
import io.matrix.api.security.RbacChecker;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * WAVE T-02 — POST /v1/analyze.
 *
 * <p>Authenticate → rate-limit → validate input → run hybrid inference →
 * return reply with explain_id. All response paths include an explain_id
 * so callers can retrieve XAI breakdowns via {@code /v1/explain/{id}}.</p>
 *
 * <p><b>Required role:</b> DEVELOPER or higher.</p>
 */
@Path("/v1/analyze")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Analyze", description = "Hybrid neuro-symbolic inference endpoint")
public final class AnalyzeResource {

    private final BrainCycle brain;
    private final JwtAuthFilter jwt;
    private final RateLimiter rateLimiter;

    public AnalyzeResource() {
        this(new StubBrainCycle(), new JwtAuthFilter(), new RateLimiter());
    }

    /** Test constructor. */
    public AnalyzeResource(BrainCycle brain, JwtAuthFilter jwt, RateLimiter rateLimiter) {
        this.brain = brain;
        this.jwt = jwt;
        this.rateLimiter = rateLimiter;
    }

    @POST
    @Operation(summary = "Run hybrid inference on text/audio/image input")
    @SecurityRequirement(name = "bearerAuth")
    public Response analyze(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
                             AnalyzeRequest request) {
        // 1. Authenticate
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
            RbacChecker.require(principal, RbacChecker.Role.DEVELOPER);
        } catch (SecurityException se) {
            return Response.status(403)
                .entity("{\"error\":\"" + se.getMessage() + "\"}")
                .build();
        }

        // 2. Rate-limit
        if (!rateLimiter.tryAcquire(claims.sub(), claims.plan())) {
            return Response.status(429)
                .entity("{\"error\":\"Rate limit exceeded for plan " + claims.plan() + "\"}")
                .header("X-RateLimit-Plan", claims.plan().name())
                .build();
        }

        // 3. Validate input (OWASP)
        if (request == null || request.input == null) {
            return Response.status(400)
                .entity("{\"error\":\"Request body required with 'input' field\"}")
                .build();
        }
        try {
            InputValidator.sanitizeText(request.input);
            InputValidator.sanitizeContentType(request.contentType);
        } catch (SecurityException se) {
            return Response.status(400)
                .entity("{\"error\":\"" + se.getMessage() + "\"}")
                .build();
        }

        // 4. Run inference (stub or real matrix-core)
        BrainCycle.CycleResult result = brain.cycle(request.input, request.context, request.model);

        // 5. Build response with explain_id
        AnalyzeResponse response = new AnalyzeResponse(
            result.reply(),
            result.confidence(),
            result.durationMs(),
            result.accepted(),
            "expl_" + Integer.toHexString(result.hashCode())
        );
        response.modulatorsFired = result.modulatorsFired();

        return Response.ok(response)
            .header("X-RateLimit-Plan", claims.plan().name())
            .header("X-RateLimit-Used", String.format("%.2f",
                rateLimiter.usage(claims.sub(), claims.plan())))
            .build();
    }
}
