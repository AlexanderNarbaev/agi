package io.matrix.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WAVE T-09 — Metrics Registry.
 *
 * Holds all Prometheus metrics for MATRIX services. Thread-safe via
 * ConcurrentHashMap + per-metric synchronization.
 *
 * <p>Standard MATRIX metrics (defined by T-09.5 spec):</p>
 * <ul>
 *   <li>matrix_analyze_requests_total — counter of analyze calls</li>
 *   <li>matrix_analyze_duration_ms — histogram of analyze latency</li>
 *   <li>matrix_explain_lookups_total — counter of explain lookups</li>
 *   <li>matrix_federation_nodes — gauge of active federation nodes</li>
 *   <li>matrix_audit_chain_integrity — gauge (1=intact, 0=tampered)</li>
 *   <li>matrix_credit_balance — gauge per customer</li>
 *   <li>matrix_rate_limit_rejections_total — counter</li>
 * </ul>
 *
 * <p><b>CONSTITUTION compliance:</b> No LLM. Pure telemetry.</p>
 */
public final class MetricsRegistry {

    private final Map<String, PrometheusMetric> metrics = new ConcurrentHashMap<>();

    public PrometheusMetric registerCounter(String name, String help) {
        return register(name, PrometheusMetric.Type.COUNTER, help);
    }

    public PrometheusMetric registerGauge(String name, String help) {
        return register(name, PrometheusMetric.Type.GAUGE, help);
    }

    public PrometheusMetric registerHistogram(String name, String help) {
        return register(name, PrometheusMetric.Type.HISTOGRAM, help);
    }

    public synchronized PrometheusMetric register(String name, PrometheusMetric.Type type, String help) {
        Objects.requireNonNull(name, "name");
        PrometheusMetric existing = metrics.get(name);
        if (existing != null) return existing;
        PrometheusMetric metric = new PrometheusMetric(name, type, help);
        metrics.put(name, metric);
        return metric;
    }

    public PrometheusMetric get(String name) {
        return metrics.get(name);
    }

    /** Render all metrics in Prometheus text exposition format. */
    public String renderAll() {
        StringBuilder sb = new StringBuilder();
        for (PrometheusMetric m : metrics.values()) {
            sb.append(m.render());
        }
        return sb.toString();
    }

    public int size() { return metrics.size(); }

    public Map<String, PrometheusMetric> all() {
        return new LinkedHashMap<>(metrics);
    }
}
