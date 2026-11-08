package io.matrix.contract;

import java.util.List;
import java.util.Map;

/**
 * W51 — the system's account of a cycle it has just run.
 *
 * <p><b>Reflection is a decision, not a paragraph.</b> The W51 requirement is explicit:
 * "Reflection must not simply generate additional text about the result. It must produce
 * machine-readable changes or decisions that can be evaluated." This record therefore ends in
 * decisions: a method-selection preference, a belief revision, a skill promotion, a
 * procedure withdrawal — each of which is a {@link Decision} that another part of the system
 * can carry out and that an evaluation can check.</p>
 *
 * <p><b>The nine questions the mission asks are nine fields.</b> What task was attempted
 * ({@link #taskId()}), which method was selected ({@link #methodId()}), why it was selected
 * ({@link #methodSelectionReason()}), what was predicted ({@link #predictionId()} and
 * {@link #predictedOutcome()}), what happened ({@link #observedOutcome()}), what differed
 * ({@link #discrepancy()}), what was learned ({@link #learned()}), whether the method should be
 * reused ({@link #reuseMethod()}), and what remains uncertain ({@link #uncertainties()}).</p>
 *
 * <p><b>An honest reflection may conclude that nothing was learned.</b> The record does not
 * require a lesson: a cycle in which the prediction was right and nothing changed says so,
 * and that is a different — and useful — piece of information from a cycle in which the
 * prediction was wrong.</p>
 *
 * @param id                  the identity of the reflection
 * @param episodeId           the episode the reflection is about
 * @param taskId              the task that was attempted
 * @param methodId            the method that was selected
 * @param methodSelectionReason why the method was selected
 * @param predictionId        the prediction that was made
 * @param predictedOutcome    what was expected
 * @param observedOutcome     what happened
 * @param discrepancy         how the outcome differed from the prediction
 * @param learned             what the cycle taught, or an empty list when it taught nothing
 * @param reuseMethod         whether the method should be used again
 * @param uncertainties       what remains uncertain
 * @param decisions           the machine-readable decisions this reflection produced
 * @param at                  when the reflection was made
 */
public record Reflection(
        String id,
        String episodeId,
        String taskId,
        String methodId,
        String methodSelectionReason,
        String predictionId,
        String predictedOutcome,
        String observedOutcome,
        String discrepancy,
        List<String> learned,
        boolean reuseMethod,
        List<String> uncertainties,
        List<Decision> decisions,
        String at) {

    /** A decision a reflection produced, ready to be carried out and checked. */
    public record Decision(
            String kind,
            String subjectId,
            String rationale,
            Map<String, Object> parameters) {

        public Decision {
            if (kind == null || kind.isBlank()) {
                throw new IllegalArgumentException("decision kind must not be blank");
            }
            if (subjectId == null || subjectId.isBlank()) {
                throw new IllegalArgumentException("decision subject id must not be blank");
            }
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    /** The kind of a decision that changes a method-selection preference. */
    public static final String DECISION_PREFER_METHOD = "PREFER_METHOD";
    /** The kind of a decision that withdraws a method. */
    public static final String DECISION_WITHDRAW_METHOD = "WITHDRAW_METHOD";
    /** The kind of a decision that revises a belief. */
    public static final String DECISION_REVISE_BELIEF = "REVISE_BELIEF";
    /** The kind of a decision that promotes a skill. */
    public static final String DECISION_PROMOTE_SKILL = "PROMOTE_SKILL";
    /** The kind of a decision that leaves a belief unresolved. */
    public static final String DECISION_LEAVE_UNRESOLVED = "LEAVE_UNRESOLVED";

    public Reflection {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("reflection id must not be blank");
        }
        if (episodeId == null || episodeId.isBlank()) {
            throw new IllegalArgumentException("reflection episode id must not be blank");
        }
        learned = learned == null ? List.of() : List.copyOf(learned);
        uncertainties = uncertainties == null ? List.of() : List.copyOf(uncertainties);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }

    /**
     * A reflection that changed nothing.
     *
     * @param id         the identity of the reflection
     * @param episodeId  the episode the reflection is about
     * @param taskId     the task that was attempted
     * @param methodId   the method that was selected
     * @param reason     why the method was selected
     * @param at         when the reflection was made
     * @return the reflection
     */
    public static Reflection learnedNothing(String id, String episodeId, String taskId,
                                            String methodId, String reason, String at) {
        return new Reflection(id, episodeId, taskId, methodId, reason, null, null, null, null,
                List.of(), true, List.of(), List.of(), at);
    }

    /** True when the cycle taught the system something it did not know. */
    public boolean learnedSomething() {
        return !learned.isEmpty();
    }

    /** True when the prediction was wrong. */
    public boolean predictionWrong() {
        return discrepancy != null && !discrepancy.isBlank();
    }

    /** The decisions of a given kind. */
    public List<Decision> decisionsOfKind(String kind) {
        return decisions.stream().filter(d -> d.kind().equals(kind)).toList();
    }

    @Override
    public String toString() {
        return "Reflection[" + id + " episode=" + episodeId + " learned=" + learned.size() + "]";
    }
}
