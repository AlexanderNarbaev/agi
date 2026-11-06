package io.matrix.autonomy;

import java.util.List;
import java.util.Optional;

/**
 * Where a bounded internal proposal is recorded.
 *
 * <p>This is the ONLY thing the impulse consumer writes. The store is the seam
 * the safety invariant S1 of
 * {@code docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md} rests on:
 * "no proposal creates an external effect".
 *
 * <p>The interface carries no persistence contract of its own. The first
 * implementation is {@link BoundedImpulseProposalStore}, which keeps the
 * proposals in memory; a durable store is a later change with its own mandate.
 */
public interface ImpulseProposalStore {

    /**
     * Record a proposal.
     *
     * @param proposal the proposal to record
     * @return {@code true} when it was recorded; {@code false} when the store
     *         refused it — the store is bounded and it refuses rather than
     *         growing or blocking
     */
    boolean record(ImpulseProposal proposal);

    /**
     * The proposal with the given id, whatever its state.
     *
     * @param id the proposal id
     * @return the proposal, or empty when the store has never seen it
     */
    Optional<ImpulseProposal> byId(String id);

    /**
     * Whether a live (still open) proposal carries the given deduplication key.
     *
     * @param dedupKey the key, {@link ImpulseProposal#dedupKey()}
     * @return {@code true} when an identical proposal is still open
     */
    boolean hasOpen(String dedupKey);

    /** The proposals that are still open, in the order they were recorded. */
    List<ImpulseProposal> open();

    /** Every proposal the store still holds, open and terminal alike. */
    List<ImpulseProposal> all();

    /**
     * Replace a proposal with its terminal version.
     *
     * @param id    the proposal id
     * @param state the terminal state
     * @return the terminal proposal, or empty when the store has never seen it
     */
    Optional<ImpulseProposal> close(String id, ImpulseProposal.ProposalState state);

    /** The number of proposals that are still open. */
    int openCount();

    /** The capacity of the open queue. */
    int capacity();
}
