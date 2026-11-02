package io.matrix.cognitive.microworld;

/**
 * W44.4 — a proposed discriminating experiment.
 *
 * <p>This is the micro-world's only action type: try an assignment that the current hypotheses
 * disagree about. The W44 kernel maps its {@code ActionProposal} onto this record
 * ({@code ADR-W44-01} §2.2), so it is an action, not a note.</p>
 *
 * <h2>The information gain is computed, never asserted</h2>
 *
 * <p>{@link #expectedInformationGain()} is the expected REDUCTION in the number of surviving
 * hypotheses, as a fraction of the hypotheses that are alive now, for one probe of this
 * assignment. The formula, for a surviving set H of size n at an assignment A:</p>
 *
 * <pre>
 *   t = the number of hypotheses in H that predict the outcome variable is TRUE at A
 *   f = the number of hypotheses in H that predict it is FALSE at A        (t + f = n)
 *   P(outcome = true)  = t / n          the hypothesis-implied prior
 *   P(outcome = false) = f / n
 *   if the outcome is true,  the f hypotheses that predicted false are eliminated,
 *                            so the surviving fraction is t / n
 *   if the outcome is false, the t hypotheses that predicted true are eliminated,
 *                            so the surviving fraction is f / n
 *   E[surviving fraction] = (t/n)(t/n) + (f/n)(f/n)
 *   expectedInformationGain = 1 - E[surviving fraction]
 * </pre>
 *
 * <p>The weighting is the hypothesis-implied prior and not a uniform one over the two outcomes,
 * and the reason is that a uniform weighting makes the metric CONSTANT: with two equally likely
 * outcomes every assignment eliminates exactly half the hypotheses in expectation, and a metric
 * that cannot rank the assignments cannot choose one. Under the hypothesis-implied prior the
 * value is 0 when every surviving hypothesis agrees about the assignment and is maximal when they
 * split evenly.</p>
 *
 * <p>The maximum attainable value is <b>0.5</b>, not 1: with a two-valued outcome the best
 * possible split leaves half the hypotheses alive whichever way the outcome falls. The value is
 * therefore comparable BETWEEN assignments, which is what a proposal needs, and it is not a
 * probability.</p>
 */
public record Experiment(String id, WorldInput input, double expectedInformationGain,
                         String rationale) {

    public Experiment {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("an experiment id must not be blank");
        }
        if (input == null) {
            throw new IllegalArgumentException("an experiment must name the assignment it tries");
        }
        if (expectedInformationGain < 0.0 || expectedInformationGain > 1.0
                || Double.isNaN(expectedInformationGain)) {
            throw new IllegalArgumentException("the expected information gain must be within [0,1]");
        }
        if (rationale == null || rationale.isBlank()) {
            throw new IllegalArgumentException("an experiment must say what it would learn");
        }
    }

    /** The canonical key of the assignment this experiment tries. */
    public String assignmentKey() {
        return input.key();
    }

    @Override
    public String toString() {
        return "Experiment[" + id + " " + input.key() + " gain=" + expectedInformationGain + "]";
    }
}
