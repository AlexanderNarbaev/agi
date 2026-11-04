package io.matrix.cognitive;

/**
 * W45.1 — the hook through which a request reports its durable progress.
 *
 * <h2>Why the hook exists</h2>
 * <p>Two things need to know where a request has got to: the recovery logic, which must decide
 * whether a retry may repeat the work, and the integration harness, which must be able to stop
 * the process at an exact point. Both are the same need, so there is one hook.</p>
 *
 * <p>The hook is called at a PHASE BOUNDARY, after the work of that phase has been committed
 * and before the next phase starts. A listener therefore never observes a half-written state:
 * whatever the listener is told has already happened.</p>
 *
 * <h2>What a listener may do</h2>
 * <p>A listener may record the checkpoint durably, and it may throw. A throw from the listener
 * stops the request at that boundary, which is exactly what a fault-injection test needs — the
 * durable state at the moment of the throw is the state the recovery will find.</p>
 *
 * <p>The production wiring uses {@link #NO_OP}: the production behaviour must not depend on
 * timing, on sleeps, or on an administrative endpoint.</p>
 */
@FunctionalInterface
public interface RequestProgressListener {

    /** A listener that does nothing. The production wiring. */
    RequestProgressListener NO_OP = checkpoint -> {
    };

    /**
     * Reports that the request has reached a checkpoint.
     *
     * @param checkpoint the checkpoint that has just been reached
     */
    void at(RequestCheckpoint checkpoint);
}
