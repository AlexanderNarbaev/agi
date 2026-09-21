package io.matrix.observability;

/**
 * WAVE T-09 — Observability Module facade.
 *
 * Replaces T-01 placeholder with real Prometheus + health + alerting.
 *
 * <p><b>CONSTITUTION compliance:</b> No LLM. Pure telemetry.</p>
 */
public final class ObservabilityModule {

    public static final String VERSION = "0.1.0-T09";

    public static final int DEFAULT_PROMETHEUS_PORT = 9090;
    public static final int DEFAULT_GRAFANA_PORT = 3000;
    public static final int DEFAULT_JAEGER_PORT = 16686;

    private static final MetricsRegistry METRICS = new MetricsRegistry();
    private static final HealthCheck HEALTH = new HealthCheck();
    private static final AlertDispatcher ALERTS = new AlertDispatcher();

    // Standard MATRIX metrics — registered on class init
    static {
        METRICS.registerCounter("matrix_analyze_requests_total",
            "Total number of /v1/analyze requests");
        METRICS.registerHistogram("matrix_analyze_duration_ms",
            "Latency of /v1/analyze requests in milliseconds");
        METRICS.registerCounter("matrix_explain_lookups_total",
            "Total number of /v1/explain lookups");
        METRICS.registerGauge("matrix_federation_nodes",
            "Current number of active federation nodes");
        METRICS.registerGauge("matrix_audit_chain_integrity",
            "1 if hash chain intact, 0 if tampered");
        METRICS.registerCounter("matrix_rate_limit_rejections_total",
            "Total requests rejected by rate limiter");
        METRICS.registerCounter("matrix_gdpr_erasures_total",
            "Total GDPR erasure operations performed");

        // Default health checks
        HEALTH.registerSupplier("process_uptime", () -> true);
    }

    private ObservabilityModule() {}

    public static String status() {
        return "matrix-observability:" + VERSION
            + ":metrics=" + METRICS.size()
            + ":probes=" + HEALTH.probeCount()
            + ":channels=" + ALERTS.channelCount();
    }

    public static MetricsRegistry metrics() { return METRICS; }
    public static HealthCheck health() { return HEALTH; }
    public static AlertDispatcher alerts() { return ALERTS; }
}
