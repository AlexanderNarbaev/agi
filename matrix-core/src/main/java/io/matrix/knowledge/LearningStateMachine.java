package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The learning lifecycle as a state machine (RECON-W42.1).
 *
 * <p>The mission's §7 names seven stages and says the lifecycle is
 * revisable: a previously mastered concept may return to PRACTICE when new
 * evidence contradicts the model, when retention fails, when transfer fails,
 * when a more precise abstraction is discovered, or when a previously hidden
 * limitation is identified.
 *
 * <p><b>Why this class exists.</b> RECON-W41 computed a stage with two
 * threshold functions over an append-only outcome log. A recomputation can
 * tell you what the state is now; it cannot tell you when it changed, what
 * changed it, or what the state was before — so the history the mission
 * requires ("preserve earlier mastery records as historical evidence") had
 * nowhere to live, and a regression was a label rather than an event.
 *
 * <p><b>The legal transitions.</b> A capability advances one stage at a time
 * along the ladder, and may fall back to PRACTICE — or to whatever stage the
 * evidence supports — when the evidence contradicts the model. Two rules
 * make the machine honest:
 *
 * <ul>
 *   <li>a transition needs a <b>cause</b>: an outcome, or a named reason. A
 *       re-labelling with no cause is not a transition;</li>
 *   <li>a <b>regression</b> is recorded as such and never hidden: the earlier
 *       mastery record stays in the log, and the transition says why.</li>
 * </ul>
 *
 * <p>The machine is deterministic: the same evidence produces the same
 * transitions, and the transition's identity is derived from its content, so
 * reconciling the same state twice records nothing new.
 */
public final class LearningStateMachine {

    /** The stages a capability may advance through, in order. */
    private static final List<LearningStage> LADDER = List.of(
            LearningStage.UNKNOWN, LearningStage.EXPLORATION, LearningStage.INITIAL_LEARNING,
            LearningStage.PRACTICE, LearningStage.COMPETENCE, LearningStage.UNDERSTANDING,
            LearningStage.KNOWLEDGE);

    /**
     * Whether a move from one stage to another is legal.
     *
     * <p>Advancing more than one stage at a time is not legal: the stages
     * are evidence thresholds, and a jump means the intermediate evidence
     * was never gathered. Falling back is legal from any stage, because the
     * evidence may contradict any model.
     */
    public static boolean legal(LearningStage from, LearningStage to) {
        if (from == null || to == null) {
            return false;
        }
        if (to == from) {
            return false;
        }
        if (to.index() > from.index()) {
            return to.index() == from.index() + 1;
        }
        return true;
    }

    /**
     * The stage a capability may fall back to when the evidence contradicts
     * its model.
     *
     * <p>PRACTICE is the floor of a contradiction: a capability whose
     * evidence no longer supports it has not become UNKNOWN — it is being
     * re-practised. UNKNOWN is only for a capability with no evidence at
     * all.
     */
    public static LearningStage regressionFloor() {
        return LearningStage.PRACTICE;
    }

    /**
     * Reconciles the computed states against the recorded ones and returns
     * the transitions that happened.
     *
     * @param workspaceId the workspace the states belong to
     * @param computed the states as the evidence currently reads them
     * @param recorded the states as they were last recorded, keyed by
     *     {@code conceptId + "|" + dimension}
     * @param outcomeId the outcome that caused the reconciliation, or null
     * @param occurredAt the moment of the reconciliation
     * @return the transitions, in the order they happened
     */
    public List<LearningTransition> reconcile(String workspaceId,
                                              List<LearningState> computed,
                                              Map<String, LearningState> recorded,
                                              String outcomeId, String occurredAt) {
        List<LearningTransition> transitions = new ArrayList<>();
        Map<String, LearningState> current = new LinkedHashMap<>();
        for (LearningState state : computed) {
            String key = key(state.conceptId(), state.dimension());
            LearningState before = recorded.get(key);
            // A capability with no recorded state yet is at UNKNOWN: the
            // ladder's floor. The first evidence may carry it several rungs
            // up, and the transition says so rather than inventing a
            // staircase of transitions that never happened.
            LearningStage from = before == null ? LearningStage.UNKNOWN : before.stage();
            if (before == null || before.stage() != state.stage()) {
                transitions.add(new LearningTransition(
                        Ids.digest(workspaceId + "|" + key + "|" + from.name()
                                + "|" + state.stage().name() + "|"
                                + (outcomeId == null ? "" : outcomeId) + "|" + occurredAt),
                        workspaceId, state.conceptId(), state.dimension(),
                        from, state.stage(),
                        reason(before, state, outcomeId), outcomeId, occurredAt));
            }
            current.put(key, state);
        }
        return transitions;
    }

    /** Why the state moved, in words a reader can check. */
    private String reason(LearningState before, LearningState after, String outcomeId) {
        if (before == null) {
            StringBuilder first = new StringBuilder("the first evidence placed the capability at "
                    + after.stage().name());
            if (after.stage().index() > LearningStage.UNKNOWN.index() + 1) {
                first.append(" - the evidence carried it several stages at once");
            }
            if (outcomeId != null) {
                first.append(" (outcome ").append(outcomeId).append(')');
            }
            return first.toString();
        }
        if (after.stage().index() < before.stage().index()) {
            StringBuilder reason = new StringBuilder(
                    "the most recent evidence contradicts the earlier model");
            if (outcomeId != null) {
                reason.append(" (outcome ").append(outcomeId).append(')');
            }
            if (after.criticalFailures() > 0) {
                reason.append("; ").append(after.criticalFailures())
                        .append(" critical item(s) failed");
            }
            return reason.toString();
        }
        StringBuilder reason = new StringBuilder("the evidence now meets the criteria for "
                + after.stage().name());
        if (outcomeId != null) {
            reason.append(" (outcome ").append(outcomeId).append(')');
        }
        return reason.toString();
    }

    /** The key a state is recorded under. */
    public static String key(String conceptId, LearningDimension dimension) {
        return conceptId + "|" + dimension.name();
    }

    /** The stages a capability may advance through. */
    public static List<LearningStage> ladder() {
        return LADDER;
    }
}
