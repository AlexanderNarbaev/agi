package io.matrix.cognitive;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * W45.0 — the durable registry of idempotency keys.
 *
 * <h2>The policy</h2>
 * <p>An idempotency key names ONE logical request of a caller. The registry holds the mapping
 * from that key to the episode the request produced, and it holds it in the STORE, so the
 * mapping survives a restart: a caller that retries after the process died is still talking
 * about the same request, and is still answered with the original outcome.</p>
 *
 * <ul>
 *   <li><b>No key</b> — nothing is registered, and every request produces a fresh episode, even
 *       when the payload is byte-identical to an earlier one. The caller asked for a new
 *       request.</li>
 *   <li><b>A key, first delivery</b> — the key is CLAIMED, the cycle runs, and the outcome is
 *       written under the key.</li>
 *   <li><b>A key, repeated delivery with the SAME payload</b> — the original outcome is returned
 *       and NO work is done: no episode, no events, no prediction.</li>
 *   <li><b>A key, repeated delivery with a DIFFERENT payload</b> — an explicit conflict. The
 *       first outcome is kept, the second request is refused, and nothing is overwritten.</li>
 * </ul>
 *
 * <h2>Why the payload is compared by a digest</h2>
 * <p>The payload is compared by a SHA-256 digest, not by {@code String.hashCode()}: a hash
 * collision would let one request masquerade as another, and the whole point of the registry is
 * that the key binds to ONE payload. The digest is a function of the bytes, so it is stable
 * across processes and across restarts.</p>
 *
 * <h2>Why the claim is written before the work</h2>
 * <p>The key row is inserted BEFORE the cycle runs, in the state {@code IN_PROGRESS}. The insert
 * is the only atomic step, and it is what makes two concurrent deliveries of one key safe: the
 * second delivery finds the key claimed and does no work. If the cycle fails, the claim is
 * released and the key can be used again — a failed request is not a completed one.</p>
 */
public final class IdempotencyRegistry {

    /** What the registry decided about a delivery. */
    public enum Kind {
        /** The caller owns the key and must now do the work. */
        ACQUIRED,
        /** The key was delivered before with the same payload; the original outcome is here. */
        REPLAY,
        /** The key was delivered before with a DIFFERENT payload. */
        CONFLICT,
        /** The key is claimed by a delivery that is still running. */
        IN_PROGRESS
    }

    /**
     * The decision about one delivery.
     *
     * @param kind      what the registry decided
     * @param key       the idempotency key
     * @param episodeId the episode of the original delivery, when there is one
     * @param outcome   the original outcome, when the delivery is a replay
     */
    public record Claim(Kind kind, String key, String episodeId, CognitiveOutcome outcome) {

        /** A claim the caller owns. */
        public static Claim acquired(String key) {
            return new Claim(Kind.ACQUIRED, key, null, null);
        }

        /** A replay of an earlier delivery. */
        public static Claim replay(String key, String episodeId, CognitiveOutcome outcome) {
            return new Claim(Kind.REPLAY, key, episodeId, outcome);
        }

        /** A key that was used with another payload. */
        public static Claim conflict(String key) {
            return new Claim(Kind.CONFLICT, key, null, null);
        }

        /** A key whose first delivery is still running. */
        public static Claim inProgress(String key) {
            return new Claim(Kind.IN_PROGRESS, key, null, null);
        }
    }

    /** How many times a delivery waits for the owner of an in-progress key. */
    private static final int WAIT_ATTEMPTS = 40;

    /** How long a delivery waits between two attempts, in milliseconds. */
    private static final long WAIT_INTERVAL_MILLIS = 50L;

    private final CognitiveStore store;
    private final Supplier<String> clock;

    /**
     * A registry over a given store.
     *
     * @param store the store that holds the mapping
     * @param clock the source of the timestamps written with the mapping
     */
    public IdempotencyRegistry(CognitiveStore store, Supplier<String> clock) {
        this.store = store;
        this.clock = clock;
    }

    /**
     * The digest that binds a key to its payload.
     *
     * @param payload the payload of the request
     * @return the digest, as hexadecimal text
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
     * Decides what a delivery of the given key means.
     *
     * @param key         the idempotency key
     * @param fingerprint the digest of the payload
     * @return the decision
     */
    public Claim claim(String key, String fingerprint) {
        if (store.claimIdempotency(key, fingerprint, clock.get())) {
            return Claim.acquired(key);
        }
        CognitiveStore.IdempotencyRecord existing = store.findIdempotency(key).orElse(null);
        if (existing == null) {
            // The claim was released between the two calls: the owner failed. The key is free.
            if (store.claimIdempotency(key, fingerprint, clock.get())) {
                return Claim.acquired(key);
            }
            existing = store.findIdempotency(key).orElse(null);
            if (existing == null) {
                return Claim.inProgress(key);
            }
        }
        if (!existing.fingerprint().equals(fingerprint)) {
            return Claim.conflict(key);
        }
        if (existing.completed()) {
            return Claim.replay(key, existing.episodeId(), OutcomeSnapshot.decode(existing.outcome()));
        }
        return Claim.inProgress(key);
    }

    /**
     * Waits for the delivery that owns an in-progress key.
     *
     * @param key         the idempotency key
     * @param fingerprint the digest of the payload
     * @return the original outcome when the owner finished, otherwise empty
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
                clock.get());
    }

    /**
     * Releases a claim whose work failed, so that the key can be used again.
     *
     * @param key the idempotency key
     */
    public void release(String key) {
        store.releaseIdempotency(key);
    }
}
