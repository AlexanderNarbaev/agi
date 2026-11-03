package io.matrix.cognitive.microworld;

/**
 * W44.4 — what the micro-world answered when it was asked.
 *
 * <p>An observation is a fact about ONE probe: the assignment that was tried, the value the
 * outcome variable took, and the moment the caller says it was taken. It carries no
 * interpretation — nothing here says what the answer MEANS. The comparison of an observation
 * with what the learner believed is the learner's work, and it happens in
 * {@link MicroWorldSession}, not in this record.</p>
 *
 * <p><b>Why {@code observedAt} is a caller-supplied string.</b> The session has no clock: a clock
 * read inside a decision path makes the decision depend on when it was taken rather than on what
 * was true (CONSTITUTION Article I). The caller, which already has the instant for other reasons,
 * supplies it as an ISO-8601 string, and the record never reads one itself.</p>
 *
 * <p><b>Why {@code adversarial} exists.</b> An observation can be produced by a FAULT-INJECTING
 * probe, which deliberately answers inconsistently with the world's behaviour. Such an observation
 * is a fact about the instrument, not about the world, and the session has to be able to tell the
 * two apart: a world that contradicts itself has to be reported, not silently averaged away. The
 * flag is set by the environment that produced the observation and is never set by the learner.</p>
 *
 * <p><b>Why {@code disposition} exists (W45.0).</b> An observation also carries its
 * <i>epistemic</i> state: whether it is allowed to constrain the active model. A contradiction
 * that the session deliberately refused to apply must stay refused after a restart, so the
 * decision has to travel with the observation and has to survive in the durable record. A fresh
 * observation starts {@link ObservationDisposition#ELIGIBLE} — the session decides; an
 * observation that arrives already carrying a held disposition is not applied.</p>
 */
public record Observation(String id, WorldInput input, boolean lamp, boolean adversarial,
                           String observedAt, ObservationDisposition disposition) {

    public Observation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("an observation id must not be blank");
        }
        if (input == null) {
            throw new IllegalArgumentException("an observation must name the assignment it was made under");
        }
        if (observedAt == null || observedAt.isBlank()) {
            throw new IllegalArgumentException("an observation must say when it was taken");
        }
        if (disposition == null) {
            throw new IllegalArgumentException("an observation must carry a disposition");
        }
    }

    /**
     * An observation that has not been adjudicated yet.
     *
     * <p>This is the W44 shape of the record, kept so that the existing callers and tests keep
     * working. Such an observation is eligible: the session, not the constructor, decides
     * whether it may constrain the model.</p>
     *
     * @param id          the identifier
     * @param input       the assignment it was made under
     * @param lamp        the value the outcome variable took
     * @param adversarial whether the probe was fault-injecting
     * @param observedAt  when it was taken
     */
    public Observation(String id, WorldInput input, boolean lamp, boolean adversarial,
                       String observedAt) {
        this(id, input, lamp, adversarial, observedAt, ObservationDisposition.ELIGIBLE);
    }

    /** The same observation, carrying a different disposition. */
    public Observation withDisposition(ObservationDisposition newDisposition) {
        return new Observation(id, input, lamp, adversarial, observedAt, newDisposition);
    }

    /** Whether this observation may constrain the active model. */
    public boolean constrainsModel() {
        return disposition.constrainsModel();
    }

    /** The canonical key of the assignment this observation was made under. */
    public String assignmentKey() {
        return input.key();
    }

    @Override
    public String toString() {
        return "Observation[" + id + " " + input.key() + " lamp=" + lamp
                + (adversarial ? " ADVERSARIAL" : "") + "]";
    }
}
