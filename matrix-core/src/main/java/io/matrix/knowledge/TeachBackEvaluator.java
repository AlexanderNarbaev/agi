package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the teaching experiment: teach a learner, then measure it
 * (owner directive §30, §48).
 *
 * <p>The measurement is made on the HELD-OUT items, which the teacher never
 * saw. The learner's score before the lesson and after the lesson are both
 * recorded, and the difference is the gain. The rule is: if the lesson does
 * not move the held-out score, the teaching did not work, whatever the
 * lesson looked like.
 *
 * <p>A declared expectation is part of the experiment, not an afterthought:
 * the caller states what the gain should be, and the evaluator reports
 * whether it happened. An experiment whose outcome is decided after the fact
 * is not an experiment.
 */
public final class TeachBackEvaluator {

    /** The outcome of one held-out item. */
    public record ItemOutcome(String itemId, String dimension, double before, double after,
                               boolean improved) {
    }

    /** The result of the whole experiment. */
    public record TeachBackResult(
            String conceptId,
            String datasetId,
            int datasetVersion,
            String levelTaughtAt,
            int itemsMeasured,
            double beforeScore,
            double afterScore,
            double gain,
            boolean metExpectation,
            String expectation,
            List<ItemOutcome> outcomes,
            String conclusion) {

        public TeachBackResult {
            outcomes = List.copyOf(outcomes == null ? List.of() : outcomes);
        }
    }

    /**
     * Teach the learner and measure it.
     *
     * @param expectedMinimumGain the gain the experiment expects; the result
     *     says whether it happened
     */
    public TeachBackResult run(TeachingPlan plan, AssessmentDataset dataset,
                               KnowledgeSnapshot snapshot, double expectedMinimumGain) {
        List<AssessmentItem> heldOut = dataset.heldOutItems().stream()
                .filter(item -> item.conceptId().equals(plan.conceptId()))
                .toList();
        if (heldOut.isEmpty()) {
            return new TeachBackResult(plan.conceptId(), dataset.id(), dataset.version(),
                    plan.targetLevel().name(), 0, 0.0, 0.0, 0.0, false,
                    "no held-out item measures this concept",
                    List.of(),
                    "The experiment could not be run: the evaluation set holds no item for "
                            + plan.conceptName() + ". Nothing is claimed.");
        }

        SyntheticLearner before = new SyntheticLearner();
        SyntheticLearner after = new SyntheticLearner();
        after.teach(plan);

        List<ItemOutcome> outcomes = new ArrayList<>();
        double beforeTotal = 0.0;
        double afterTotal = 0.0;
        for (AssessmentItem item : heldOut) {
            double beforeScore = AssessmentGrader.score(item, before.answer(item));
            double afterScore = AssessmentGrader.score(item, after.answer(item));
            beforeTotal += beforeScore;
            afterTotal += afterScore;
            outcomes.add(new ItemOutcome(item.id(), item.dimension().name(),
                    round(beforeScore), round(afterScore), afterScore > beforeScore));
        }

        double beforeMean = beforeTotal / heldOut.size();
        double afterMean = afterTotal / heldOut.size();
        double gain = round(afterMean - beforeMean);
        boolean met = gain >= expectedMinimumGain;

        String conclusion = met
                ? "The lesson moved the held-out score by " + gain + ", which met the declared "
                        + "expectation of " + expectedMinimumGain + ". This is a machine-to-machine "
                        + "result: it shows that the taught account contained the content the "
                        + "held-out items ask for, which is necessary for transfer and not "
                        + "sufficient for it."
                : "The lesson moved the held-out score by " + gain + ", below the declared "
                        + "expectation of " + expectedMinimumGain + ". The teaching did not confer "
                        + "the assessed capability at this level. This is a machine-to-machine "
                        + "result and says nothing about human teaching effectiveness.";

        return new TeachBackResult(plan.conceptId(), dataset.id(), dataset.version(),
                plan.targetLevel().name(), heldOut.size(), round(beforeMean), round(afterMean),
                gain, met, "gain >= " + expectedMinimumGain, outcomes, conclusion);
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
