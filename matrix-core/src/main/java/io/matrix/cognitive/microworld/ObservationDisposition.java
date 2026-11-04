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
 *
 * <h2>W45.1 — the state machine</h2>
 *
 * <p><b>Why the states need a machine and not a convention.</b> The W45.0 wave recorded the
 * disposition of a case in ONE row, and the micro-world derives a case id from the conditions
 * AND the observed outcome — so the SAME case comes back in a later episode. The production
 * path wrote that row with {@code INSERT OR REPLACE}, and the adjudication a review had taken
 * was therefore erased by the next observation of the same fact: the observation and its
 * evidence survived, and the DECISION about whether they may constrain the model did not. A
 * rule that is written down in prose and enforced nowhere is a rule that a re-entrant write
 * will break, so the rule lives in code: {@link #canTransitionTo(ObservationDisposition)} is
 * the whole transition table, and the store asks it before it moves a case.</p>
 *
 * <p><b>The transitions.</b></p>
 * <table border="1">
 *   <caption>The legal transitions of a canonical case's disposition</caption>
 *   <tr><th>from</th><th>to</th><th>legal</th></tr>
 *   <tr><td>(none)</td><td>ELIGIBLE</td><td>yes — the initial state of an ordinary observation</td></tr>
 *   <tr><td>(none)</td><td>UNRESOLVED</td><td>yes — a contradiction whose cause is unknown</td></tr>
 *   <tr><td>ELIGIBLE</td><td>UNRESOLVED</td><td>yes — a later observation contradicts a settled belief</td></tr>
 *   <tr><td>ELIGIBLE</td><td>ACCEPTED, REJECTED</td><td>yes — a review decides</td></tr>
 *   <tr><td>UNRESOLVED</td><td>ACCEPTED, REJECTED</td><td>yes — a review decides</td></tr>
 *   <tr><td>ACCEPTED</td><td>REJECTED</td><td>yes — a review changes its mind; the earlier decision survives in the history</td></tr>
 *   <tr><td>ACCEPTED</td><td>UNRESOLVED</td><td>yes — a review RE-OPENS it; the earlier acceptance survives in the history</td></tr>
 *   <tr><td>REJECTED</td><td>ELIGIBLE</td><td><b>NEVER</b> — a rejected observation must not become eligible merely because it was observed again</td></tr>
 *   <tr><td>REJECTED</td><td>UNRESOLVED</td><td>yes — a review re-opens it, and that transition is recorded</td></tr>
 *   <tr><td>REJECTED</td><td>ACCEPTED</td><td>yes — a review changes its mind, recorded</td></tr>
 *   <tr><td>any</td><td>any, without a review</td><td><b>NO</b> — re-observing never adjudicates</td></tr>
 * </table>
 *
 * <p><b>Why REJECTED cannot become ELIGIBLE.</b> ELIGIBLE is the state in which an observation
 * is ordinary model data. A review rejected it, so it is not model data, and no amount of
 * seeing the same thing again can make it so: the rejection was a judgement about the
 * observation, and the only thing that can overturn a judgement is another judgement. The
 * re-opening path — REJECTED to UNRESOLVED — exists so that a review can send it back to the
 * queue, and it is recorded, because a re-opened case is pending again and a reader has to be
 * able to see that it ever was rejected.</p>
 *
 * <p><b>Why UNRESOLVED cannot become ELIGIBLE.</b> That transition is not in the table for the
 * same reason the conservative default is UNRESOLVED: an observation whose cause is unknown
 * must not become model data by the passage of time. It becomes model data when a review
 * <em>accepts</em> it, and the record then says a review accepted it rather than that it was
 * simply observed.</p>
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
     * Whether the observation does not constrain the active model.
     *
     * <p><b>This is the model-eligibility rule, not a workflow state.</b> It answers one
     * question — may this observation constrain the model — and it answers it for two
     * dispositions that mean very different things to a workflow: UNRESOLVED, which is waiting
     * for a review, and REJECTED, which is a review's final word. A workflow that needs to
     * tell those two apart must not ask this question; it must ask
     * {@link #awaitingAdjudication()} or {@link #terminallyExcluded()}.</p>
     *
     * <p>The two questions exist because the W45.0 wave used this one method for both, and a
     * rejected observation was then indistinguishable from a pending one: a queue that asked
     * "is this held?" listed the rejected cases as if they were waiting for a review that had
     * already happened.</p>
     *
     * @return true when the observation may not constrain the model
     */
    public boolean isHeld() {
        return this == UNRESOLVED || this == REJECTED;
    }

    /**
     * Whether the observation is waiting for a review.
     *
     * <p>Only an UNRESOLVED observation awaits adjudication. A REJECTED one has already been
     * adjudicated, and treating it as pending would put a settled case back into the queue.</p>
     *
     * @return true when the observation is pending a review
     */
    public boolean awaitingAdjudication() {
        return this == UNRESOLVED;
    }

    /**
     * Whether the observation has been rejected for good.
     *
     * <p>Only a REJECTED observation is terminally excluded. It stays in the history and it
     * never constrains the model, but it is not pending: no review is outstanding.</p>
     *
     * @return true when the observation was rejected and the rejection stands
     */
    public boolean terminallyExcluded() {
        return this == REJECTED;
    }

    /**
     * Whether a case in this disposition may move to the given one.
     *
     * <p>This is the transition table of the javadoc, in code. A transition that is not legal
     * here is refused by the store, whatever the caller intends: the caller that wants a
     * rejected observation to become model data has to go through a review, and the review
     * writes a decision into the history.</p>
     *
     * <p>A transition to the SAME disposition is legal, and it is not a transition of the state:
     * a review that re-affirms what it found leaves the case where it is and records the
     * decision anyway. The history is what tells the two apart — a case with one row in it
     * was decided once, and a case with two rows was decided twice.</p>
     *
     * <p><b>Why the table is here and not in the store.</b> The disposition is the state, and
     * the state knows what may follow it. Putting the rule in the store would let a second
     * writer answer the question differently, which is how the W45.0 defect happened in the
     * first place: the write and the rule about the write were in different places, and only
     * one of them was consulted.</p>
     *
     * @param next the disposition the case would move to
     * @return true when the move is legal
     */
    public boolean canTransitionTo(ObservationDisposition next) {
        if (next == null) {
            return false;
        }
        if (this == next) {
            return true;
        }
        return switch (this) {
            // A settled belief can be contradicted later, and a review can decide.
            case ELIGIBLE -> next == UNRESOLVED || next == ACCEPTED || next == REJECTED;
            // A held contradiction can only be decided by a review.
            case UNRESOLVED -> next == ACCEPTED || next == REJECTED;
            // A review can change its mind, or re-open what it accepted.
            case ACCEPTED -> next == REJECTED || next == UNRESOLVED;
            // A rejection can be re-opened or overturned by a review, but never becomes
            // eligible on its own.
            case REJECTED -> next == UNRESOLVED || next == ACCEPTED;
        };
    }

    /**
     * Whether a case that carries NO disposition may be given this one as its FIRST state.
     *
     * <p>The initial write of an observation is not a decision: an ordinary observation starts
     * {@link #ELIGIBLE}, and an observation whose cause is unknown starts
     * {@link #UNRESOLVED}. A case cannot be born ACCEPTED or REJECTED, because those two
     * states say that a REVIEW happened, and the birth of the case is not a review.</p>
     *
     * @param disposition the disposition the case would start in
     * @return true when the disposition may be the first state of a case
     */
    public static boolean canBeInitialDisposition(ObservationDisposition disposition) {
        return disposition == ELIGIBLE || disposition == UNRESOLVED;
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
