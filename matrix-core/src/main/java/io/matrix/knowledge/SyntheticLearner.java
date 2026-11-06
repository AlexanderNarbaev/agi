package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * A machine learner that can be taught and then measured
 * (owner directive §30, §48).
 *
 * <p>This is a SYNTHETIC learner. The owner directive is explicit that a
 * result obtained with one must be described as machine-to-machine teaching
 * and not as proven human teaching effectiveness, and nothing here claims
 * otherwise.
 *
 * <p>What the experiment actually measures, stated precisely: after being
 * taught a plan, the learner can answer an assessment only out of the
 * accounts it was given. Its score therefore measures whether the lesson
 * CONTAINED the content the assessment asks for. That is a necessary
 * condition for transfer and a far from sufficient one — a human may
 * understand an account without reusing its wording, and a machine may
 * reproduce the wording without understanding it. Both directions of that
 * gap are real, and the experiment is reported with the gap named.
 *
 * <p>The learner holds only what it was taught. It does not consult the
 * workspace it was taught from, so a question it cannot answer from the
 * lesson is answered by abstention rather than by the teacher's own store.
 */
public final class SyntheticLearner {

    private final List<TeachingStep> learnedSteps = new ArrayList<>();

    /** Teach this learner a plan. */
    public void teach(TeachingPlan plan) {
        learnedSteps.addAll(plan.steps());
    }

    /** What this learner has been taught. */
    public List<String> taughtConcepts() {
        return learnedSteps.stream().map(TeachingStep::conceptId).distinct().toList();
    }

    /** The accounts this learner holds. */
    public List<String> learnedAccounts() {
        return learnedSteps.stream().map(TeachingStep::statement).toList();
    }

    /**
     * Answer an assessment item using only what was taught.
     *
     * <p>A learner that was taught a sequence of accounts at several levels
     * can draw on all of them: the introductory account and the mechanistic
     * one are both part of what it knows.
     *
     * @return the answer, or an empty string when the learner cannot answer
     */
    public String answer(AssessmentItem item) {
        List<String> parts = new ArrayList<>();
        for (TeachingStep step : learnedSteps) {
            parts.add(step.statement());
        }
        return String.join(" ", parts);
    }
}
