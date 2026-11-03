package io.matrix.cognitive.microworld;

/**
 * W45.0 — the epistemic disposition of an observation: may it constrain the active model?
 *
 * <p><b>Why this is not {@code Case.Origin}.</b> The origin of a case says WHERE the
 * observation came from — the system's own probe, somebody's report, a derivation, an injected
 * fault. The disposition says something entirely different: whether the observation is allowed
 * to CHANGE WHAT THE MODEL BELIEVES. A system-observed case can be eligible, unresolved or
 * rejected; a reported case can be eligible too. Overloading the origin would make two
 * unrelated questions share one field, and the second question could never be answered.</p>
 *
 * <p><b>Why the disposition is durable.</b> A contradiction that is deliberately kept out of
 * the active model must STAY out of it. Before W45 the knowledge store recorded that an
 * observation contradicted the model — as an evidence relation — but the micro-world's
 * restoration path never read it back, so a session rebuilt from the durable cases applied the
 * contradiction the live session had refused to apply. The disposition is the record that
 * survives a restart and says what the model's inclusion policy actually was.</p>
 *
 * <p><b>The states.</b></p>
 * <ul>
 *   <li>{@link #ELIGIBLE} — the observation constrains the active model. It was recorded and
 *       the model was built from it.</li>
 *   <li>{@link #UNRESOLVED} — the observation contradicts something the model believed and the
 *       cause has not been established. It is HELD: it is visible, it is durable, it is never
 *       deleted, and it does NOT constrain the model until it is adjudicated. This is the
 *       state a contradiction lives in.</li>
 *   <li>{@link #ACCEPTED} — a conflict was reviewed and the observation was accepted into the
 *       model. It constrains the model, and the record says that a review resolved it rather
 *       than that it was simply observed.</li>
 *   <li>{@link #REJECTED} — the observation was reviewed and REJECTED: it is kept in the audit
 *       history and it never constrains the model.</li>
 * </ul>
 *
 * <p><b>The conservative default.</b> A record whose disposition cannot be established — an
 * old case from before this field existed, with no evidence link to infer it from — is
 * {@link #UNRESOLVED}, not {@link #ELIGIBLE}. An observation that cannot be classified must
 * not silently become confirmed model data.</p>
 */
public enum ObservationDisposition {

    /** The observation constrains the active model. */
    ELIGIBLE,

    /**
     * The observation is held in an open conflict: it is recorded, durable and visible, and it
     * does not constrain the active model.
     */
    UNRESOLVED,

    /** A reviewed observation that was accepted into the active model. */
    ACCEPTED,

    /** A reviewed observation that was rejected: kept in the history, never in the model. */
    REJECTED;

    /**
     * Whether an observation in this disposition may constrain the active model.
     *
     * <p>This is the ONE authoritative rule for model eligibility. The codec that restores a
     * session, the session itself, the reference method and the promotion check all ask this
     * question, and they all get the same answer.</p>
     *
     * @return true when the observation may constrain the model
     */
    public boolean constrainsModel() {
        return this == ELIGIBLE || this == ACCEPTED;
    }

    /**
     * Whether the disposition describes a conflict that is still open.
     *
     * @return true when the observation is held pending adjudication
     */
    public boolean isHeld() {
        return this == UNRESOLVED || this == REJECTED;
    }

    /**
     * The disposition of a record that cannot be classified.
     *
     * <p>Used for the records of a database that predates this field. The rule is deliberately
     * asymmetric: an unknown disposition must not become model data.</p>
     *
     * @return {@link #UNRESOLVED}
     */
    public static ObservationDisposition conservativeDefault() {
        return UNRESOLVED;
    }
}
