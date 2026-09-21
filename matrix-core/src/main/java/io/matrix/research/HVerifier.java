package io.matrix.research;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RUN 46 — Hypothesis verifier scaffolding.
 *
 * <p>Standardizes the pattern of running a hypothesis test:
 * <ol>
 *   <li>Initialize: set up test data, models, fixtures.</li>
 *   <li>Run: execute the experiment.</li>
 *   <li>Measure: collect metrics (accuracy, ECE, drift, etc.).</li>
 *   <li>Verify: check against acceptance criterion.</li>
 * </ol>
 *
 * <p>Provides a uniform {@code Verdict} record + {@code HypothesisVerdict}
 * enum so all EXP tests emit the same shape of result.
 *
 * <p>This is intentionally lightweight — it does NOT replace
 * JUnit tests; it provides a SHARED data class for EXP reporting.
 */
public abstract class HVerifier {

    /** Verdict of a hypothesis test. */
    public enum HypothesisVerdict {
        /** Metric meets acceptance criterion. */
        ACCEPTED,
        /** Metric is in the gray zone; needs more data. */
        INCONCLUSIVE,
        /** Metric does NOT meet acceptance criterion. */
        REJECTED,
        /** Test setup failed (missing data, etc.). */
        ERROR
    }

    /** Result of running one hypothesis test. */
    public record Verdict(
            String hypothesisId,
            HypothesisVerdict outcome,
            String metric,
            double measured,
            double threshold,
            String criterion,
            Map<String, Object> extras) {

        public boolean isAccepted() { return outcome == HypothesisVerdict.ACCEPTED; }

        public String toSummaryString() {
            return String.format(
                    "%s: %s (measured=%.4f, threshold=%.4f, criterion='%s')",
                    hypothesisId, outcome, measured, threshold, criterion);
        }
    }

    /** Run the experiment and return a verdict. */
    public abstract Verdict runExperiment();

    /** Build a simple ACCEPTED verdict. */
    protected static Verdict accepted(String id, String metric, double measured, double threshold, String criterion) {
        return new Verdict(id, HypothesisVerdict.ACCEPTED, metric, measured, threshold, criterion, new LinkedHashMap<>());
    }

    /** Build a REJECTED verdict. */
    protected static Verdict rejected(String id, String metric, double measured, double threshold, String criterion) {
        return new Verdict(id, HypothesisVerdict.REJECTED, metric, measured, threshold, criterion, new LinkedHashMap<>());
    }

    /** Build an INCONCLUSIVE verdict. */
    protected static Verdict inconclusive(String id, String metric, double measured, double threshold, String criterion) {
        return new Verdict(id, HypothesisVerdict.INCONCLUSIVE, metric, measured, threshold, criterion, new LinkedHashMap<>());
    }

    /** Build an ERROR verdict. */
    protected static Verdict error(String id, String message) {
        return new Verdict(id, HypothesisVerdict.ERROR, "error", 0.0, 0.0, message, new LinkedHashMap<>());
    }

    /** Add an extra field to a verdict. */
    protected static Verdict withExtra(Verdict v, String key, Object value) {
        Map<String, Object> extras = new LinkedHashMap<>(v.extras());
        extras.put(key, value);
        return new Verdict(v.hypothesisId(), v.outcome(), v.metric(),
                v.measured(), v.threshold(), v.criterion(), extras);
    }
}
