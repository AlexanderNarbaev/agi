package io.matrix.knowledge;

/**
 * What a counterexample does to a claim (RECON-W43.0).
 *
 * <p>The mission's §4B asks the system to determine whether a
 * counterexample refutes the claim, reveals an exception, reveals a missing
 * condition, exposes an incorrect interpretation, exposes an invalid
 * inference, or is itself unreliable or ambiguous — and says plainly:
 * <b>"Do not automatically choose one explanation."</b>
 *
 * <p>So this vocabulary holds only what the system can decide from what it
 * has, and the assessment carries the REST as
 * {@link ClaimAssessment#alternativeExplanations()}. The system that
 * silently picks one of six explanations is the system that is confidently
 * wrong.
 *
 * <p><b>What the system can decide.</b> It has the claim's proposition and
 * scope, and the observation's text and — when the observer recorded one —
 * the condition under which it contradicts. From those it can tell:
 *
 * <ul>
 *   <li>{@link #REFUTES} — the observation contradicts the claim <b>as the
 *       claim states it</b>: the claim asserts without qualification, and
 *       the observation contradicts that assertion.</li>
 *   <li>{@link #REVEALS_MISSING_CONDITION} — the observation contradicts the
 *       claim <b>under a stated condition</b>. The claim is not thereby
 *       false; it is incomplete, and the condition it does not state is
 *       what the observation shows.</li>
 *   <li>{@link #INCONCLUSIVE} — the system cannot decide, because the
 *       observation records no condition and the claim states no scope, so
 *       there is nothing to read the observation against.</li>
 * </ul>
 *
 * <p><b>What the system does NOT decide</b>, and reports as candidates:
 * that the claim merely has an exception; that the system's own reading of
 * the source was wrong; that the inference from the evidence was invalid;
 * or that the observation itself is unreliable or ambiguous. Those need a
 * judgement the text does not contain, and they are listed rather than
 * resolved.
 */
public enum CounterexampleReading {

    /** The observation contradicts the claim as stated. */
    REFUTES,

    /** The observation contradicts the claim under a condition it does not state. */
    REVEALS_MISSING_CONDITION,

    /** The system cannot decide, and says which explanations it cannot exclude. */
    INCONCLUSIVE;

    /**
     * The explanations the system lists when it cannot decide.
     *
     * <p>These are the remaining readings of the mission's six. They are
     * carried with the assessment so that a reader can see that the system
     * considered them and did not settle them.
     */
    public static java.util.List<String> unresolvedExplanations() {
        return java.util.List.of(
                "the claim has an exception the counterexample reveals",
                "the system's interpretation of the source was wrong",
                "the inference from the evidence was invalid",
                "the counterexample itself is unreliable or ambiguous");
    }
}
