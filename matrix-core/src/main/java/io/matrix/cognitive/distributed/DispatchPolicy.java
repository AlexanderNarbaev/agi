package io.matrix.cognitive.distributed;

/**
 * W53 — the bounds the coordinator keeps on the work it hands to another process.
 *
 * <p><b>Why a policy and not four numbers in the coordinator.</b> A bounded task is
 * bounded <i>before</i> it is sent, and the bounds are a decision of the deployment, not
 * of the call site. Naming them in one place makes them visible, testable and
 * comparable: a reader can see what the coordinator will refuse and why.</p>
 *
 * <p><b>What each bound is for.</b></p>
 * <ul>
 *   <li>{@code maxPayloadBytes} — the largest bounded input the coordinator will put on
 *       the wire. A task whose input does not fit is refused <b>before</b> any durable
 *       record is written, so an oversized task leaves no trace of a dispatch that never
 *       happened.</li>
 *   <li>{@code maxConcurrentDispatches} — the largest number of dispatches in flight at
 *       once. The coordinator refuses the work that exceeds it instead of queueing it:
 *       an unbounded queue is an unbounded memory and an unbounded latency.</li>
 *   <li>{@code maxRetries} — the largest number of times one request may be replayed.
 *       A retry budget is what keeps "at-least-once delivery" from turning into an
 *       unbounded loop against a peer that keeps failing.</li>
 *   <li>{@code defaultBudgetMillis} and {@code defaultDeadlineMillis} — what a task gets
 *       when the caller names neither.</li>
 * </ul>
 *
 * <p><b>Why the deadline is not a promise about the peer.</b> The deadline bounds the
 * work <i>chain A waits for</i>. The peer may still finish its work after chain A has
 * stopped waiting, and its result may still be committed on its side — a timeout does
 * not undo the peer's effect, and it does not delete the answer that arrived late.</p>
 *
 * @param maxPayloadBytes        the largest bounded input, in bytes
 * @param maxConcurrentDispatches the largest number of dispatches in flight at once
 * @param maxRetries              the largest number of replays of one request
 * @param defaultBudgetMillis     the execution budget a task gets when none is named
 * @param defaultDeadlineMillis   the deadline a task gets when none is named
 */
public record DispatchPolicy(
        int maxPayloadBytes,
        int maxConcurrentDispatches,
        int maxRetries,
        long defaultBudgetMillis,
        long defaultDeadlineMillis) {

    /** The bounds the coordinator uses when the deployment names none. */
    public static DispatchPolicy standard() {
        return new DispatchPolicy(
                CognitiveCoordinator.DEFAULT_MAX_PAYLOAD_BYTES,
                CognitiveCoordinator.DEFAULT_MAX_CONCURRENT_DISPATCHES,
                CognitiveCoordinator.DEFAULT_MAX_RETRIES,
                CognitiveCoordinator.DEFAULT_BUDGET_MILLIS,
                CognitiveCoordinator.DEFAULT_DEADLINE_MILLIS);
    }

    public DispatchPolicy {
        if (maxPayloadBytes <= 0) {
            throw new IllegalArgumentException("the payload limit must be positive");
        }
        if (maxConcurrentDispatches <= 0) {
            throw new IllegalArgumentException("the concurrency limit must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("the retry budget must not be negative");
        }
        if (defaultBudgetMillis <= 0L) {
            throw new IllegalArgumentException("the default budget must be positive");
        }
        if (defaultDeadlineMillis <= 0L) {
            throw new IllegalArgumentException("the default deadline must be positive");
        }
    }
}
