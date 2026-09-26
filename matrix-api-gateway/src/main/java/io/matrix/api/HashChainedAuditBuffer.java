package io.matrix.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * RECON-W0 — In-memory hash-chained audit buffer (gateway side).
 *
 * <p>Mirrors the semantics of {@code io.matrix.audit.HashChainedLog}
 * so the gateway's {@code /v1/audit/logs} endpoint serves tamper-evident
 * events. Each event stores the SHA-256 hash of the previous event's
 * hash concatenated with this event's payload; tampering with any event
 * invalidates every subsequent hash.</p>
 *
 * <p>Keep the buffer ≤ 200 events (matches the previous ring size) to
 * bound memory. Production deployments should persist via
 * {@code HashChainedLog} from matrix-audit.</p>
 */
public final class HashChainedAuditBuffer {

    private final int capacity;
    private final List<ChainEvent> events = new ArrayList<>();

    public HashChainedAuditBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity > 0");
        this.capacity = capacity;
    }

    public synchronized ChainEvent append(String eventType, String userId,
                                          String action, String timestamp) {
        String prevHash = events.isEmpty()
            ? "0".repeat(64)
            : events.get(events.size() - 1).hash();
        ChainEvent e = new ChainEvent(
            events.size() + 1,
            eventType,
            userId == null ? "" : userId,
            action == null ? "" : action,
            timestamp == null ? Instant.now().toString() : timestamp,
            prevHash,
            computeHash(prevHash, eventType, userId, action, timestamp));
        events.add(e);
        while (events.size() > capacity) events.remove(0);
        return e;
    }

    /** Returns -1 if chain is intact, otherwise the index of the first tampered event. */
    public synchronized int verify() {
        String prev = "0".repeat(64);
        for (int i = 0; i < events.size(); i++) {
            ChainEvent e = events.get(i);
            if (!e.prevHash().equals(prev)) return i;
            String recomputed = computeHash(e.prevHash(),
                e.eventType(), e.userId(), e.action(), e.timestamp());
            if (!recomputed.equals(e.hash())) return i;
            prev = e.hash();
        }
        return -1;
    }

    public synchronized List<ChainEvent> all() {
        return List.copyOf(events);
    }

    public synchronized int size() {
        return events.size();
    }

    private static String computeHash(String prev, String type, String user,
                                      String action, String ts) {
        String payload = prev + "|" + type + "|" + user + "|" + action + "|" + ts;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK; this is unreachable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record ChainEvent(long id, String eventType, String userId,
                             String action, String timestamp,
                             String prevHash, String hash) {}
}
