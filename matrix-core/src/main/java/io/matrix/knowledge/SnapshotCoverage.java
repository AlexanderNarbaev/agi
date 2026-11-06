package io.matrix.knowledge;

/**
 * W48-01 — what a snapshot's payload actually carries.
 *
 * <p>A snapshot payload is not "the workspace". It is the typed knowledge content plus
 * the rows of the tables that a replacement erases, and <b>which</b> of those tables it
 * carries depends on when it was written:</p>
 *
 * <ul>
 *   <li>a version-1 payload is the bare knowledge content — no observation history and no
 *       learning state;</li>
 *   <li>a version-2 payload carries the observation family;</li>
 *   <li>and since OD-036 (W47, 2026-11-05) it carries the learning family too.</li>
 * </ul>
 *
 * <p><b>Why the distinction is a safety mechanism and not a curiosity.</b> A restore
 * erases the workspace before it writes the payload back. If the payload does not carry
 * a family that the target holds, the restore destroys that family's rows and cannot
 * bring them back — a silent loss of learning history or of the adjudication record. So
 * the restore must know what the payload carries BEFORE it erases anything, and it must
 * refuse when the answer would cost the owner data.</p>
 *
 * <p>The coverage is derived from the payload's own table list. A family is "carried"
 * when the payload holds an entry for every table of that family — the entry may be
 * empty, because "the table was there and it had no rows" is a fact about the snapshot,
 * while "the table is not in the payload at all" is a fact about the BUILD that wrote
 * it.</p>
 */
public enum SnapshotCoverage {

    /** The payload is the typed knowledge content and nothing else. */
    KNOWLEDGE_ONLY,

    /** The payload carries the observation family, but not the learning family. */
    OBSERVATION_HISTORY,

    /** The payload carries the observation family AND the complete learning family. */
    FULL,

    /** The payload was written by a build whose coverage this build cannot describe. */
    UNKNOWN;

    /**
     * The tables of the observation family: the epistemic case and its history.
     *
     * <p>OD-025 put them in the erasure set, so a payload that does not carry them would
     * destroy the adjudication record of the observation it describes.</p>
     */
    public static final java.util.List<String> OBSERVATION_TABLES = java.util.List.of(
            "epistemic_case",
            "observation_occurrence",
            "observation_disposition",
            "observation_adjudication");

    /**
     * The tables of the learning family.
     *
     * <p>OD-036 put them in the erasure set, so a payload that does not carry them would
     * destroy the learner's history: the assessment items, the learning errors, the
     * learning states, the transitions, the outcomes, the dialogue turns, the asked
     * questions and the Hebbian bindings.</p>
     */
    public static final java.util.List<String> LEARNING_TABLES = java.util.List.of(
            "learning_transition",
            "learning_error",
            "assessment_outcome",
            "assessment_item",
            "learning_state",
            "dialogue_turn",
            "asked_question",
            "hebbian_binding");

    /**
     * The coverage of a payload that carries the named tables.
     *
     * @param tables the tables the payload holds an entry for
     * @return the coverage the payload has
     */
    public static SnapshotCoverage of(java.util.Collection<String> tables) {
        boolean observations = tables.containsAll(OBSERVATION_TABLES);
        boolean learning = tables.containsAll(LEARNING_TABLES);
        if (observations && learning) {
            return FULL;
        }
        if (observations) {
            return OBSERVATION_HISTORY;
        }
        return KNOWLEDGE_ONLY;
    }

    /**
     * The families this coverage does NOT carry.
     *
     * @return the names of the families a restore of this payload would destroy and could
     *         not bring back
     */
    public java.util.List<String> missingFamilies() {
        return switch (this) {
            case FULL -> java.util.List.of();
            case OBSERVATION_HISTORY -> java.util.List.of("the learning state");
            case KNOWLEDGE_ONLY -> java.util.List.of("the observation history", "the learning state");
            case UNKNOWN -> java.util.List.of("everything this build cannot describe");
        };
    }
}
