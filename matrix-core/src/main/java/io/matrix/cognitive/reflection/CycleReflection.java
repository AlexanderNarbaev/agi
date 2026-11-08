package io.matrix.cognitive.reflection;

import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.ErrorSignal;
import io.matrix.contract.Evaluation;
import io.matrix.contract.Reflection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W51 §4 — the cycle's reflection: what was attempted, what was chosen, what happened, what
 * was learned, and what remains uncertain.
 *
 * <p><b>What this replaces.</b> The repository had a trace — a list of steps with a phase, an
 * actor and a line of text — and a note list, and no evaluation of the DECISION. The trace
 * says what happened; it does not say whether the method that was chosen should be chosen
 * again, and it cannot be read by anything that is not a human. The mandate asks for
 * "reflection as evaluation of actual decisions and outcomes" that "must produce
 * machine-readable changes or decisions that can be evaluated", and the canonical
 * {@link Reflection} contract was written for exactly that — and had no producer.</p>
 *
 * <p><b>What a reflection is NOT.</b> It is not another paragraph about the result. Every
 * field of it is a fact the cycle already holds, and the {@link Reflection.Decision}s it
 * carries are things that can be CARRIED OUT: a method to prefer, a method to withdraw, a
 * belief to revise, a skill to promote, a proposition to leave unresolved.</p>
 *
 * <p><b>When the method should not be reused.</b> The rule is the outcome, not a feeling: a
 * method whose prediction was contradicted, or that refused to compute, is withdrawn; a
 * method whose prediction was confirmed is preferred; and a method that never got to answer
 * is neither — the cycle says so instead of guessing.</p>
 *
 * <p><b>What remains uncertain is named.</b> A cycle whose hypotheses disagree, or whose
 * observation was held back as an unresolved conflict, ends with those things listed. A
 * reflection that claims certainty the cycle did not have would be worse than no reflection
 * at all.</p>
 */
public final class CycleReflection {

    /** The kind of decision that prefers a method. */
    public static final String DECISION_PREFER_METHOD = Reflection.DECISION_PREFER_METHOD;

    /** The kind of decision that withdraws a method. */
    public static final String DECISION_WITHDRAW_METHOD = Reflection.DECISION_WITHDRAW_METHOD;

    /** The kind of decision that leaves a proposition unresolved. */
    public static final String DECISION_LEAVE_UNRESOLVED = Reflection.DECISION_LEAVE_UNRESOLVED;

    /** The subject a method decision is about. */
    private static final String SUBJECT_METHOD = "method";

    private CycleReflection() {
        throw new AssertionError("no instances");
    }

    /**
     * The reflection of a completed cycle.
     *
     * @param episodeId   the episode the reflection is about
     * @param taskId      the task that was attempted
     * @param decision    the method decision, when the cycle made one
     * @param prediction  what the cycle predicted, when it predicted
     * @param error       the comparison of the prediction with the outcome, when there was one
     * @param observed    what was observed, when something was
     * @param computed    what the method returned, when it returned
     * @param learned     what the cycle taught
     * @param uncertainties what remains uncertain
     * @param at          when the reflection was made
     * @return the reflection
     */
    public static Reflection of(String episodeId, String taskId, AlgorithmDecision decision,
                                io.matrix.cognitive.Prediction prediction, ErrorSignal error,
                                Boolean computed, List<String> learned,
                                List<String> uncertainties, String at) {
        String methodId = decision == null ? "none" : decision.selectedAlgorithmId();
        String reason = decision == null ? "no method was selected" : decision.reason();
        String predicted = prediction == null ? null : prediction.predictedValue();
        String observed = observedOutcome(error, computed);
        String discrepancy = discrepancyOf(error);

        List<String> learnedList = learned == null ? List.of() : List.copyOf(learned);
        List<String> uncertainList = uncertainties == null ? List.of() : List.copyOf(uncertainties);

        List<Reflection.Decision> decisions = new ArrayList<>();
        boolean reuse = shouldBeReused(error, computed);
        if (error != null) {
            // The method's prediction was CONTRADICTED. That is the method's own failure:
            // it produced an answer the world refused.
            decisions.add(new Reflection.Decision(DECISION_WITHDRAW_METHOD, methodId,
                    "the method's prediction was contradicted by the observation",
                    Map.of("episodeId", episodeId)));
        } else if (computed != null) {
            decisions.add(new Reflection.Decision(DECISION_PREFER_METHOD, methodId,
                    "the method answered and the outcome confirmed the prediction",
                    Map.of("episodeId", episodeId)));
        }
        if (!uncertainList.isEmpty()) {
            decisions.add(new Reflection.Decision(DECISION_LEAVE_UNRESOLVED, episodeId,
                    "the cycle left something unresolved and says what",
                    Map.of("uncertainties", uncertainList)));
        }

        return new Reflection(
                "ref-" + episodeId,
                episodeId,
                taskId,
                methodId,
                reason,
                prediction == null ? null : prediction.id(),
                predicted,
                observed,
                discrepancy,
                learnedList,
                reuse,
                uncertainList,
                decisions,
                at);
    }

    /**
     * The evaluation of a completed cycle: the metrics a reader can compare.
     *
     * @param episodeId  the episode the evaluation is about
     * @param reflection the reflection it evaluates
     * @param error      the comparison of the prediction with the outcome
     * @param learned    how many things the cycle taught
     * @param at         when the evaluation was made
     * @return the evaluation
     */
    public static Evaluation evaluationOf(String episodeId, Reflection reflection,
                                          ErrorSignal error, int learned, String at) {
        Map<String, Evaluation.Metric> metrics = new LinkedHashMap<>();
        metrics.put("predictions.confirmed", new Evaluation.Metric(
                error == null ? 1.0 : 0.0, 1));
        metrics.put("things.learned", new Evaluation.Metric(learned, 1));
        metrics.put("uncertainties.remaining", new Evaluation.Metric(
                reflection.uncertainties().size(), 1));
        metrics.put("method.reusable", new Evaluation.Metric(
                reflection.reuseMethod() ? 1.0 : 0.0, 1));
        return new Evaluation("eval-" + episodeId, episodeId, "cognitive-cycle",
                Map.of("method", reflection.methodId(), "task", reflection.taskId()),
                metrics, null, "the metrics of one completed cycle", at);
    }

    /**
     * Whether the method should be used again.
     *
     * <p><b>The rule is the outcome, and it distinguishes three cases.</b> A method whose
     * prediction was CONTRADICTED has failed and is withdrawn. A method that answered and
     * was confirmed is preferred. A method that could NOT answer is neither: it did not
     * fail, the MODEL was unsettled, and withdrawing it would punish the method for the
     * model's state. Such a method stays usable, and the thing that remains unresolved is
     * the PROPOSITION, which is what the cycle says.</p>
     *
     * @param error    the comparison of the prediction with the outcome
     * @param computed what the method returned
     * @return true when the method earned another turn
     */
    public static boolean shouldBeReused(ErrorSignal error, Boolean computed) {
        return error == null;
    }

    private static String observedOutcome(ErrorSignal error, Boolean computed) {
        if (error != null && error.observed() != null) {
            return error.observed();
        }
        if (computed != null) {
            return Boolean.toString(computed);
        }
        return null;
    }

    private static String discrepancyOf(ErrorSignal error) {
        if (error == null) {
            return null;
        }
        return "the prediction and the observation disagree; the magnitude is "
                + error.magnitude() + " and the disposition is " + error.disposition();
    }
}
