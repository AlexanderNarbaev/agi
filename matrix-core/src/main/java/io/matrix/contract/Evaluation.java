package io.matrix.contract;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * W51 — the measurement of a subject against declared criteria.
 *
 * <p><b>An evaluation is a verdict with its measurements attached.</b> The W51 requirement
 * that "reflection must produce machine-readable changes or decisions that can be evaluated"
 * needs a record whose verdict can be CHECKED rather than read, and this is it: the metrics
 * are named, the denominator of each metric is named, and the verdict says which criterion was
 * met.</p>
 *
 * <p><b>The criteria are declared before the measurement.</b> {@link #criteria()} are the
 * conditions the verdict is computed against. A verdict without declared criteria is an
 * opinion, and an evaluation that decides its criterion after seeing the number is the
 * mistake the W50 demonstrator's "declared expectation" was designed to prevent.</p>
 *
 * <p><b>A metric without a denominator is not a measurement.</b> Each metric carries its
 * sample size, so "accuracy 1.0 over 1 case" and "accuracy 1.0 over 100 cases" are
 * distinguishable.</p>
 *
 * @param id          the identity of the evaluation
 * @param subjectId   what was evaluated
 * @param subjectKind the kind of thing that was evaluated
 * @param criteria    the declared criteria the verdict is computed against
 * @param metrics     the measurements, by name
 * @param verdict     whether the criteria were met
 * @param reason      why the verdict is what it is
 * @param evaluatedAt when the evaluation happened
 */
public record Evaluation(
        String id,
        String subjectId,
        String subjectKind,
        Map<String, String> criteria,
        Map<String, Metric> metrics,
        Boolean verdict,
        String reason,
        String evaluatedAt) {

    /**
     * One measurement, with the number of cases behind it.
     *
     * @param value       the value of the measurement
     * @param sampleSize  the number of cases the value was measured over
     */
    public record Metric(double value, int sampleSize) {
        public Metric {
            if (Double.isNaN(value)) {
                throw new IllegalArgumentException("metric value must not be NaN");
            }
            if (sampleSize < 0) {
                throw new IllegalArgumentException("metric sample size must not be negative");
            }
        }
    }

    public Evaluation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("evaluation id must not be blank");
        }
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("evaluation subject id must not be blank");
        }
        criteria = criteria == null ? Map.of() : new LinkedHashMap<>(criteria);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }

    /**
     * An evaluation that met its criteria.
     *
     * @param id          the identity of the evaluation
     * @param subjectId   what was evaluated
     * @param subjectKind the kind of thing that was evaluated
     * @param criteria    the declared criteria
     * @param metrics     the measurements
     * @param at          when the evaluation happened
     * @return the evaluation
     */
    public static Evaluation passed(String id, String subjectId, String subjectKind,
                                    Map<String, String> criteria, Map<String, Metric> metrics,
                                    String at) {
        return new Evaluation(id, subjectId, subjectKind, criteria, metrics, Boolean.TRUE,
                "every declared criterion was met", at);
    }

    /**
     * An evaluation that did not meet its criteria.
     *
     * @param reason why the criteria were not met
     * @return the evaluation
     */
    public Evaluation failed(String reason) {
        return new Evaluation(id, subjectId, subjectKind, criteria, metrics, Boolean.FALSE, reason,
                evaluatedAt);
    }

    /**
     * The value of a metric.
     *
     * @param name the name of the metric
     * @return the metric, or {@code null} when it was not measured
     */
    public Metric metric(String name) {
        return metrics.get(name);
    }

    /** True when the evaluation met its criteria. */
    public boolean passed() {
        return Boolean.TRUE.equals(verdict);
    }

    @Override
    public String toString() {
        return "Evaluation[" + id + " " + subjectKind + " verdict=" + verdict + "]";
    }
}
