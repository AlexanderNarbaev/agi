package io.matrix.cognitive;

import java.util.List;

/**
 * W53 — chain A's durable intent to send a bounded cognitive task to a peer.
 *
 * <p><b>Why this record exists at all.</b> The research track on durable execution found
 * the rule the whole design rests on: SQLite's own documentation says a transaction over
 * several attached databases "are atomic for each individual database, but are not atomic
 * across all databases as a set", and chain A and chain B own two such databases. So no
 * configuration rescues a distributed two-phase commit, and the design must be
 * <b>crash-consistent, never atomic</b>. The way to be crash-consistent across a
 * boundary you do not own is to write down your intent <i>before</i> you act, so that
 * after a crash you can look at what you meant to do and finish it.</p>
 *
 * <p><b>What the intent is for.</b> It is chain A's outbox row. It records the task, the
 * request, the capability, the payload hash and the state, so that:</p>
 * <ul>
 *   <li>a request that was prepared but not yet sent is still visible after a restart,
 *       and can be sent again — the delivery is at-least-once;</li>
 *   <li>a request that was sent but whose answer was lost is still visible, and the same
 *       request id can be sent again — the peer deduplicates it, so the logical effect
 *       happens once;</li>
 *   <li>a request that was answered and adjudicated is still visible, and the whole
 *       history of the task can be reconstructed from durable state alone.</li>
 * </ul>
 *
 * <p><b>What it does not claim.</b> It does not claim that the task happened exactly
 * once. It cannot: the two databases are separate, and the honest guarantee is at-least-once
 * delivery with a durable deduplication at the receiver and effectively-once logical
 * effects. The claim that is not made is written down here so that no later reader has to
 * guess.</p>
 *
 * @param requestId        the stable request id — the idempotency key of the exchange
 * @param taskId           the task this request belongs to
 * @param workspaceId      the workspace the task belongs to
 * @param capabilityId     the capability the peer was asked to run
 * @param payloadHash      the hash of the request payload, for the conflict rule
 * @param state            where the dispatch is
 * @param remoteResponder  who answered, when somebody did
 * @param remoteStatus     the peer's own status, when it answered
 * @param disposition      chain A's verdict on the answer, when it was made
 * @param dispositionReason why chain A decided that
 * @param responseJson     the peer's structured response, when it arrived
 * @param reportJson       the peer's secondary report, when it arrived
 * @param createdAt        when the intent was recorded
 * @param updatedAt        when the intent last changed
 */
public record DispatchIntent(
        String requestId,
        String taskId,
        String parentEpisodeId,
        String workspaceId,
        String capabilityId,
        String payloadHash,
        State state,
        String remoteResponder,
        String remoteStatus,
        String disposition,
        String dispositionReason,
        String responseJson,
        String reportJson,
        String reportDisposition,
        String createdAt,
        String updatedAt) {

    /**
     * Where a dispatch is.
     *
     * <p><b>Why the states are what they are.</b> Each one names a moment that really
     * happened and that is durably recorded, not a moment that might happen. A dispatch
     * that is {@code DISPATCH_PENDING} is one that chain A has committed to send; a
     * dispatch that is {@code COMPLETED} is one whose result is already on disk; and a
     * dispatch that is {@code ADJUDICATED} is one whose answer chain A has evaluated
     * against its own evidence. Nothing is ever marked complete before the thing it
     * claims to be complete has been written.</p>
     */
    public enum State {
        /** The intent exists; nothing has been sent. */
        CREATED,
        /** The request is committed and ready to be sent — the outbox row. */
        DISPATCH_PENDING,
        /** The peer accepted the message; the execution is under way. */
        DISPATCHED,
        /** The peer finished the execution and the result is durably recorded. */
        COMPLETED,
        /** The peer refused the task. */
        REFUSED,
        /** The peer does not serve the capability the task named. */
        UNSUPPORTED,
        /** The peer's execution failed. */
        FAILED,
        /** The peer's execution did not finish in time. */
        TIMED_OUT,
        /** The task was cancelled before its cognitive execution finished. */
        CANCELLED,
        /** The answer was evaluated and chain A recorded its verdict. */
        ADJUDICATED;

        /**
         * True when nothing more will happen to this dispatch.
         *
         * @return true when the state is final
         */
        public boolean terminal() {
            return this == REFUSED || this == UNSUPPORTED || this == FAILED
                    || this == TIMED_OUT || this == CANCELLED || this == ADJUDICATED;
        }

        /**
         * True when the dispatch still owes the peer an answer.
         *
         * <p>A dispatch in one of these states is one that a reconciliation pass must look
         * at after a restart: the intent says it was sent, and the durable record of the
         * answer is not here yet.</p>
         *
         * @return true when the state is unfinished
         */
        public boolean unfinished() {
            return this == CREATED || this == DISPATCH_PENDING || this == DISPATCHED;
        }

        /**
         * The state the peer named, mapped onto this vocabulary.
         *
         * <p>The peer's vocabulary is its own: {@code COMPLETED}, {@code PARTIAL},
         * {@code REFUSED}, {@code UNSUPPORTED}, {@code FAILED}, {@code TIMED_OUT},
         * {@code CANCELLED} and {@code IN_PROGRESS}. The mapping is explicit so that no
         * unknown status silently becomes a success.</p>
         *
         * @param remote the peer's status name
         * @return the state, or null when the name is not one this vocabulary knows
         */
        public static State fromRemote(String remote) {
            if (remote == null || remote.isBlank()) {
                return null;
            }
            return switch (remote) {
                case "COMPLETED", "PARTIAL" -> State.COMPLETED;
                case "REFUSED" -> State.REFUSED;
                case "UNSUPPORTED" -> State.UNSUPPORTED;
                case "FAILED" -> State.FAILED;
                case "TIMED_OUT" -> State.TIMED_OUT;
                case "CANCELLED" -> State.CANCELLED;
                case "IN_PROGRESS" -> State.DISPATCHED;
                default -> null;
            };
        }
    }

    /**
     * The names of the states that a reconciliation pass must look at.
     *
     * @return the unfinished states
     */
    public static List<State> unfinishedStates() {
        return List.of(State.CREATED, State.DISPATCH_PENDING, State.DISPATCHED);
    }
}
