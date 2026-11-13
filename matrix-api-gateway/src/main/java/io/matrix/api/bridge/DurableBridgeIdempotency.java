package io.matrix.api.bridge;

import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeIdempotency;
import io.matrix.contract.bridge.BridgeRefusalReason;

import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * W53 — the bridge's idempotency registry, backed by chain B's own durable inbox.
 *
 * <p><b>Why this exists.</b> The W52 receiver deduplicated in memory, and the W52 report
 * named the defect: a restart of chain B between two deliveries of the same request id
 * loses the record of the first delivery, and the second delivery is treated as new. For a
 * message that carries a proposition that is a duplicate memory entry; for a message that
 * asks a peer to <b>execute a cognitive path</b> it is a second execution of the same
 * task.</p>
 *
 * <p><b>What this adapter does.</b> It moves the registry into the same durable store that
 * holds the request's state and its result, so that one write answers three questions
 * after a restart: was this request seen, what state did it reach, and what was its
 * result. The decision is made from the row, never from a map.</p>
 *
 * <p><b>The conflict rule.</b> A request id that arrives with a different payload digest is
 * refused — the caller is asking for two different things under one identity, and
 * executing either of them would make the caller's trace a lie. The refusal names the
 * conflict and nothing else; the digest itself is not echoed, because it is a digest of
 * the caller's payload and the refusal travels to whoever can read the channel.</p>
 */
public final class DurableBridgeIdempotency implements BridgeIdempotency {

    private final RemoteTaskStore store;

    /**
     * The registry over chain B's durable inbox.
     *
     * @param store the inbox
     */
    public DurableBridgeIdempotency(RemoteTaskStore store) {
        // A null inbox is allowed ONLY through {@link #refusing()}: the registry then
        // refuses every delivery instead of pretending it can remember one.
        this.store = store;
    }

    /**
     * The registry of a bridge that has no inbox.
     *
     * <p>It refuses every lookup: a bridge without a durable inbox cannot serve a remote
     * task, and the W52 defect — a process-local registry that forgets a delivery when
     * the process restarts — must not survive as a configuration somebody can switch
     * on. Failing closed is the only safe answer.</p>
     */
    public static DurableBridgeIdempotency refusing() {
        return new DurableBridgeIdempotency(null);
    }

    @Override
    public Optional<Outcome> find(String requestId) {
        if (requestId == null || requestId.isBlank() || store == null) {
            return Optional.empty();
        }
        try {
            return store.find(requestId).map(row -> Outcome.of(
                    decisionOf(row), row.payloadDigest()));
        } catch (SQLException e) {
            throw new IllegalStateException("the task inbox could not be read", e);
        }
    }

    @Override
    public Outcome firstOutcome(String requestId, String payloadDigest,
                                 Supplier<Outcome> firstDelivery) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("an idempotency key must not be blank");
        }
        if (payloadDigest == null || payloadDigest.isBlank()) {
            throw new IllegalArgumentException(
                    "an idempotency key needs the digest of the payload");
        }
        if (firstDelivery == null) {
            throw new IllegalArgumentException("there is no delivery to record");
        }
        if (store == null) {
            throw new IllegalStateException(
                    "this bridge has no durable task inbox, so it cannot accept a task");
        }
        try {
            Optional<RemoteTaskStore.Row> existing = store.find(requestId);
            if (existing.isPresent()) {
                RemoteTaskStore.Row row = existing.get();
                if (!row.payloadDigest().equals(payloadDigest)) {
                    return Outcome.of(BridgeDecision.refused(requestId, requestId,
                            BridgeRefusalReason.REQUEST_ID_CONFLICT,
                            "this request id was already used with a different payload, so "
                                    + "the request is a conflict and nothing was executed"),
                            payloadDigest);
                }
                return Outcome.of(decisionOf(row), payloadDigest);
            }
            return firstDelivery.get();
        } catch (SQLException e) {
            throw new IllegalStateException("the task inbox could not be read", e);
        }
    }

    @Override
    public int recordedCount() {
        if (store == null) {
            return 0;
        }
        try {
            return store.size();
        } catch (SQLException e) {
            throw new IllegalStateException("the task inbox could not be read", e);
        }
    }

    /**
     * The decision a stored row stands for.
     *
     * @param row the row
     * @return the decision
     */
    private static BridgeDecision decisionOf(RemoteTaskStore.Row row) {
        if (row.state() == RemoteTaskStore.State.COMPLETED) {
            return BridgeDecision.accepted(row.requestId(), "audit-" + row.requestId());
        }
        if (row.state().terminal()) {
            return BridgeDecision.refused(row.requestId(), "audit-" + row.requestId(),
                    BridgeRefusalReason.MALFORMED_MESSAGE,
                    row.refusalReason() == null
                            ? "the request ended without a result" : row.refusalReason());
        }
        return BridgeDecision.accepted(row.requestId(), "audit-" + row.requestId());
    }
}
