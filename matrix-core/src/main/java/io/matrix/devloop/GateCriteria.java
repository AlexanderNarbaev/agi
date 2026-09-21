package io.matrix.devloop;

import java.util.Map;
import java.util.Objects;

/**
 * The observable evidence evaluated by a maturity-gate criterion (SPEC-000#fr-5).
 *
 * <p>A gate's threshold-checker is a {@code Predicate<GateCriteria>}; this record carries
 * the measurable metrics (fidelity, transfer rate, competence, …) that the checker reads.
 * Immutable and deterministic: the map is defensively copied on construction.
 *
 * @param metrics named metric values (e.g. {@code {"fidelity": 0.87}})
 */
public record GateCriteria(Map<String, Double> metrics) {

    public GateCriteria {
        Objects.requireNonNull(metrics, "metrics");
        metrics = Map.copyOf(metrics);
    }

    /** Read a metric by name, defaulting to {@code 0.0} when absent. */
    public double metric(String name) {
        return metrics.getOrDefault(name, 0.0);
    }
}
