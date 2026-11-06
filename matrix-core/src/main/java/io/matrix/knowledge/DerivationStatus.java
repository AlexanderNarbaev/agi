package io.matrix.knowledge;

/**
 * Whether a claim that was INFERRED actually follows from its premises.
 *
 * <p>RECON-W41. A warrant says where a claim came from; this says whether
 * the reasoning that produced it is sound. The distinction is the one the
 * research on truth maintenance and Nyaya both insist on: a derivation can
 * be <b>valid</b> (the conclusion follows) while its premises are
 * <b>false</b>, and a derivation can be <b>invalid</b> (the conclusion does
 * not follow) while everything it started from was true.
 *
 * <p>Conflating the two is how a system ends up asserting things it has not
 * established. So the store keeps them apart:
 *
 * <ul>
 *   <li>{@link #NOT_DERIVED} — the claim was not produced by inference.</li>
 *   <li>{@link #DERIVED_VALID} — the conclusion follows from the recorded
 *       premises. Validity only; the premises are judged separately.</li>
 *   <li>{@link #DERIVED_INVALID} — the conclusion does not follow.</li>
 *   <li>{@link #UNDECIDED} — the question could not be settled within the
 *       checker's budget. This is an honest answer, not a failure.</li>
 * </ul>
 */
public enum DerivationStatus {
    NOT_DERIVED,
    DERIVED_VALID,
    DERIVED_INVALID,
    UNDECIDED
}
