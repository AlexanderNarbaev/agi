package io.matrix.api;

import io.matrix.imports.BooleanChainRunner;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Production health-check endpoint (RUN 38).
 *
 * <p>Reports the liveness and readiness of all critical services
 * for use by container orchestrators (Kubernetes, Nomad, etc.).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /v1/health} — full health snapshot (all services)</li>
 *   <li>{@code GET /v1/health/live} — liveness only (is process alive?)</li>
 *   <li>{@code GET /v1/health/ready} — readiness only (can serve traffic?)</li>
 * </ul>
 *
 * <p>Status codes:
 * <ul>
 *   <li>200 OK — service healthy</li>
 *   <li>503 SERVICE_UNAVAILABLE — service degraded (chain not loaded,
 *       corpus empty, etc.)</li>
 * </ul>
 *
 * <p>Honest caveats:
 * <ul>
 *   <li>Health checks are point-in-time snapshots, not probes.</li>
 *   <li>"ready" means "all critical services have non-null state"
 *       — not "all features work correctly". A deeper readiness
 *       probe would call /v1/chat and check the response.</li>
 * </ul>
 */
@Path("/v1/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @Inject
    BooleanChainRunner chainRunner;

    @Inject
    QaCorpusIndex qaIndex;

    @Inject
    LmHeadTrainer lmHeadTrainer;

    /** Full health snapshot — returns 200 always (for diagnostics). */
    @GET
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", overallStatus().toString());
        body.put("uptimeMs", MetricsResource.START_TIME_MS == 0 ? 0
                : System.currentTimeMillis() - MetricsResource.START_TIME_MS);
        body.put("services", servicesStatus());
        return body;
    }

    /** Liveness: is the JVM up? Always 200 unless the process is dead. */
    @GET
    @Path("/live")
    public Map<String, Object> live() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("uptimeMs", System.currentTimeMillis() - MetricsResource.START_TIME_MS);
        return body;
    }

    /** Readiness: can the service serve traffic? Returns 503 if degraded. */
    @GET
    @Path("/ready")
    public jakarta.ws.rs.core.Response ready() {
        Status s = overallStatus();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", s.toString());
        body.put("services", servicesStatus());
        if (s == Status.UP) {
            return jakarta.ws.rs.core.Response.ok(body).build();
        } else {
            return jakarta.ws.rs.core.Response.status(503).entity(body).build();
        }
    }

    /** Overall status: UP if all critical services are healthy. */
    private Status overallStatus() {
        boolean chainLoaded = chainRunner != null && chainRunner.layerCount() > 0;
        boolean corpusLoaded = qaIndex != null && qaIndex.size() > 0;
        // Both critical for production readiness.
        return (chainLoaded && corpusLoaded) ? Status.UP : Status.DEGRADED;
    }

    /** Per-service status snapshot. */
    private Map<String, Object> servicesStatus() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chain", Map.of(
                "loaded", chainRunner != null && chainRunner.layerCount() > 0,
                "layers", chainRunner == null ? 0 : chainRunner.layerCount(),
                "totalNeurons", chainRunner == null ? 0 : chainRunner.totalNeurons()
        ));
        body.put("corpus", Map.of(
                "loaded", qaIndex != null && qaIndex.size() > 0,
                "entries", qaIndex == null ? 0 : qaIndex.size()
        ));
        body.put("lmHead", Map.of(
                "trained", lmHeadTrainer != null && lmHeadTrainer.isTrained(),
                "trainedPairs", lmHeadTrainer == null ? 0 : lmHeadTrainer.trainedPairs()
        ));
        return body;
    }

    /** Health status enum. */
    public enum Status { UP, DEGRADED }
}
