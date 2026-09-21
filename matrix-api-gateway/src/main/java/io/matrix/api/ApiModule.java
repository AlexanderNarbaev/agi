package io.matrix.api;

/**
 * WAVE T-01 placeholder — Wave T-02 will implement the Quarkus REST facade.
 *
 * <p>This class serves as a compile-time anchor so the multi-module build does
 * not fail with empty sourceset warnings. It is intentionally trivial and
 * contains no runtime logic.</p>
 *
 * <p><b>CONSTITUTION compliance:</b> Zero inference, zero LLM access, zero
 * side effects. The real API gateway is implemented in T-02.</p>
 */
public final class ApiModule {

    /** Module version for telemetry/health endpoints. */
    public static final String VERSION = "0.1.0-T01";

    private ApiModule() {
        // static facade only
    }

    /**
     * Lightweight liveness probe. T-02 will replace this with a proper
     * {@code /q/health} SmallRye Health endpoint.
     */
    public static String liveness() {
        return "matrix-api-gateway:" + VERSION + ":live";
    }
}
