package io.matrix.api.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.matrix.api.security.JwtAuthFilter;
import io.matrix.api.security.RbacChecker;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * WAVE T-02 — GET /v1/audit/logs.
 *
 * <p>Retrieves the immutable action log for compliance and debugging.
 * T-02 in-memory implementation; T-06 replaces with the real
 * {@code matrix-audit} hash-chained backend.</p>
 *
 * <p><b>Required role:</b> ADMIN only (sensitive data).</p>
 */
@Path("/v1/audit")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Audit", description = "Immutable action log access")
public final class AuditResource {

    /** Public for testing — production wires to matrix-audit in T-06. */
    public static final ConcurrentLinkedDeque<AuditEntry> ENTRIES = new ConcurrentLinkedDeque<>();

    public static void record(String userId, String action, String target, int statusCode) {
        AuditEntry e = new AuditEntry();
        e.timestamp = Instant.now().toString();
        e.userId = userId;
        e.action = action;
        e.target = target;
        e.statusCode = statusCode;
        ENTRIES.add(e);
        // Keep memory bounded: drop oldest beyond 100k entries
        while (ENTRIES.size() > 100_000) {
            ENTRIES.pollFirst();
        }
    }

    public static final class AuditEntry {
        @JsonProperty("timestamp")
        public String timestamp;

        @JsonProperty("user_id")
        public String userId;

        @JsonProperty("action")
        public String action;

        @JsonProperty("target")
        public String target;

        @JsonProperty("status_code")
        public int statusCode;
    }

    private final JwtAuthFilter jwt;

    public AuditResource() {
        this(new JwtAuthFilter());
    }

    public AuditResource(JwtAuthFilter jwt) {
        this.jwt = jwt;
    }

    @GET
    @Path("/logs")
    @Operation(summary = "Retrieve immutable action log (admin only)")
    @SecurityRequirement(name = "bearerAuth")
    public Response logs(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
                          @QueryParam("limit") @DefaultValue("100") int limit) {
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
            RbacChecker.require(principal, RbacChecker.Role.ADMIN);
        } catch (SecurityException se) {
            return Response.status(403)
                .entity("{\"error\":\"" + se.getMessage() + "\"}")
                .build();
        }

        if (limit < 1 || limit > 1000) {
            return Response.status(400)
                .entity("{\"error\":\"limit must be 1..1000\"}")
                .build();
        }

        List<AuditEntry> snapshot = new ArrayList<>(ENTRIES);
        // Return most recent first
        Collections.reverse(snapshot);
        if (snapshot.size() > limit) {
            snapshot = snapshot.subList(0, limit);
        }
        return Response.ok(snapshot).build();
    }
}
