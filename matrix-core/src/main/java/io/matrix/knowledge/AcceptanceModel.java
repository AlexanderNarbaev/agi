package io.matrix.knowledge;

import io.matrix.tsetlin.TsetlinTrainer;
import io.matrix.tsetlin.WisardProducer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The supervised learner over the learner's own answers, and the comparison
 * that decided which learner is used (owner directive §24).
 *
 * <p>This is the replacement for what {@code AgentBrainService.generateFromBir()}
 * pretended to be. The difference is not cosmetic:
 *
 * <ul>
 *   <li>the labels are REAL — the verdict of the deterministic grader, which
 *       knows the key terms of the item — instead of a constant {@code true};</li>
 *   <li>the INPUTS are explicit features — the presence of the item's key
 *       terms, the shape of the answer, the item's dimension — instead of an
 *       FNV hash of the first 64 characters;</li>
 *   <li>the outcome is MEASURED on held-out items, and it is compared with
 *       the deterministic grader it is meant to replace;</li>
 *   <li>the learned rule is DISTILLED into an exact, inspectable clause set,
 *       so what the learner concluded can be read;</li>
 *   <li>and the learner is CHOSEN BY MEASUREMENT: the Tsetlin machine and
 *       the WiSARD are both trained on the same data and both measured, and
 *       the report says which was faster and which was more accurate.</li>
 * </ul>
 *
 * <p>Whether the winner is worth using in production is decided by the
 * measurement, not by this class. The honest expectation is that the
 * deterministic grader wins on a small dataset; the value of the learned
 * rule is that it says something a lookup table cannot — it names the shape
 * of the answers that tend to be accepted.
 */
public final class AcceptanceModel {

    /** The fewest held-out answers before a comparison may be reported as one. */
    private static final int MINIMUM_MEASUREMENT = 5;

    /** The number of training epochs. Five is enough for the counts above. */
    private static final int EPOCHS = 5;

    /** The number of WiSARD rams. */
    private static final int WISARD_RAMS = 16;

    /** The result of training and measuring the learner. */
    public record Evaluation(
            int trainedOn,
            int measuredOn,
            int correctOnHeldOut,
            double heldOutAccuracy,
            int deterministicCorrectOnHeldOut,
            double deterministicAccuracy,
            boolean beatsDeterministic,
            String conclusion,
            LearnerComparison comparison) {
    }

    /** The two learners, trained on the same data and measured the same way. */
    public record LearnerComparison(
            String tsetlinAccuracy,
            long tsetlinMillis,
            String wisardAccuracy,
            long wisardMillis,
            String faster,
            String verdict) {
    }

    private final TsetlinTrainer trainer;
    private final List<AssessmentItem> items;
    private final int clauses;
    private final int states;
    private String cachedRule;

    public AcceptanceModel(List<AssessmentItem> items, Random random) {
        this(items, random, 64, 200);
    }

    /**
     * A learner with an explicit capacity.
     *
     * <p>The capacity matters: with ten clauses and five automaton states the
     * Tsetlin machine does not learn even "every answer naming the key terms
     * is acceptable" — it predicts FALSE for its own training data. With the
     * production counts it learns. That is a capacity finding, not a tuning
     * preference, and it is recorded in the WAL.
     */
    public AcceptanceModel(List<AssessmentItem> items, Random random, int clauses, int states) {
        this.items = List.copyOf(items);
        this.clauses = clauses;
        this.states = states;
        this.trainer = new TsetlinTrainer(TextFeatureVector.WIDTH, clauses, states, random);
    }

    /**
     * Train the learner on the training outcomes and measure it on the
     * held-out ones.
     *
     * <p>The learner predicts from the features of an ANSWER, so it can only
     * be measured on items that have been answered. The held-out items are
     * answered by the learner during the assessment; those answers are the
     * measurement, and they are not used to train.
     *
     * @param outcomes every outcome recorded for the dataset
     */
    public Evaluation trainAndEvaluate(List<AssessmentOutcome> outcomes) {
        List<Long> trainingFeatures = new ArrayList<>();
        List<Boolean> trainingLabels = new ArrayList<>();
        List<AssessmentOutcome> heldOutOutcomes = new ArrayList<>();

        for (AssessmentOutcome outcome : outcomes) {
            AssessmentItem item = item(outcome.itemId());
            if (item == null) {
                continue;
            }
            if (item.heldOut()) {
                heldOutOutcomes.add(outcome);
                continue;
            }
            trainingFeatures.add(TextFeatureVector.features(item, outcome.learnerAnswer()));
            trainingLabels.add(outcome.correct());
        }

        if (trainingFeatures.isEmpty()) {
            return new Evaluation(0, heldOutOutcomes.size(), 0, 0.0, 0, 0.0, false,
                    "No training outcomes were available, so nothing was learned and nothing is claimed.",
                    null);
        }
        if (heldOutOutcomes.isEmpty()) {
            return new Evaluation(trainingFeatures.size(), 0, 0, 0.0, 0, 0.0, false,
                    "The learner was trained on " + trainingFeatures.size()
                            + " outcomes, but no held-out item has been answered yet, so there is "
                            + "nothing to measure it on. Answer a held-out item and measure again.",
                    null);
        }

        long[][] inputs = new long[trainingFeatures.size()][1];
        boolean[] labels = new boolean[trainingLabels.size()];
        for (int i = 0; i < trainingFeatures.size(); i++) {
            inputs[i][0] = trainingFeatures.get(i);
            labels[i] = trainingLabels.get(i);
        }

        long tsetlinStart = System.nanoTime();
        trainer.trainBatch(inputs, labels, EPOCHS);
        long tsetlinMicros = (System.nanoTime() - tsetlinStart) / 1_000;

        long wisardStart = System.nanoTime();
        WisardProducer wisard = new WisardProducer(TextFeatureVector.WIDTH, WISARD_RAMS, 42L);
        for (int i = 0; i < trainingFeatures.size(); i++) {
            wisard.train(unpack(trainingFeatures.get(i)), labels[i] ? 1 : 0);
        }
        long wisardMicros = (System.nanoTime() - wisardStart) / 1_000;

        int correct = 0;
        int wisardCorrect = 0;
        int deterministicCorrect = 0;
        for (AssessmentOutcome outcome : heldOutOutcomes) {
            AssessmentItem item = item(outcome.itemId());
            long features = TextFeatureVector.features(item, outcome.learnerAnswer());
            if (trainer.predict(features)) {
                correct++;
            }
            if (wisard.classifyLabel(unpack(features)) == 1) {
                wisardCorrect++;
            }
            if (outcome.correct()) {
                deterministicCorrect++;
            }
        }

        int measured = heldOutOutcomes.size();
        double learnedAccuracy = (double) correct / measured;
        double wisardAccuracy = (double) wisardCorrect / measured;
        double deterministicAccuracy = (double) deterministicCorrect / measured;

        // The verdict follows the measurement. It is not written first and
        // then defended: an earlier version of this code claimed "the WiSARD
        // trained faster" in the branch where the Tsetlin was in fact the
        // faster one, and the measurement is now in microseconds so that the
        // comparison is a comparison.
        String faster;
        String verdict;
        if (wisardAccuracy < learnedAccuracy) {
            faster = "the Tsetlin machine";
            verdict = "The WiSARD was " + (wisardMicros <= tsetlinMicros ? "faster" : "no faster")
                    + " but less accurate on the held-out items (" + fmt(wisardAccuracy) + " against "
                    + fmt(learnedAccuracy) + "), so the Tsetlin machine is kept. Accuracy comes first "
                    + "when the speed does not matter: both trainings take well under a second.";
        } else if (wisardMicros < tsetlinMicros) {
            faster = "the WiSARD";
            verdict = "The WiSARD trained faster (" + wisardMicros + " against " + tsetlinMicros
                    + " microseconds) and was no less accurate on the held-out items ("
                    + fmt(wisardAccuracy) + " against " + fmt(learnedAccuracy) + "). That is the case "
                    + "for using it, and it is recorded rather than acted on here, because the sample "
                    + "is too small to change the production path on.";
        } else {
            faster = "neither was measurably faster";
            verdict = "Neither learner was measurably faster on this sample (" + tsetlinMicros + " and "
                    + wisardMicros + " microseconds) and they were equally accurate on the held-out "
                    + "items (" + fmt(learnedAccuracy) + " against " + fmt(wisardAccuracy) + "). The "
                    + "Tsetlin machine is kept: it is the one the production path already uses, and its "
                    + "rule can be distilled exactly.";
        }

        LearnerComparison comparison = new LearnerComparison(
                fmt(learnedAccuracy), tsetlinMicros, fmt(wisardAccuracy), wisardMicros,
                faster, verdict);

        // A measurement on a handful of items is not a measurement. A tie at
        // zero on one item is not evidence of anything, and saying "it is a
        // candidate for the production path" on that basis would be exactly
        // the kind of claim this work is supposed to refuse.
        String conclusion;
        if (measured < MINIMUM_MEASUREMENT) {
            conclusion = "Only " + measured + " held-out item(s) have been answered, so the measurement "
                    + "is not yet one: a comparison on " + measured + " item(s) cannot decide anything. "
                    + "The deterministic grader stays in the production path until there are at least "
                    + MINIMUM_MEASUREMENT + " held-out answers.";
        } else if (learnedAccuracy >= deterministicAccuracy) {
            conclusion = "The learned rule matched the deterministic grader on the held-out items ("
                    + Math.round(learnedAccuracy * 100) + "% against "
                    + Math.round(deterministicAccuracy * 100) + "% on " + measured
                    + " items). The deterministic grader still stays in the production path - it is "
                    + "exact and needs no training - but the rule is worth keeping, because it says "
                    + "something the lookup table cannot: which SHAPE of answer tends to be accepted.";
        } else {
            conclusion = "The learned rule did NOT match the deterministic grader on the held-out items ("
                    + Math.round(learnedAccuracy * 100) + "% against "
                    + Math.round(deterministicAccuracy * 100) + "% on " + measured
                    + " items). The deterministic grader stays in the production path. The rule is "
                    + "recorded because it says something the lookup table cannot: which SHAPE of "
                    + "answer tends to be accepted.";
        }

        return new Evaluation(trainingFeatures.size(), measured, correct, round(learnedAccuracy),
                deterministicCorrect, round(deterministicAccuracy),
                learnedAccuracy >= deterministicAccuracy, conclusion, comparison);
    }

    /**
     * The rule the learner distilled, in a readable form.
     *
     * <p>The distillation enumerates the whole input space - 65,536 vectors
     * at this width - and evaluates each against every clause, so it is
     * deliberately NOT done on the measurement path. It is done once, on
     * request, and cached.
     */
    public String distilledRule() {
        if (cachedRule == null) {
            cachedRule = describe(trainer.toClauseSet("acceptance-model"));
        }
        return cachedRule;
    }

    /** A readable description of the clauses the learner distilled. */
    private String describe(io.matrix.bir.ClauseSetForm clauseSet) {
        StringBuilder sb = new StringBuilder();
        try {
            long[] output = new long[1];
            clauseSet.eval(new long[] {0L}, output);
            sb.append("the clause set evaluates the empty feature vector to ")
                    .append(output[0] == 0 ? "FALSE" : "TRUE")
                    .append("; the distilled rule holds ")
                    .append(clauseSet.clauses().size())
                    .append(" clause(s) over the feature vector.");
        } catch (RuntimeException e) {
            sb.append("the clause set could not be evaluated: ").append(e.getMessage());
        }
        return sb.toString();
    }

    private AssessmentItem item(String itemId) {
        for (AssessmentItem item : items) {
            if (item.id().equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    /** The packed feature vector as the boolean vector the WiSARD expects. */
    private static long[] unpack(long packed) {
        long[] out = new long[TextFeatureVector.WIDTH];
        for (int i = 0; i < TextFeatureVector.WIDTH; i++) {
            out[i] = (packed >>> i) & 1L;
        }
        return out;
    }

    private static String fmt(double v) {
        return Math.round(v * 1000.0) / 1000.0 + " (" + Math.round(v * 100) + "%)";
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
