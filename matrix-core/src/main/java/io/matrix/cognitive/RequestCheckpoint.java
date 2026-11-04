package io.matrix.cognitive;

/**
 * W45.1 — the durable checkpoints of ONE request execution.
 *
 * <h2>Why a checkpoint exists</h2>
 * <p>A request that dies leaves a record behind. The record says how far the request got, and
 * that is the only thing that decides whether a retry may repeat the work. A request that had
 * not written anything may be executed again from the beginning; a request that had committed
 * an observation may NOT be executed again, because the second execution would commit the same
 * observation twice.</p>
 *
 * <p>Each checkpoint is written DURABLY, in the same store and the same transaction as the work
 * it describes, before the work continues. A checkpoint therefore never claims more than the
 * store has already committed: the checkpoint is at most as advanced as the durable state.</p>
 *
 * <h2>The order of the checkpoints</h2>
 * <p>The enumeration order IS the order of the lifecycle, and the first checkpoint that implies
 * an irreversible effect is {@link #OBSERVATION_COMMITTED}: from that point on the epistemic
 * store holds an observation that a repeated execution would duplicate.</p>
 */
public enum RequestCheckpoint {

    /** The key is claimed and the identities are allocated. Nothing has been written yet. */
    CLAIMED(false),

    /** The episode row exists. The episode is open and holds no prediction. */
    EPISODE_OPENED(false),

    /** The prediction was persisted. */
    PREDICTION_WRITTEN(false),

    /**
     * The world was probed.
     *
     * <p>For the micro-world the probe is a pure deterministic computation, so repeating it is
     * harmless. A future stateful environment must raise a barrier here.</p>
     */
    EXPERIMENT_EXECUTED(false),

    /**
     * The epistemic transaction committed: the case, its source, its evidence and the
     * disposition are on disk.
     *
     * <p>This is the point of no return. A repeated execution would commit the observation a
     * second time, so a request that reached this checkpoint may never be executed again
     * blindly.</p>
     */
    OBSERVATION_COMMITTED(true),

    /** The regulatory state was persisted. */
    REGULATION_PERSISTED(true),

    /** The terminal trace was written and the episode was closed. */
    EPISODE_CLOSED(true),

    /** The outcome snapshot was persisted. */
    OUTCOME_PERSISTED(true),

    /** The idempotency record was completed. The request is now replayable. */
    IDEMPOTENCY_COMPLETED(true);

    /** Whether reaching this checkpoint means an effect that a repeat would duplicate. */
    private final boolean irreversible;

    RequestCheckpoint(boolean irreversible) {
        this.irreversible = irreversible;
    }

    /**
     * Whether an execution that reached this checkpoint has already produced an effect that a
     * repeated execution would duplicate.
     *
     * @return true when the execution may NOT be repeated blindly
     */
    public boolean irreversible() {
        return irreversible;
    }

    /**
     * Whether this checkpoint is at or after the given one.
     *
     * @param other the checkpoint to compare with
     * @return true when this checkpoint is the given one or a later one
     */
    public boolean atOrAfter(RequestCheckpoint other) {
        return ordinal() >= other.ordinal();
    }

    /**
     * Whether reaching this checkpoint means that a durable effect already exists.
     *
     * @return true when the store holds something a repeated execution would write again
     */
    public boolean durableEffect() {
        return ordinal() >= EPISODE_OPENED.ordinal();
    }
}
