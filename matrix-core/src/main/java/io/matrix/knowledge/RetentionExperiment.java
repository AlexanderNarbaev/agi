package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The retention baseline experiment (RECON-W42.5).
 *
 * <p>The mission's §14 asks that "the delayed retention is measured and
 * improved over a no-consolidation baseline". Measuring it needs two arms
 * and one instrument:
 *
 * <ul>
 *   <li>the <b>baseline arm</b> — the exercises are asked in the dataset's
 *       order, each item at most twice;</li>
 *   <li>the <b>consolidation arm</b> — the exercises are chosen by the
 *       {@link ExerciseSelector}, which asks the wrongly answered items
 *       again and works on the dimensions that have not reached
 *       competence.</li>
 * </ul>
 *
 * <p><b>What the instrument measures, and what it does not.</b> It measures
 * the <b>selection policy</b> of the system, under a scripted learner. The
 * learner is a deterministic function — it answers an item wrongly the
 * first time and rightly the second — and it is the same in both arms.
 * The comparison therefore says which policy leads the same learner to a
 * better state in fewer sessions. It does <b>not</b> say anything about a
 * person, and the report of it must not be read as a claim about human
 * learning.
 *
 * <p><b>No randomness</b> (the CONSTITUTION's first article): the learner
 * is a fixed rule, and every run of the experiment gives the same numbers.
 */
public final class RetentionExperiment {

    /** How many items a session asks in either arm. */
    public static final int ITEMS_PER_SESSION = 5;

    /** Which policy chooses the exercises. */
    public enum Policy {
        /** The dataset's order: every item at most twice. */
        BASELINE_ORDER,
        /** The system's own selection. */
        CONSOLIDATION_SELECTED
    }

    /** What one arm did. */
    public record ArmResult(
            Policy policy,
            int sessions,
            int itemsAsked,
            int itemsWrongAtTheEnd,
            int itemsRight,
            LearningStage weakestStage,
            double meanScore,
            String retentionStage,
            int retentionEvidence,
            boolean retentionMeasured) {

        public ArmResult {
            policy = policy == null ? Policy.BASELINE_ORDER : policy;
            weakestStage = weakestStage == null ? LearningStage.UNKNOWN : weakestStage;
            retentionStage = retentionStage == null ? LearningStage.UNKNOWN.name() : retentionStage;
        }
    }

    /** The comparison of the two arms. */
    public record Report(
            ArmResult baseline,
            ArmResult consolidation,
            int itemsInTheDataset,
            String whatWasMeasured,
            List<String> limits) {

        public Report {
            whatWasMeasured = whatWasMeasured == null ? "" : whatWasMeasured;
            limits = List.copyOf(limits == null ? List.of() : limits);
        }

        /** How many sessions the consolidation arm needed fewer than the baseline. */
        public int sessionsSaved() {
            return baseline.sessions() - consolidation.sessions();
        }

        /** How many items the consolidation arm left wrong fewer. */
        public int wrongItemsAvoided() {
            return baseline.itemsWrongAtTheEnd() - consolidation.itemsWrongAtTheEnd();
        }
    }

    private RetentionExperiment() {
    }

    /**
     * Runs the experiment.
     *
     * @param workbench the workspace
     * @param workspaceId the workspace
     * @param maxSessions how many sessions each arm may run
     * @return the report
     */
    public static Report run(WorkbenchFactory factory, String workspaceId, int maxSessions) {
        // EACH ARM GETS ITS OWN WORKSPACE. The first version of this ran
        // both arms over one workspace, and the second arm inherited the
        // first arm's outcomes - so the comparison was between an arm and
        // an arm-plus-its-predecessor.
        try (KnowledgeWorkbench baselineWorkbench = factory.create()) {
            // The experiment SETS UP its workspace: it means the load, so it says so. W47
            // made the door ask, and an experiment that loads the package into a workspace
            // that already holds it is a deliberate replacement.
            baselineWorkbench.loadBundledPackage(workspaceId, true);
            ArmResult baseline = runArm(baselineWorkbench, workspaceId, Policy.BASELINE_ORDER,
                    maxSessions);
            try (KnowledgeWorkbench consolidationWorkbench = factory.create()) {
                consolidationWorkbench.loadBundledPackage(workspaceId, true);
                ArmResult consolidation = runArm(consolidationWorkbench, workspaceId,
                        Policy.CONSOLIDATION_SELECTED, maxSessions);
                return report(baseline, consolidation, consolidationWorkbench, workspaceId);
            }
        }
    }

    /**
     * Makes a workspace for one arm.
     *
     * <p>The factory is what keeps the arms apart.
     */
    @FunctionalInterface
    public interface WorkbenchFactory {
        /** A fresh workspace. */
        KnowledgeWorkbench create();
    }

    private static Report report(ArmResult baseline, ArmResult consolidation,
                                 KnowledgeWorkbench workbench, String workspaceId) {
        return new Report(baseline, consolidation, workbench.dataset(workspaceId).items().size(),
                "the selection policy of the system, under a deterministic scripted learner "
                        + "that answers an item wrongly the first time and rightly the second. "
                        + "The learner is the same in both arms, so the difference is the policy.",
                List.of(
                        "This is a measurement of the SYSTEM's selection policy, not of a person. "
                                + "The learner is a scripted rule and the numbers say nothing about "
                                + "human learning.",
                        "The dataset holds " + workbench.dataset(workspaceId).items().size()
                                + " items. A number measured over a set of this size is a "
                                + "demonstration, not a generalisation.",
                        "The delayed retention is measured from the outcomes of the sessions before "
                                + "the last one, so it is only measured when the arm ran at least "
                                + "two sessions."));
    }

    /**
     * Runs one arm.
     *
     * @param workbench the workspace
     * @param workspaceId the workspace
     * @param policy the policy that chooses the exercises
     * @param maxSessions how many sessions the arm may run
     * @return what the arm did
     */
    public static ArmResult runArm(KnowledgeWorkbench workbench, String workspaceId,
                                    Policy policy, int maxSessions) {
        List<AssessmentItem> items = workbench.dataset(workspaceId).items();
        int asked = 0;
        int right = 0;
        Set<String> askedIds = new LinkedHashSet<>();
        Map<String, String> lastAsked = new java.util.LinkedHashMap<>();
        List<String> sessions = new ArrayList<>();

        for (int session = 1; session <= maxSessions; session++) {
            // The session number is padded, so that the lexical order of
            // the session ids is the order of the sessions.
            String sessionId = "exp-" + policy.name() + "-" + String.format("%03d", session);
            sessions.add(sessionId);
            // The items asked IN THIS SESSION. The retry belongs to the
            // next session: the learner needs the interval, and asking the
            // same item twice in a row would measure nothing.
            Set<String> inSession = new LinkedHashSet<>();
            List<String> toAsk = policy == Policy.BASELINE_ORDER
                    ? nextInOrder(items, askedIds, inSession, ITEMS_PER_SESSION, lastAsked)
                    : nextBySelection(workbench, workspaceId, inSession);
            if (toAsk.isEmpty()) {
                break;
            }
            for (String itemId : toAsk) {
                asked++;
                // THE SCRIPTED LEARNER: the first attempt is wrong, the
                // second is right. The same rule in both arms.
                boolean correct = askedIds.contains(itemId);
                workbench.assess(workspaceId, itemId, correct ? answerOf(items, itemId) : "wrong",
                        0.5, sessionId);
                if (correct) {
                    right++;
                }
            }
            for (String itemId : toAsk) {
                askedIds.add(itemId);
                inSession.add(itemId);
                lastAsked.put(itemId, sessionId);
            }
            // CONVERGENCE: the arm stops when every item has been answered
            // correctly at its last attempt. The number of sessions it
            // took to get there is the measurement - a run that is capped
            // at a fixed number of sessions says nothing about how long
            // the policy takes.
            if (allAnsweredCorrectly(workbench, workspaceId, items)) {
                break;
            }
        }

        // THE LAST OUTCOME OF EACH ITEM, and not every outcome: an item
        // asked three times leaves three records, and counting the first
        // one as well would measure the history rather than the state.
        // The order is the SESSION, not the clock. The whole experiment
        // runs inside a millisecond, so the store's ordering by the
        // wall-clock instant cannot say which outcome came last - and a
        // measurement that picks an arbitrary one is not a measurement.
        java.util.Map<String, AssessmentOutcome> lastByItem = new java.util.LinkedHashMap<>();
        for (AssessmentOutcome outcome : workbench.outcomes(workspaceId)) {
            AssessmentOutcome current = lastByItem.get(outcome.itemId());
            if (current == null
                    || compareSessions(outcome.sessionId(), current.sessionId()) >= 0) {
                lastByItem.put(outcome.itemId(), outcome);
            }
        }
        List<AssessmentOutcome> last = new ArrayList<>(lastByItem.values());
        double mean = last.stream().mapToDouble(AssessmentOutcome::score).average().orElse(0.0);
        int wrong = (int) last.stream().filter(outcome -> !outcome.correct()).count();

        LearningStage weakest = LearningStage.UNKNOWN;
        double weakestScore = Double.MAX_VALUE;
        for (LearningState state : workbench.learningStates(workspaceId)) {
            if (state.score() < weakestScore) {
                weakestScore = state.score();
                weakest = state.stage();
            }
        }

        String retentionStage = LearningStage.UNKNOWN.name();
        int retentionEvidence = 0;
        boolean measured = false;
        for (LearningState state : workbench.learningStates(workspaceId)) {
            if (state.dimension() == LearningDimension.RETENTION) {
                retentionStage = state.stage().name();
                retentionEvidence = state.evidenceCount();
                measured = state.stage() != LearningStage.UNKNOWN;
            }
        }

        return new ArmResult(policy, sessions.size(), asked, wrong, right, weakest, mean,
                retentionStage, retentionEvidence, measured);
    }

    /**
     * The next items of the dataset's order.
     *
     * <p>The baseline asks the items it has not asked yet, and only when
     * they are all asked does it come back to the ones the learner got
     * wrong. That is the honest baseline: it is the same learner and the
     * same number of items per session, and the difference is WHEN the
     * failed items come back. The first version of this asked everything
     * at once and never retried, which made the comparison a strawman.
     *
     * @param items the dataset
     * @param askedIds the items the learner has been asked
     * @param inSession the items asked in this session
     * @param limit how many to ask
     * @return the items to ask
     */
    private static List<String> nextInOrder(List<AssessmentItem> items, Set<String> askedIds,
                                            Set<String> inSession, int limit,
                                            Map<String, String> lastAsked) {
        List<String> next = new ArrayList<>();
        for (AssessmentItem item : items) {
            if (!askedIds.contains(item.id()) && !inSession.contains(item.id())) {
                next.add(item.id());
                if (next.size() >= limit) {
                    return next;
                }
            }
        }
        // Everything has been asked once: the ones asked LEAST RECENTLY
        // come back. The first version took them in the dataset's order,
        // and the same five items were retried in every session while the
        // other seventeen were never retried - a baseline that never
        // learned them, which is not a fair comparison.
        List<AssessmentItem> byAge = new ArrayList<>();
        for (AssessmentItem item : items) {
            if (askedIds.contains(item.id()) && !inSession.contains(item.id())) {
                byAge.add(item);
            }
        }
        byAge.sort((left, right) -> lastAskedOf(left, lastAsked).compareTo(
                lastAskedOf(right, lastAsked)));
        for (AssessmentItem item : byAge) {
            next.add(item.id());
            if (next.size() >= limit) {
                return next;
            }
        }
        return next;
    }

    /** When an item was last asked, read from the outcomes. */
    private static String lastAskedOf(AssessmentItem item, Map<String, String> lastAsked) {
        return lastAsked.getOrDefault(item.id(), "");
    }

    /**
     * The next items the system's own selection chooses.
     *
     * <p>The first version of this filtered out every item the learner had
     * ever been asked, and that defeated the error-driven selection: the
     * items the learner had failed were exactly the ones it was asked
     * again. The filter belongs to the SESSION, not to the whole run.
     */
    private static List<String> nextBySelection(KnowledgeWorkbench workbench, String workspaceId,
                                                Set<String> inSession) {
        List<String> next = new ArrayList<>();
        for (ExerciseSelector.Selection selection : workbench.nextExercises(workspaceId,
                ITEMS_PER_SESSION)) {
            if (!inSession.contains(selection.item().id())) {
                next.add(selection.item().id());
            }
        }
        return next;
    }

    /**
     * Whether every item has been answered correctly at its last attempt.
     *
     * @param workbench the workspace
     * @param workspaceId the workspace
     * @return true when there is nothing left to get right
     */
    private static boolean allAnsweredCorrectly(KnowledgeWorkbench workbench, String workspaceId,
                                                List<AssessmentItem> items) {
        Map<String, Boolean> last = new java.util.LinkedHashMap<>();
        for (AssessmentOutcome outcome : workbench.outcomes(workspaceId)) {
            last.put(outcome.itemId(), outcome.correct());
        }
        if (last.isEmpty()) {
            return false;
        }
        // EVERY item of the dataset, not only the ones that were asked:
        // the first version of this counted the asked items alone, and the
        // arm declared itself converged after two sessions with five items
        // of the dataset untouched.
        for (AssessmentItem item : items) {
            Boolean correct = last.get(item.id());
            if (correct == null || !correct) {
                return false;
            }
        }
        return true;
    }

    /** The number of the session, read from the session id. */
    private static int sessionNumber(String sessionId) {
        if (sessionId == null) {
            return 0;
        }
        int dash = sessionId.lastIndexOf('-');
        if (dash < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(sessionId.substring(dash + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The order of two sessions, by their number. */
    private static int compareSessions(String left, String right) {
        return Integer.compare(sessionNumber(left), sessionNumber(right));
    }

    /** The answer that the grader scores as correct for an item. */
    private static String answerOf(List<AssessmentItem> items, String itemId) {
        for (AssessmentItem item : items) {
            if (item.id().equals(itemId)) {
                return String.join(" ", item.keyTerms());
            }
        }
        return "";
    }
}
