package io.matrix.observability;

/**
 * WAVE T-01 placeholder — Wave T-09 will implement the observability stack.
 *
 * <p>Final design (T-09):</p>
 * <ul>
 *   <li>Prometheus exporter: pulls metrics from {@code matrix-core}'s
 *       {@code quarkus-micrometer-registry-prometheus} endpoint.</li>
 *   <li>Grafana dashboards: BIR/HDC inference latency, modulator state,
 *       federation health, credit consumption.</li>
 *   <li>Loki log aggregation: JSON-structured logs from all ecosystem modules.</li>
 *   <li>Jaeger tracing: OpenTelemetry spans across HTTP gateway → SDK → core.</li>
 * </ul>
 *
 * <p><b>CONSTITUTION compliance:</b> Telemetry only. No user data leaves the
 * trust boundary; aggregate metrics only.</p>
 */
public final class ObservabilityModule {

    /** Module version. */
    public static final String VERSION = "0.1.0-T01";

    /** Default Prometheus scrape port (matches matrix-core Quarkus default). */
    public static final int DEFAULT_PROMETHEUS_PORT = 9090;

    /** Default Grafana UI port. */
    public static final int DEFAULT_GRAFANA_PORT = 3000;

    /** Default Jaeger UI port. */
    public static final int DEFAULT_JAEGER_PORT = 16686;

    private ObservabilityModule() {
        // static facade only
    }

    public static String status() {
        return "matrix-observability:" + VERSION
            + ":prom=" + DEFAULT_PROMETHEUS_PORT
            + ":grafana=" + DEFAULT_GRAFANA_PORT
            + ":jaeger=" + DEFAULT_JAEGER_PORT;
    }
}
