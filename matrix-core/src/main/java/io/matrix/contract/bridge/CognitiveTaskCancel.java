package io.matrix.contract.bridge;

import io.matrix.contract.Correlation;
import io.matrix.contract.Provenance;

/**
 * W53 — the typed request to withdraw a dispatched cognitive task.
 *
 * <p><b>What this is.</b> A caller that has dispatched a task may withdraw it. The
 * withdrawal names the request it withdraws, and nothing else: it carries no input, no
 * expectation and no permission of its own. The right to send it is granted by the
 * receiver, and the reason travels in the record so that the audit says why the work was
 * abandoned.</p>
 *
 * <p><b>What the peer does with it.</b> The peer keeps its own state machine. A task that
 * has not started is not started; a task that is running is recorded as cancelled, and
 * whether its effect survives depends on whether the effect was already committed — a
 * cancellation cannot undo what has already happened. The caller learns what the peer did
 * from the peer's own record, not from the cancellation having been sent.</p>
 *
 * @param protocolVersion the protocol version the sender speaks
 * @param taskId          the task the cancellation withdraws
 * @param requestId       the request the cancellation withdraws
 * @param correlation     the correlation of the withdrawn task
 * @param reason          why the task is withdrawn
 * @param requestedAt     when the cancellation was requested
 * @param provenance      the provenance of the delivery
 */
public record CognitiveTaskCancel(
        int protocolVersion,
        String taskId,
        String requestId,
        Correlation correlation,
        String reason,
        String requestedAt,
        Provenance provenance) {

    public CognitiveTaskCancel {
        if (protocolVersion < 1) {
            throw new IllegalArgumentException("the cancellation must name a protocol version");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("the cancellation needs a task id");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("the cancellation needs a request id");
        }
        if (correlation == null) {
            throw new IllegalArgumentException("the cancellation needs a correlation");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("the cancellation needs a reason");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("the cancellation needs a provenance");
        }
    }
}
