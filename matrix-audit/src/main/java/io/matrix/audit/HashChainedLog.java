package io.matrix.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * WAVE T-06 — Hash-Chained Audit Log.
 *
 * Append-only log where each entry's hash is computed as:
 *
 *   hash_i = SHA-256(prev_hash_i || event_id_i || timestamp_i || payload_i)
 *
 * Any tampering with a prior entry breaks the chain — verification
 * detects it in O(n) by recomputing each hash and comparing.
 *
 * <p><b>Thread safety:</b> {@link ReentrantReadWriteLock} allows concurrent
 * reads (verifications, queries) but exclusive writes (appends). This
 * matches the expected access pattern: many queries, occasional appends.</p>
 *
 * <p><b>CONSTITUTION compliance:</b> Hash algorithm is SHA-256 (NIST approved,
 * FIPS 140-2 compliant). No LLM is invoked.</p>
 */
public final class HashChainedLog {

    private final List<AuditEvent> events;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public HashChainedLog() {
        this.events = new ArrayList<>();
    }

    /** Constructor for restoring from persistence (test helper). */
    public HashChainedLog(List<AuditEvent> initialEvents) {
        this.events = new ArrayList<>(initialEvents);
    }

    /**
     * Append a new event. Computes the hash automatically using the
     * last event's hash as prev_hash.
     *
     * @return the appended event (with hash set)
     */
    public AuditEvent append(AuditEvent.Builder builder) {
        lock.writeLock().lock();
        try {
            String prevHash = events.isEmpty()
                ? "0".repeat(64)
                : events.get(events.size() - 1).hash();

            AuditEvent raw = builder.prevHash(prevHash).build();
            String hash = computeHash(raw);
            AuditEvent event = new AuditEvent(
                raw.eventId(), raw.timestamp(), raw.prevHash(), hash,
                raw.userId(), raw.action(), raw.target(), raw.statusCode(),
                raw.explainId(), raw.metadata(), raw.tombstone()
            );
            events.add(event);
            return event;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Append an event that already has its hash set (used by pruner). */
    public AuditEvent appendVerified(AuditEvent event) {
        lock.writeLock().lock();
        try {
            String expectedPrev = events.isEmpty()
                ? "0".repeat(64)
                : events.get(events.size() - 1).hash();
            if (!expectedPrev.equals(event.prevHash())) {
                throw new IllegalArgumentException("prev_hash mismatch: expected " +
                    expectedPrev.substring(0, 8) + "..., got " +
                    event.prevHash().substring(0, 8) + "...");
            }
            String recomputed = computeHash(event);
            if (!recomputed.equals(event.hash())) {
                throw new IllegalArgumentException("hash mismatch: recomputed " +
                    recomputed.substring(0, 8) + "..., got " +
                    event.hash().substring(0, 8) + "...");
            }
            events.add(event);
            return event;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Verify the entire chain. Returns null if intact, or the index
     * of the first tampered entry.
     */
    public Integer verify() {
        lock.readLock().lock();
        try {
            String expectedPrev = "0".repeat(64);
            for (int i = 0; i < events.size(); i++) {
                AuditEvent e = events.get(i);
                if (!e.prevHash().equals(expectedPrev)) {
                    return i;
                }
                String recomputed = computeHash(e);
                if (!recomputed.equals(e.hash())) {
                    return i;
                }
                expectedPrev = e.hash();
            }
            return null;  // intact
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Total number of events. */
    public int size() {
        lock.readLock().lock();
        try {
            return events.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** All events, in append order. Returns immutable copy. */
    public List<AuditEvent> all() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(events));
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Most recent N events (newest first). */
    public List<AuditEvent> recent(int n) {
        lock.readLock().lock();
        try {
            int from = Math.max(0, events.size() - n);
            List<AuditEvent> slice = new ArrayList<>(events.subList(from, events.size()));
            Collections.reverse(slice);
            return Collections.unmodifiableList(slice);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Find an event by ID. */
    public Optional<AuditEvent> findById(String eventId) {
        lock.readLock().lock();
        try {
            for (AuditEvent e : events) {
                if (e.eventId().equals(eventId)) {
                    return Optional.of(e);
                }
            }
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Find events by user_id (sorted by timestamp, newest first). */
    public List<AuditEvent> findByUser(String userId) {
        lock.readLock().lock();
        try {
            List<AuditEvent> matches = new ArrayList<>();
            for (AuditEvent e : events) {
                if (e.userId().equals(userId)) {
                    matches.add(e);
                }
            }
            matches.sort((a, b) -> b.timestamp().compareTo(a.timestamp()));
            return Collections.unmodifiableList(matches);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Compute the SHA-256 hash for an event. Hash inputs:
     * prev_hash || event_id || timestamp || user_id || action || target ||
     * status_code || explain_id || metadata || tombstone
     */
    public static String computeHash(AuditEvent event) {
        String input = event.prevHash()
            + "|" + event.eventId()
            + "|" + event.timestamp().toEpochMilli()
            + "|" + event.userId()
            + "|" + event.action()
            + "|" + event.target()
            + "|" + event.statusCode()
            + "|" + (event.explainId() == null ? "" : event.explainId())
            + "|" + (event.metadata() == null ? "" : event.metadata())
            + "|" + event.tombstone();
        return sha256(input);
    }

    /** SHA-256 helper (hex output). */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
