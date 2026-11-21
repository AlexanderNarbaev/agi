package io.matrix.cognitive.algorithms;

import java.util.Locale;
import java.util.Map;

/**
 * W44.2 — how a method's declared complexity becomes a comparable cost.
 *
 * <p>The selector has to compare methods that were measured with methods that were not. Mixing a
 * measured nanosecond count with a symbolic big-O would be comparing incompatible units, so
 * everything is converted to nanoseconds first.</p>
 *
 * <p><b>The convention.</b> A capability's {@link AlgorithmCapability.ComplexityProfile#time()} may
 * carry an explicit cost hint after the asymptotic label, separated by a semicolon:</p>
 * <pre>
 *   "O(V+E)"                          — the label alone; the growth table below is used
 *   "O(V+E);costMillis=12"            — an explicit measured cost of 12 ms
 *   "O(V+E);costMillis=12;costNodes=4096"
 * </pre>
 *
 * <p>The explicit form is preferred and is what this package's own methods use: it carries a
 * number that somebody measured rather than a number that somebody guessed. The growth table is
 * the fallback for a label written by hand, and its values are a CONVENTION — representative
 * nanosecond costs for a single small input — not measurements of anything. The decision record
 * says which of the two was used, so a reader never mistakes a convention for a measurement.</p>
 *
 * <p>Unknown labels are not an error: they fall back to
 * {@link #DEFAULT_DECLARED_COST_NANOS} and the parsed cost records that the label was not
 * recognised, so the caller can see that the estimate is a placeholder.</p>
 */
public final class DeclaredCost {

    /** The nanoseconds in one millisecond. DERIVED from the time unit definition. */
    private static final long NANOS_PER_MILLI = 1_000_000L;

    /**
     * The cost assumed for a complexity label this package does not recognise.
     *
     * <p>CONVENTION: a placeholder in the middle of the growth table, so that an unknown label
     * neither wins nor loses a comparison for an accidental reason.</p>
     */
    public static final long DEFAULT_DECLARED_COST_NANOS = 10_000L;

    /**
     * Representative nanosecond costs by asymptotic label.
     *
     * <p>CONVENTION: these are the costs of one small computation, not measurements of a
     * particular machine. Their purpose is to ORDER methods whose cost nobody measured; the
     * absolute values matter only when a measured value and a declared value meet, which is why
     * the explicit {@code costMillis} hint exists.</p>
     */
    private static final long COST_CONSTANT = 1L;
    private static final long COST_LOGARITHMIC = 10L;
    private static final long COST_LINEAR = 100L;
    private static final long COST_LINEAR_LOGARITHMIC = 1_000L;
    private static final long COST_GRAPH_EDGES = 1_000L;
    private static final long COST_GRAPH_DENSE = 10_000L;
    private static final long COST_EXPONENTIAL = 1_000_000L;

    private static final Map<String, Long> GROWTH_TABLE = Map.of(
            "O(1)", COST_CONSTANT,
            "O(log n)", COST_LOGARITHMIC,
            "O(n)", COST_LINEAR,
            "O(n log n)", COST_LINEAR_LOGARITHMIC,
            "O(V+E)", COST_GRAPH_EDGES,
            "O(V*E)", COST_GRAPH_DENSE,
            "O(2^n)", COST_EXPONENTIAL);

    /**
     * A cost read out of a complexity profile.
     *
     * @param nanos    the cost in nanoseconds
     * @param nodes    the number of search nodes the method is expected to touch, or zero when unknown
     * @param explicit whether the cost was stated outright rather than inferred from the label
     * @param label    the asymptotic label the cost was read from
     * @param recognised whether the label was one this package knows
     */
    public record Cost(long nanos, int nodes, boolean explicit, String label, boolean recognised,
                       boolean measured) {

        /** A short description for a decision record. */
        public String describe() {
            String basis = measured ? "measured" : (explicit ? "declared" : "growth-table");
            return basis + " " + label + " = " + nanos + "ns"
                    + (nodes > 0 ? ", " + nodes + " nodes" : "");
        }
    }

    private DeclaredCost() {
    }

    /**
     * Builds the complexity label with a MEASURED cost.
     *
     * <p><b>Why a measurement is written differently from an estimate.</b> A declared cost is
     * a promise: somebody said "this is about so long". A measured cost is a fact: somebody
     * ran the work and timed it. The two must not read the same in a decision record, because
     * a decision made on a promise and a decision made on a measurement have different
     * standing, and the reader of the record has to be able to tell them apart.</p>
     *
     * <p>The measurement is written in NANOSECONDS, because a measurement is a duration and a
     * duration is not a whole number of milliseconds: rounding it before it is stored would
     * lose the precision the measurement was taken with.</p>
     *
     * @param label the asymptotic label, e.g. {@code "O(V+E)"}
     * @param nanos the measured cost in nanoseconds
     * @return the label with the measurement appended
     */
    public static String declareMeasured(String label, long nanos) {
        return (label == null ? "unknown" : label) + ";costMeasuredNanos=" + nanos;
    }

    /**
     * Builds the complexity label with an explicit cost hint.
     *
     * @param label the asymptotic label, e.g. {@code "O(V+E)"}
     * @param millis the measured cost in milliseconds
     * @param nodes the expected number of search nodes, or zero when it is not a search
     * @return the label with the hint appended
     */
    public static String declare(String label, long millis, int nodes) {
        StringBuilder sb = new StringBuilder(label == null ? "unknown" : label);
        sb.append(";costMillis=").append(millis);
        if (nodes > 0) {
            sb.append(";costNodes=").append(nodes);
        }
        return sb.toString();
    }

    /**
     * Reads the cost out of a complexity profile's time string.
     *
     * @param complexityTime the value of {@link AlgorithmCapability.ComplexityProfile#time()}
     * @return the parsed cost; never null
     */
    public static Cost parse(String complexityTime) {
        String text = complexityTime == null ? "" : complexityTime.trim();
        if (text.isEmpty()) {
            return new Cost(DEFAULT_DECLARED_COST_NANOS, 0, false, "unknown", false, false);
        }
        String[] parts = text.split(";");
        String label = parts[0].trim();
        Long explicitMillis = null;
        Long measuredNanos = null;
        int nodes = 0;
        for (int i = 1; i < parts.length; i++) {
            String hint = parts[i].trim();
            int equals = hint.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = hint.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String value = hint.substring(equals + 1).trim();
            if ("costmillis".equals(key)) {
                try {
                    explicitMillis = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    explicitMillis = null;
                }
            } else if ("costnodes".equals(key)) {
                try {
                    nodes = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    nodes = 0;
                }
            } else if ("costmeasurednanos".equals(key)) {
                try {
                    measuredNanos = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    measuredNanos = null;
                }
            }
        }
        if (measuredNanos != null && measuredNanos >= 0) {
            // a MEASUREMENT wins over a declared hint: the work was timed, and a number
            // somebody wrote down is not a better fact than one somebody measured
            return new Cost(measuredNanos, Math.max(0, nodes), true, label, true, true);
        }
        if (explicitMillis != null && explicitMillis >= 0) {
            return new Cost(explicitMillis * NANOS_PER_MILLI, Math.max(0, nodes), true, label, true,
                    false);
        }
        Long tableValue = GROWTH_TABLE.get(label);
        if (tableValue == null) {
            return new Cost(DEFAULT_DECLARED_COST_NANOS, Math.max(0, nodes), false, label, false,
                    false);
        }
        return new Cost(tableValue, Math.max(0, nodes), false, label, true, false);
    }
}
