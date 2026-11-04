package io.matrix.cognitive;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * W45.1 — the durable registry of idempotency keys, and the recovery of the requests that a
 * terminated process left behind.
 *
 * <h2>The policy</h2>
 * <p>An idempotency key names ONE logical request of a caller. The registry holds the mapping
 * from that key to the execution it produced, and it holds it in the STORE, so the mapping
 * survives a restart: a caller that retries after the process died is still talking about the
 * same request.</p>
 *
 * <ul>
 *   <li><b>No key</b> — nothing is registered, and every request produces a fresh episode, even
 *       when the payload is byte-identical to an earlier one. The caller asked for a new
 *       request.</li>
 *   <li><b>A key, first delivery</b> — the key is CLAIMED with its identities, the cycle runs,
 *       and the outcome is written under the key.</li>
 *   <li><b>A key, repeated delivery with the SAME payload</b> — the original outcome is returned
 *       and NO work is done: no episode, no events, no prediction.</li>
 *   <li><b>A key, repeated delivery with a DIFFERENT payload</b> — an explicit conflict. The
 *       first outcome is kept, the second request is refused, and nothing is overwritten.</li>
 * </ul>
 *
 * <h2>W45.1 — what happens when the process dies</h2>
 * <p>The W45.0 registry had two states, and both failure paths were unclosed:</p>
 * <ul>
 *   <li>a process that died with the key claimed left the key claimed FOREVER: the retry found
 *       {@code IN_PROGRESS}, waited two seconds and reported that the request was still
 *       running. There was no recovery path at all;</li>
 *   <li>a request that failed AFTER a committed observation DELETED its mapping, so the next
 *       delivery started a second episode and committed the same observation twice.</li>
 * </ul>
 *
 * <p>Both are closed here. The claim carries the identities of the execution, the owner that
 * made it and the last durable checkpoint, and the registry decides — from the operating system,
 * not from a guess — whether the owner is still alive:</p>
 *
 * <table>
 *   <caption>the recovery decision</caption>
 *   <tr><th>the state found</th><th>the owner</th><th>the decision</th></tr>
 *   <tr><td>{@code COMPLETED}</td><td>—</td><td>REPLAY the stored outcome</td></tr>
 *   <tr><td>{@code IN_PROGRESS}</td><td>alive</td><td>IN_PROGRESS — the work is not stolen</td></tr>
 *   <tr><td>{@code IN_PROGRESS}</td><td>dead, no durable effect</td><td>RECOVER — execute again,
 *       in the SAME episode</td></tr>
 *   <tr><td>{@code IN_PROGRESS}</td><td>dead, durable effect</td><td>INDETERMINATE — the caller is
 *       told the truth</td></tr>
 * </table>
 *
 * <p>A request that is executed again is never given a second episode: the episode id and the
 * signal id were allocated at claim time and are kept by the store.</p>
 */
public final class IdempotencyRegistry {

    /** What the registry decided about a delivery. */
    public enum Kind {
        /** The caller owns the request and must now do the work. */
        ACQUIRED,
        /** The key was delivered before with the same payload; the original outcome is here. */
        REPLAY,
        /** The key was delivered before with a DIFFERENT payload. */
        CONFLICT,
        /** The key is claimed by a delivery that is still running. */
        IN_PROGRESS,
        /**
         * The request was interrupted before any effect and may be executed again.
         *
         * <p>The caller owns it and must do the work — in the SAME episode.</p>
         */
        RECOVERED,
        /**
         * The request was interrupted after a durable effect and may NOT be executed again.
         *
         * <p>The outcome is unknown. The caller must not retry blindly.</p>
         */
        INDETERMINATE
    }

    /**
     * The decision about one delivery.
     *
     * @param kind      what the registry decided
     * @param key       the idempotency key
     * @param episodeId the episode of the original delivery, when there is one
     * @param outcome   the original outcome, when the delivery is a replay
     * @param signalId  the signal the execution received, when there is one
     * @param sequence  the arrival number of the signal, when there is one
     * @param state     the lifecycle state the request is in
     * @param checkpoint the last durable checkpoint of the execution
     */
    public record Claim(Kind kind, String key, String episodeId, CognitiveOutcome outcome,
                        String signalId, long sequence, RequestExecutionState state,
                        RequestCheckpoint checkpoint) {

        /** A claim the caller owns. */
        public static Claim acquired(String key, String signalId, long sequence) {
            return new Claim(Kind.ACQUIRED, key, null, null, signalId, sequence,
                    RequestExecutionState.IN_PROGRESS, RequestCheckpoint.CLAIMED);
        }

        /** A claim the caller owns after a recovery of an interrupted request. */
        public static Claim recovered(String key, String signalId, long sequence,
                                      RequestCheckpoint checkpoint) {
            return new Claim(Kind.RECOVERED, key, null, null, signalId, sequence,
                    RequestExecutionState.IN_PROGRESS, checkpoint);
        }

        /** A replay of an earlier delivery. */
        public static Claim replay(String key, String episodeId, CognitiveOutcome outcome) {
            return new Claim(Kind.REPLAY, key, episodeId, outcome, null, 0,
                    RequestExecutionState.COMPLETED, RequestCheckpoint.IDEMPOTENCY_COMPLETED);
        }

        /** A key that was used with another payload. */
        public static Claim conflict(String key) {
            return new Claim(Kind.CONFLICT, key, null, null, null, 0, null, null);
        }

        /** A key whose first delivery is still running. */
        public static Claim inProgress(String key) {
            return new Claim(Kind.IN_PROGRESS, key, null, null, null, 0,
                    RequestExecutionState.IN_PROGRESS, null);
        }

        /** A key whose delivery was interrupted after a durable effect. */
        public static Claim indeterminate(String key, RequestCheckpoint checkpoint) {
            return new Claim(Kind.INDETERMINATE, key, null, null, null, 0,
                    RequestExecutionState.INTERRUPTED_PARTIAL, checkpoint);
        }

        /** Whether the caller owns the work. */
        public boolean owned() {
            return kind == Kind.ACQUIRED || kind == Kind.RECOVERED;
        }
    }

    /** How many times a delivery waits for the owner of an in-progress key. */
    private static final int WAIT_ATTEMPTS = 40;

    /** How long a delivery waits between two attempts, in milliseconds. */
    private static final long WAIT_INTERVAL_MILLIS = 50;

    private final CognitiveStore store;
    private final Supplier<String> clock;
    private final EpisodeIdAllocator allocator;
    private final OwnershipToken owner;

    /**
     * A registry over a store.
     *
     * @param store the store that holds the durable mapping
     * @param clock the source of the timestamps written with the durable records
     */
    public IdempotencyRegistry(CognitiveStore store, Supplier<String> clock) {
        this(store, clock, null, OwnershipToken.current());
    }

    /**
     * A registry over a store, with an allocator for the identities of the executions.
     *
     * @param store     the store that holds the durable mapping
     * @param clock     the source of the timestamps written with the durable records
     * @param allocator the allocator of the signal ids and the arrival numbers
     * @param owner     the token of this process
     */
    public IdempotencyRegistry(CognitiveStore store, Supplier<String> clock,
                               EpisodeIdAllocator allocator, OwnershipToken owner) {
        this.store = store;
        this.clock = clock;
        this.allocator = allocator;
        this.owner = owner;
    }

    /**
     * The digest that binds a payload to its key.
     *
     * @param payload the payload
     * @return the SHA-256 digest of the payload, in hexadecimal
     */
    public static String fingerprintOf(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((payload == null ? "" : payload)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("failed to fingerprint the request payload", e);
        }
    }

    /**
     * The stable identity of a logical request.
     *
     * <p>It is derived from the key, so it is the SAME after a restart: a retry continues the
     * same logical request rather than starting a new one.</p>
     *
     * @param key the idempotency key
     * @return the request id
     */
    public static String requestIdOf(String key) {
        return "req-" + fingerprintOf(key).substring(0, 16);
    }

    /**
     * Claims a key for one execution, or reports what is already there.
     *
     * @param key         the idempotency key
     * @param fingerprint the digest of the payload
     * @return the decision
     */
    public Claim claim(String key, String fingerprint) {
        String now = clock.get();
        CognitiveStore.IdempotencyRecord existing = store.findIdempotency(key).orElse(null);
        if (existing == null) {
            String signalId = allocator == null ? "sig-" + key : allocator.nextSignalId();
            long sequence = allocator == null ? 0 : allocator.nextSignalSequence();
            String episodeId = EpisodeIdAllocator.episodeIdFor(signalId);
            CognitiveStore.IdempotencyRecord claimed = store.claimIdempotency(key, fingerprint,
                    requestIdOf(key), signalId, sequence, episodeId, owner, now);
            store.recordRequestEvent(key, requestIdOf(key), episodeId,
                    RequestExecutionState.IN_PROGRESS.name(), RequestCheckpoint.CLAIMED.name(),
                    "the request was claimed", now);
            if (claimed.executionState().orElse(RequestExecutionState.IN_PROGRESS)
                    == RequestExecutionState.IN_PROGRESS
                    && claimed.attempts() == 1) {
                return Claim.acquired(key, signalId, sequence);
            }
            return interpret(claimed, fingerprint);
        }
        return interpret(existing, fingerprint);
    }

    private Claim interpret(CognitiveStore.IdempotencyRecord existing, String fingerprint) {
        String key = existing.key();
        if (!existing.fingerprint().equals(fingerprint)) {
            return Claim.conflict(key);
        }
        RequestExecutionState state = existing.executionState().orElse(null);
        if (state == null) {
            // A row written by a version that did not know the lifecycle. It is neither
            // completable nor re-executable: it is indeterminate rather than a guess.
            return Claim.indeterminate(key, existing.lastCheckpoint().orElse(null));
        }
        switch (state) {
            case COMPLETED -> {
                return Claim.replay(key, existing.episodeId(),
                        OutcomeSnapshot.decode(existing.outcome()));
            }
            case IN_PROGRESS, CLAIMED -> {
                Optional<OwnershipToken> claimedBy = existing.owner();
                if (claimedBy.isPresent()) {
                    OwnershipToken.Ownership ownership =
                            OwnershipToken.classify(claimedBy.get(), owner);
                    if (ownership == OwnershipToken.Ownership.MINE
                            || ownership == OwnershipToken.Ownership.LIVE_OTHER) {
                        // The work is not stolen: it is ours, or it belongs to a process that
                        // is still running.
                        return Claim.inProgress(key);
                    }
                }
                return recover(existing);
            }
            case FAILED_NO_EFFECT, INTERRUPTED_NO_EFFECT -> {
                return takeOver(existing);
            }
            default -> {
                return Claim.indeterminate(key, existing.lastCheckpoint().orElse(null));
            }
        }
    }

    /**
     * The recovery of a request whose owner is gone.
     *
     * @param existing the row of the request
     * @return the decision
     */
    private Claim recover(CognitiveStore.IdempotencyRecord existing) {
        String key = existing.key();
        RequestCheckpoint checkpoint = existing.lastCheckpoint().orElse(null);
        boolean effect = checkpoint != null && checkpoint.durableEffect();
        RequestExecutionState recovered = effect
                ? RequestExecutionState.INTERRUPTED_PARTIAL
                : RequestExecutionState.INTERRUPTED_NO_EFFECT;
        String detail = effect
                ? "the owner of the request is gone and the execution had reached " + checkpoint
                        + "; the outcome cannot be established and the request may not be repeated"
                : "the owner of the request is gone and the execution had written nothing"
                        + (checkpoint == null ? "" : " beyond " + checkpoint)
                        + "; the request may be executed again";
        store.recordRecovery(key, recovered, checkpoint, detail, clock.get());
        if (effect) {
            return Claim.indeterminate(key, checkpoint);
        }
        return takeOver(existing);
    }

    /**
     * Takes a re-executable request over, keeping its identities.
     *
     * @param existing the row of the request
     * @return the decision
     */
    private Claim takeOver(CognitiveStore.IdempotencyRecord existing) {
        String key = existing.key();
        String signalId = existing.signalId() != null ? existing.signalId() : "sig-" + key;
        long sequence = existing.signalSequence();
        if (allocator != null && (existing.signalId() == null || existing.signalId().isBlank())) {
            signalId = allocator.nextSignalId();
            sequence = allocator.nextSignalSequence();
        }
        CognitiveStore.IdempotencyRecord claimed = store.claimIdempotency(key, existing.fingerprint(),
                existing.requestId() != null ? existing.requestId() : requestIdOf(key),
                signalId, sequence, existing.episodeId() != null ? existing.episodeId()
                        : EpisodeIdAllocator.episodeIdFor(signalId),
                owner, clock.get());
        store.recordRequestEvent(key, claimed.requestId(), claimed.episodeId(),
                RequestExecutionState.IN_PROGRESS.name(),
                RequestCheckpoint.CLAIMED.name(),
                "the interrupted request was taken over by a new execution of the same logical request",
                clock.get());
        return Claim.recovered(key, signalId, sequence,
                existing.lastCheckpoint().orElse(RequestCheckpoint.CLAIMED));
    }

    /**
     * Waits for the owner of an in-progress key to finish.
     *
     * @param key        the idempotency key
     * @param fingerprint the digest of the payload
     * @return the outcome, when the owner finished within the wait
     */
    public Optional<CognitiveOutcome> await(String key, String fingerprint) {
        for (int attempt = 0; attempt < WAIT_ATTEMPTS; attempt++) {
            CognitiveStore.IdempotencyRecord existing = store.findIdempotency(key).orElse(null);
            if (existing == null) {
                return Optional.empty();
            }
            if (!existing.fingerprint().equals(fingerprint)) {
                return Optional.empty();
            }
            if (existing.completed()) {
                return Optional.of(OutcomeSnapshot.decode(existing.outcome()));
            }
            try {
                Thread.sleep(WAIT_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Writes the outcome of a completed delivery under its key.
     *
     * @param key       the idempotency key
     * @param episodeId the episode the delivery produced
     * @param signalId  the signal the delivery received
     * @param outcome   the outcome of the delivery
     */
    public void complete(String key, String episodeId, String signalId, CognitiveOutcome outcome) {
        store.completeIdempotency(key, episodeId, signalId, OutcomeSnapshot.encode(outcome),
                clock.get(), owner);
    }

    /**
     * Records the failure of an execution.
     *
     * <p>W45.1: the mapping is never DELETED. A failure is recorded with the classification
     * that says whether the work may be repeated, and the row keeps the identities and the
     * checkpoint so that a recovery can reason about it.</p>
     *
     * @param key        the idempotency key
     * @param state      the lifecycle state the request moved to
     * @param checkpoint the last durable checkpoint of the execution
     * @param failure    what happened, in words
     */
    public void fail(String key, RequestExecutionState state, RequestCheckpoint checkpoint,
                     String failure) {
        store.failIdempotency(key, state, checkpoint, failure, clock.get(), owner);
    }

    /**
     * Records the durable checkpoint an execution reached.
     *
     * @param key        the idempotency key
     * @param checkpoint the checkpoint that was reached
     */
    public void checkpoint(String key, RequestCheckpoint checkpoint) {
        store.recordCheckpoint(key, checkpoint, clock.get());
    }

    /**
     * Records the recovery decision of a request whose owner is provably gone.
     *
     * <p>This write is NOT fenced by the owner, and that is deliberate: the fence exists to
     * stop a live owner from writing over a new owner, and here the owner is dead — the
     * recovery writes on its behalf. The caller must have established that the owner is gone
     * before it calls this.</p>
     *
     * @param key        the idempotency key
     * @param state      the state the request moved to
     * @param checkpoint the last durable checkpoint, when there is one
     * @param recovery   what the recovery decided, in words
     */
    public void recordRecovery(String key, RequestExecutionState state,
                               RequestCheckpoint checkpoint, String recovery) {
        store.recordRecovery(key, state, checkpoint, recovery, clock.get());
    }

    /**
     * The requests that are not in a terminal state.
     *
     * @return the unfinished requests
     */
    public java.util.List<CognitiveStore.IdempotencyRecord> unfinished() {
        return store.unfinishedRequests();
    }

    /**
     * The lifecycle history of one request.
     *
     * @param key the idempotency key
     * @return the events of the request
     */
    public java.util.List<String> history(String key) {
        return store.requestEvents(key);
    }
}
