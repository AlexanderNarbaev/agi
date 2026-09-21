package io.matrix.verification;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.inject.Inject;
import java.util.Map;

/**
 * REST API for verification system.
 */
@Path("/api/v1/verification")
@Produces(MediaType.APPLICATION_JSON)
public class VerificationResource {

    @Inject
    RuntimeVerifier verifier;

    @Inject
    ContinuousVerifier continuous;

    @Inject
    VerificationReport report;

    @GET
    @Path("/properties")
    public Map<String, Object> getProperties() {
        return Map.of(
                "properties", verifier.getAvailableProperties(),
                "count", verifier.getAvailableProperties().size()
        );
    }

    @GET
    @Path("/stats")
    public Map<String, Object> getStats() {
        return continuous.getStats();
    }

    @GET
    @Path("/violations")
    public Map<String, Object> getViolations() {
        return Map.of(
                "total", report.getTotalViolations(),
                "critical", report.getCriticalViolations()
        );
    }
}
