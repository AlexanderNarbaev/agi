package io.matrix.autonomy;

import io.matrix.lifecycle.AutonomyImpulse;

import java.util.Objects;

/**
 * A bounded internal proposal — the record an accepted autonomy impulse becomes.
 *
 * <p>This is the W49 implementation of
 * {@code docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md} §5, built
 * under the owner's decision OD-045 (option A).
 *
 * <p><b>What a proposal is.</b> A typed record of an intent, with provenance, a
 * budget, a lifetime and a terminal outcome. It is Chain B's own record and it
 * is written inside Chain B.
 *
 * <p><b>What a proposal is not.</b> It is not an executor. Nothing in this class
 * sends a message, runs a command, touches an external service, or writes to
 * Chain A. The terminal state {@link ProposalState#ACCEPTED_FOR_REVIEW} means
 * "recorded and waiting for a human, or for a later wave that carries its own
 * mandate" — it does not mean that the work happened.
 */
public record ImpulseProposal(String id,
                              AutonomyImpulse impulse,
                              ProposalKind kind,
                              String cycle,
                              String source,
                              long envelopeSpent,
                              int rowsAllocated,
                              double shadowPrice,
                              long expiresAtNanos,
                              ProposalState state) {

    public ImpulseProposal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(impulse, "impulse");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(cycle, "cycle");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(state, "state");
        if (envelopeSpent < 0) {
            throw new IllegalArgumentException("envelopeSpent must be ≥ 0");
        }
        if (rowsAllocated < 0) {
            throw new IllegalArgumentException("rowsAllocated must be ≥ 0");
        }
        if (shadowPrice < 0.0 || !Double.isFinite(shadowPrice)) {
            throw new IllegalArgumentException("shadowPrice must be finite and ≥ 0");
        }
    }

    /**
     * The kind of work a proposal asks for — a closed enumeration, one kind per
     * canonical impulse. It is closed on purpose: an open kind would let the
     * vocabulary grow without a decision.
     */
    public enum ProposalKind {
        CURIOSITY_EXPLORATION,
        MEMORY_CONSOLIDATION,
        INTEGRITY_VERIFICATION,
        DIGEST_SHARE;

        /** The kind that corresponds to the given impulse. */
        public static ProposalKind of(AutonomyImpulse impulse) {
            Objects.requireNonNull(impulse, "impulse");
            return switch (impulse) {
                case CURIOSITY -> CURIOSITY_EXPLORATION;
                case CONSOLIDATION -> MEMORY_CONSOLIDATION;
                case INTEGRITY_CHECK -> INTEGRITY_VERIFICATION;
                case SHARE_DIGEST -> DIGEST_SHARE;
            };
        }
    }

    /**
     * The states of a proposal.
     *
     * <p>{@link #PROPOSED} is <b>not</b> terminal — a proposal is still open
     * while it waits. Every other state is terminal and observable.
     */
    public enum ProposalState {
        /** Recorded and still open. Not a terminal state. */
        PROPOSED,
        /** The proposal outlived its expiry and was closed by the sweep. */
        EXPIRED,
        /** An identical proposal was already live; this one was refused. */
        DUPLICATE,
        /** The bounded queue was full; the proposal was refused, not blocked. */
        REFUSED_QUEUE_FULL,
        /** The FROZEN ethical gate refused it — at creation or at execution. */
        REFUSED_FROZEN,
        /** The scheduler's allocation produced no work to propose. */
        REFUSED_BUDGET,
        /** Recorded and waiting for a human, or for a later mandated wave. */
        ACCEPTED_FOR_REVIEW;

        /** Whether this state is terminal. */
        public boolean terminal() {
            return this != PROPOSED;
        }
    }

    /**
     * The deduplication key: the impulse, the cycle and the kind.
     *
     * <p>A proposal identical to a live one is refused. The key contains no wall
     * clock and no randomness, so the same three inputs always produce the same
     * key.
     */
    public String dedupKey() {
        return impulse.name() + '|' + cycle + '|' + kind.name();
    }
}
