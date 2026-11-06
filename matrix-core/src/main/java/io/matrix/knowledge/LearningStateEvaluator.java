package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives the learning state of a concept and dimension from recorded
 * outcomes (owner directive §9, §10, §28, §31).
 *
 * <p>Two rules govern everything here.
 *
 * <p><b>Evidence, not declaration.</b> A stage is reached because outcomes
 * met stated criteria. Nothing in this class can be told "the learner is
 * competent"; it can only be shown which outcomes moved it and how.
 *
 * <p><b>Regression must be visible.</b> When an outcome that contradicts an
 * earlier success arrives, the state falls and the note says so. Keeping
 * the old label would be the easy thing to do and the dishonest one.
 */
public final class LearningStateEvaluator {

    private final List<AssessmentItem> items;
    private final Set<String> itemIds = new HashSet<>();

    public LearningStateEvaluator(List<AssessmentItem> items) {
        this.items = List.copyOf(items);
        for (AssessmentItem item : this.items) {
            if (!itemIds.add(item.id())) {
                throw new IllegalArgumentException("duplicate assessment item id: " + item.id());
            }
        }
    }

    /**
     * The state of a concept in a dimension.
     *
     * @param outcomes every outcome recorded for that concept and dimension,
     *     in the order they happened
     */
    public LearningState evaluate(String conceptId, LearningDimension dimension,
                                  List<AssessmentOutcome> outcomes,
                                  MasteryCriteria criteria) {
        // Calibration is a property of the learner, not of one dimension: it
        // is measured over every outcome recorded for the concept.
        boolean calibration = dimension == LearningDimension.CALIBRATION;
        // RETENTION IS THE DELAYED EVIDENCE (RECON-W42.4).
        //
        // The mission's §7.4 asks for "delayed retrieval, after a meaningful
        // interval" and the dataset holds no item of the RETENTION
        // dimension at all - so the dimension had no criterion and could
        // never be anything but UNKNOWN.
        //
        // The retention evidence is the learner's performance BEFORE the
        // most recent session: the outcomes of the sessions that came
        // before it. The learner has practised since, and what the earlier
        // performance says about the material now is the retention. The
        // delay is a property of the DATA - the session boundary - and not
        // of a clock, which the CONSTITUTION's first article forbids in a
        // decision path.
        boolean retention = dimension == LearningDimension.RETENTION;
        Set<String> earlierSessions = retention ? sessionsBeforeTheLatest(outcomes) : Set.of();
        List<AssessmentOutcome> relevant = outcomes.stream()
                .filter(o -> itemIds.contains(o.itemId()))
                .filter(o -> {
                    AssessmentItem item = item(o.itemId());
                    if (item == null || !item.conceptId().equals(conceptId)) {
                        return false;
                    }
                    if (retention) {
                        return earlierSessions.contains(o.sessionId());
                    }
                    return calibration || item.dimension() == dimension;
                })
                .toList();

        if (relevant.isEmpty()) {
            return LearningState.unknown(conceptId, dimension);
        }

        double mean = calibration
                ? calibrationScore(relevant)
                : relevant.stream().mapToDouble(AssessmentOutcome::score).average().orElse(0.0);
        Set<String> variants = new LinkedHashSet<>();
        int heldOut = 0;
        int criticalFailures = 0;
        List<String> evidenceIds = new ArrayList<>();
        for (AssessmentOutcome outcome : relevant) {
            AssessmentItem item = item(outcome.itemId());
            variants.add(item.variant());
            evidenceIds.add(outcome.itemId());
            if (item.heldOut()) {
                heldOut++;
            }
            if (item.critical() && !outcome.correct()) {
                criticalFailures++;
            }
        }

        LearningStage stage = stageFor(mean, relevant.size(), variants.size(), heldOut,
                criticalFailures, criteria);
        String note = noteFor(stage, mean, relevant.size(), variants.size(), heldOut,
                criticalFailures, relevant);

        return new LearningState(conceptId, dimension, stage, Math.round(mean * 1000.0) / 1000.0,
                relevant.size(), variants.size(), heldOut, criticalFailures, evidenceIds, note);
    }

    /**
     * The calibration score: one minus the mean distance between the
     * confidence the learner claimed and the outcome it actually got.
     *
     * <p>A learner who is always right and always sure scores 1. A learner
     * who is right half the time and claims certainty every time scores 0.
     */
    private static double calibrationScore(List<AssessmentOutcome> outcomes) {
        double error = 0.0;
        for (AssessmentOutcome outcome : outcomes) {
            double actual = outcome.correct() ? 1.0 : 0.0;
            error += Math.abs(outcome.claimedConfidence() - actual);
        }
        return 1.0 - error / outcomes.size();
    }

    /**
     * The sessions that came before the most recent one.
     *
     * <p>The order of the sessions is the order the outcomes were recorded
     * in, and the session ids are compared in that order — a session id is
     * an opaque string, so the order comes from the outcomes themselves.
     *
     * @param outcomes the outcomes
     * @return the session ids before the last one
     */
    private Set<String> sessionsBeforeTheLatest(List<AssessmentOutcome> outcomes) {
        List<String> order = new ArrayList<>();
        for (AssessmentOutcome outcome : outcomes) {
            if (!order.contains(outcome.sessionId())) {
                order.add(outcome.sessionId());
            }
        }
        if (order.size() <= 1) {
            return Set.of();
        }
        return new LinkedHashSet<>(order.subList(0, order.size() - 1));
    }

    private LearningStage stageFor(double mean, int count, int variants, int heldOut,
                                   int criticalFailures, MasteryCriteria criteria) {
        if (count == 0) {
            return LearningStage.UNKNOWN;
        }
        boolean competent = count >= criteria.minimumItems()
                && variants >= criteria.minimumVariants()
                && heldOut >= criteria.minimumHeldOutItems()
                && mean >= criteria.competenceThreshold()
                && (!criteria.requireNoCriticalFailure() || criticalFailures == 0);
        if (!competent) {
            // Below the competence bar there are still two distinguishable
            // states: nothing has been shown yet, or something has.
            return mean > 0.0 ? LearningStage.PRACTICE : LearningStage.EXPLORATION;
        }
        // Competence is reached on the evidence criteria alone. Understanding
        // is a different claim about a different dimension and is judged in
        // its own right, not inferred from a high score here.
        return LearningStage.COMPETENCE;
    }

    private String noteFor(LearningStage stage, double mean, int count, int variants, int heldOut,
                           int criticalFailures, List<AssessmentOutcome> relevant) {
        StringBuilder note = new StringBuilder();
        note.append(count).append(" outcome").append(count == 1 ? "" : "s")
                .append(" across ").append(variants).append(" variant")
                .append(variants == 1 ? "" : "s")
                .append(", mean score ").append(Math.round(mean * 100)).append("%");
        if (heldOut > 0) {
            note.append(", ").append(heldOut).append(" held out");
        }
        if (criticalFailures > 0) {
            note.append(", ").append(criticalFailures).append(" critical failure")
                    .append(criticalFailures == 1 ? "" : "s");
        }
        if (stage == LearningStage.COMPETENCE) {
            note.append(". The acceptance criteria are met.");
        } else {
            note.append(". The acceptance criteria are not yet met, so the state is ")
                    .append(stage.name().toLowerCase(java.util.Locale.ROOT)).append(".");
        }
        AssessmentOutcome last = relevant.get(relevant.size() - 1);
        if (!last.correct() && relevant.stream().anyMatch(AssessmentOutcome::correct)) {
            note.append(" The most recent outcome contradicts an earlier success, so the state was lowered.");
        }
        return note.toString();
    }

    private AssessmentItem item(String itemId) {
        for (AssessmentItem item : items) {
            if (item.id().equals(itemId)) {
                return item;
            }
        }
        return null;
    }
}
