package io.matrix.api.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.matrix.api.federation.FederationRegistry;
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

/**
 * WAVE T-02 — POST /v1/federate.
 *
 * <p>Joins a new federation node to the MATRIX liquid federation pool.
 * Returns the assigned node_id, region, and shard capacity.</p>
 *
 * <p><b>Required role:</b> DEVELOPER or higher.</p>
 */
@Path("/v1/federate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Federate", description = "Federation node management")
public final class FederateResource {

    private final FederationRegistry registry;
    private final JwtAuthFilter jwt;

    public FederateResource() {
        this(new FederationRegistry(), new JwtAuthFilter());
    }

    public FederateResource(FederationRegistry registry, JwtAuthFilter jwt) {
        this.registry = registry;
        this.jwt = jwt;
    }

    public static final class JoinRequest {
        @JsonProperty("region")
        public String region;

        @JsonProperty("shard_capacity")
        public Integer shardCapacity;
    }

    public static final class JoinResponse {
        @JsonProperty("node_id")
        public String nodeId;

        @JsonProperty("region")
        public String region;

        @JsonProperty("shard_capacity")
        public int shardCapacity;

        @JsonProperty("joined_at")
        public String joinedAt;

        @JsonProperty("total_nodes")
        public int totalNodes;
    }

    @POST
    @Operation(summary = "Join a federation node")
    @SecurityRequirement(name = "bearerAuth")
    public Response join(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
                          JoinRequest request) {
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

        if (request == null || request.region == null) {
            return Response.status(400)
                .entity("{\"error\":\"region required\"}")
                .build();
        }

        try {
            InputValidator.sanitizeText(request.region);
        } catch (SecurityException se) {
            return Response.status(400)
                .entity("{\"error\":\"" + se.getMessage() + "\"}")
                .build();
        }

        int capacity = request.shardCapacity == null ? 100 : request.shardCapacity;
        if (capacity < 1 || capacity > 100_000) {
            return Response.status(400)
                .entity("{\"error\":\"shard_capacity must be 1..100000\"}")
                .build();
        }

        FederationRegistry.Node node = registry.join(request.region, capacity);
        JoinResponse response = new JoinResponse();
        response.nodeId = node.nodeId();
        response.region = node.region();
        response.shardCapacity = node.shardCapacity();
        response.joinedAt = node.joinedAt().toString();
        response.totalNodes = registry.size();

        return Response.status(201).entity(response).build();
    }

    @GET
    @Operation(summary = "List all federation nodes")
    @SecurityRequirement(name = "bearerAuth")
    public Response list(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
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

        return Response.ok(registry.list()).build();
    }
}
