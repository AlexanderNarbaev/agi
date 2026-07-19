package io.matrix.privacy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GDPR tombstoning service — applies {@link Tombstone} markers to system
 * resources (FnlPackage, Neuron, Snapshot, ...) and persists them in a
 * durable audit log.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #tombstone(String, String, String, String, String, String)} creates
 *       a {@link Tombstone}, registers it, and returns it.</li>
 *   <li>Subsequent calls to {@link #isTombstoned(String, String)} answer {@code true}.</li>
 *   <li>{@link #all()} returns the full audit list (for compliance reporting).</li>
 * </ol>
 *
 * <p>The registry is thread-safe ({@link ConcurrentHashMap}) so any agent or
 * API request can erase concurrently. Tombstones themselves are immutable
 * records — once recorded, an entry cannot be un-tombstoned; the only
 * legal remedy is to create a new entry and a new audit record pointing
 * to the replacement.
 *
 * <p>Ref: L6 §6.7.
 */
public final class TombstoneService {

    /** Composite key: {@code resourceType + ":" + resourceId}. */
    private final ConcurrentHashMap<String, Tombstone> byResource = new ConcurrentHashMap<>();
    /** Audit log ordered by insertion (append-only). */
    private final List<Tombstone> auditLog = java.util.Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong sequence = new AtomicLong();

    public TombstoneService() {}

    /**
     * Records a tombstone. Returns the new {@link Tombstone} (never null).
     * If the same resource is already tombstoned, the existing tombstone is
     * preserved and returned (idempotent).
     *
     * @param subjectId     who/what is being erased (data subject, package id, ...)
     * @param resourceType  e.g. {@code "FnlPackage"}
     * @param resourceId    unique resource id within that type
     * @param reason        one of the {@code REASON_*} constants or custom
     * @param signature     content hash of the original entry (proof of erasure)
     * @param requesterId   operator/agent id initiating the erasure
     */
    public Tombstone tombstone(String subjectId, String resourceType,
                                String resourceId, String reason,
                                String signature, String requesterId) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(requesterId, "requesterId");

        String key = keyOf(resourceType, resourceId);
        return byResource.computeIfAbsent(key, k -> {
            Tombstone t = new Tombstone(
                    UUID.randomUUID(),
                    subjectId,
                    resourceType,
                    resourceId,
                    reason,
                    signature == null ? "" : signature,
                    Instant.now(),
                    requesterId);
            auditLog.add(t);
            sequence.incrementAndGet();
            return t;
        });
    }

    /** Convenience overload using the {@code GDPR Article 17} default reason. */
    public Tombstone tombstoneGdpr(String subjectId, String resourceType,
                                    String resourceId, String requesterId) {
        return tombstone(subjectId, resourceType, resourceId,
                Tombstone.REASON_GDPR_ERASURE, "", requesterId);
    }

    /** True iff this resource already carries a tombstone. */
    public boolean isTombstoned(String resourceType, String resourceId) {
        return byResource.containsKey(keyOf(resourceType, resourceId));
    }

    /** Look up an existing tombstone, or null. */
    public Tombstone find(String resourceType, String resourceId) {
        return byResource.get(keyOf(resourceType, resourceId));
    }

    /** Total count of tombstones applied since startup. */
    public int count() {
        return byResource.size();
    }

    /** Snapshot of all tombstones (caller must not mutate). */
    public List<Tombstone> all() {
        return java.util.List.copyOf(auditLog);
    }

    /** Filter tombstones by reason (prefix match, e.g. {@code "gdpr."}). */
    public List<Tombstone> filterByReason(String reasonPrefix) {
        if (reasonPrefix == null || reasonPrefix.isEmpty()) return all();
        return auditLog.stream()
                .filter(t -> t.reason() != null && t.reason().startsWith(reasonPrefix))
                .toList();
    }

    /** Filter tombstones by data subject id. */
    public List<Tombstone> filterBySubject(String subjectId) {
        return auditLog.stream()
                .filter(t -> Objects.equals(t.subjectId(), subjectId))
                .toList();
    }

    /** Bulk-tombstone a list of resources atomically (single Instant stamp). */
    public List<Tombstone> tombstoneAll(String subjectId, String resourceType,
                                         Collection<String> resourceIds,
                                         String reason, String requesterId) {
        List<Tombstone> created = new ArrayList<>(resourceIds.size());
        for (String id : resourceIds) {
            created.add(tombstone(subjectId, resourceType, id, reason, "", requesterId));
        }
        return created;
    }

    /** Human-readable summary suitable for the L12 audit log. */
    public String summary() {
        Map<String, Long> byReason = auditLog.stream()
                .filter(t -> t.reason() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        t -> t.reason().contains(".") ? t.reason().substring(0, t.reason().indexOf('.')) : "other",
                        java.util.stream.Collectors.counting()));
        StringBuilder sb = new StringBuilder();
        sb.append("TombstoneService: ").append(count()).append(" total tombstones;");
        byReason.forEach((k, v) -> sb.append(' ').append(k).append('=').append(v).append(';'));
        return sb.toString().trim();
    }

    private static String keyOf(String type, String id) {
        return type + ":" + id;
    }
}
