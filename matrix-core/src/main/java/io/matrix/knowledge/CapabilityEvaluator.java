package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Derives a learner's position on §9's ladder for one concept, from the
 * per-dimension states and from the teach-back.
 *
 * <p>The per-dimension states are what §10 asks for; this is the ladder that
 * §9 asks for. The two are joined here and only here, so that the rule for
 * reaching UNDERSTANDING and KNOWLEDGE is written down in one place and can
 * be tested against the ones it is supposed to represent.
 *
 * <p>The rule, in full:
 *
 * <ul>
 *   <li><b>UNKNOWN</b> — no outcome has been recorded for any dimension of
 *       this concept.</li>
 *   <li><b>EXPLORATION</b> — outcomes exist and none of them was
 *       correct.</li>
 *   <li><b>INITIAL_LEARNING</b> — something was learned: at least one
 *       correct outcome, but not enough to be practising.</li>
 *   <li><b>PRACTICE AND APPLICATION</b> — the learner is applying the
 *       material, but not yet reliably across every dimension.</li>
 *   <li><b>COMPETENCE</b> — every dimension the assessment set covers for
 *       this concept has reached COMPETENCE.</li>
 *   <li><b>UNDERSTANDING</b> — competence, plus competence on the CAUSAL,
 *       TRANSFER and EVALUATION dimensions. §9 defines understanding as
 *       explaining causes, predicting outcomes and identifying
 *       counterexamples, so a high recall score does not reach it.</li>
 *   <li><b>KNOWLEDGE / TEACHABLE CAPABILITY</b> — understanding, plus a
 *       TEACH-BACK that met its declared expectation. §9: "Generating an
 *       explanation is not proof of teaching." The teach-back is
 *       machine-to-machine, and the record says so.</li>
 * </ul>
 *
 * <p>A stage is never reached by having no evidence against it: a concept
 * whose assessment set has no items for a dimension simply does not have
 * that dimension, and the missing dimensions are NAMED rather than counted
 * as failures.
 */
public final class CapabilityEvaluator {

    /**
     * The declared expectation of the teach-back that the highest stage
     * needs.
     *
     * <p>It is declared rather than derived, and the record carries it: a
     * teach-back that met a bar nobody wrote down proves nothing. A fifth
     * of the assessed content is a modest bar, chosen so that a lesson
     * that merely contains a little of the content does not qualify.
     */
    public static final double DEFAULT_EXPECTED_GAIN = 0.2;

    /** The dimensions that §9 names for understanding. */
    public static final List<LearningDimension> UNDERSTANDING_DIMENSIONS = List.of(
            LearningDimension.CAUSAL, LearningDimension.TRANSFER, LearningDimension.EVALUATION);

    private CapabilityEvaluator() {
    }

    /**
     * Derive the capability state of one concept.
     *
     * @param conceptId the concept
     * @param dimensionStates the per-dimension states, already evaluated
     * @param teachBack the teach-back result, or null if there has been none
     */
    public static CapabilityState evaluate(String conceptId,
                                           List<LearningState> dimensionStates,
                                           TeachBackEvaluator.TeachBackResult teachBack) {
        Map<LearningDimension, LearningStage> stages = new EnumMap<>(LearningDimension.class);
        int correctOutcomes = 0;
        int totalOutcomes = 0;
        for (LearningState state : dimensionStates) {
            stages.put(state.dimension(), state.stage());
            totalOutcomes += state.evidenceCount();
            // The calibration measures the learner's HONESTY, not their
            // knowledge: a learner who is wrong every time and claims
            // certainty has a calibration score of 0.5, not 0. Counting it
            // as "something was learned" would put a learner who has learnt
            // nothing into PRACTICE.
            if (state.dimension() != LearningDimension.CALIBRATION && state.score() > 0.0) {
                correctOutcomes++;
            }
        }

        List<String> missing = new ArrayList<>();
        for (LearningDimension dimension : stages.keySet()) {
            if (stages.get(dimension) != LearningStage.COMPETENCE) {
                missing.add(dimension.name());
            }
        }

        LearningStage stage;
        String note;

        if (totalOutcomes == 0) {
            stage = LearningStage.UNKNOWN;
            note = "No outcome has been recorded for this concept, so nothing is known about it.";
        } else if (correctOutcomes == 0) {
            stage = LearningStage.EXPLORATION;
            note = "Every recorded outcome was wrong, so the concept is still unexplored.";
        } else if (!missing.isEmpty()) {
            // Some dimensions are short of competence. Whether that is
            // practice or mere initial learning depends on how much of the
            // assessment set has been reached at all.
            boolean everyDimensionTouched = stages.size() >= 2
                    && stages.values().stream().allMatch(s -> s != LearningStage.UNKNOWN);
            stage = everyDimensionTouched ? LearningStage.PRACTICE : LearningStage.INITIAL_LEARNING;
            note = "Some dimensions are short of competence ("
                    + String.join(", ", missing)
                    + "), so the learner is still practising rather than competent.";
        } else {
            boolean understanding = UNDERSTANDING_DIMENSIONS.stream()
                    .allMatch(d -> stages.get(d) == LearningStage.COMPETENCE);
            boolean taught = teachBack != null && teachBack.metExpectation();
            if (!understanding) {
                stage = LearningStage.COMPETENCE;
                note = "Every assessed dimension has reached competence, but understanding also needs "
                        + "competence on the causal, transfer and evaluation dimensions, and those "
                        + "are not all there yet.";
            } else if (!taught) {
                stage = LearningStage.UNDERSTANDING;
                note = "The learner can explain causes, predict outcomes and identify "
                        + "counterexamples. The highest stage is not reached because there is no "
                        + "teach-back that met its expectation: generating an explanation is not "
                        + "proof of teaching.";
            } else {
                stage = LearningStage.KNOWLEDGE;
                note = "The learner is competent across the assessed dimensions, can explain causes, "
                        + "predict outcomes and identify counterexamples, and a teach-back met its "
                        + "declared expectation. The teach-back is machine-to-machine: it shows that "
                        + "the lesson CONTAINED the assessed content, not that a human learned it.";
            }
        }

        String teachBackConclusion = teachBack == null
                ? null
                : teachBack.conclusion();

        return new CapabilityState(conceptId, stage, stages, missing,
                teachBack != null && teachBack.metExpectation(), teachBackConclusion, note);
    }
}
