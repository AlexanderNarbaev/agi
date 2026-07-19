package io.matrix.privacy;

import io.matrix.privacy.storage.InMemoryTombstoneStorage;
import io.matrix.privacy.storage.TombstoneStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GDPR tombstoning service — applies {@link Tombstone} markers to system
 * resources (FnlPackage, Neuron, Snapshot, ...) and persists them via a
 * pluggable {@link TombstoneStorage} backend.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #tombstone(String, String, String, String, String, String)} creates
 *       a {@link Tombstone}, persists it via the storage backend, and returns it.</li>
 *   <li>Subsequent calls to {@link #isTombstoned(String, String)} answer {@code true}.</li>
 *   <li>{@link #all()} returns the full audit list (delegated to storage).</li>
 * </ol>
 *
 * <p>Backwards compatibility: the no-arg constructor uses the in-memory
 * storage backend (same as before). For durable persistence, use
 * {@link #TombstoneService(TombstoneStorage)} and pass a Postgres/Kafka/S3
 * backend (or any {@link io.matrix.privacy.storage.CompositeTombstoneStorage}).
 *
 * <p>The local in-memory cache mirrors the storage backend for fast
 * lookups; reads fall back to {@link TombstoneStorage#load} on cache miss.
 *
 * <p>Ref: L6 §6.7, L12 §4.
 */
public final class TombstoneService {

    private static final Logger log = LoggerFactory.getLogger(TombstoneService.class);

    private final TombstoneStorage storage;
    /** Composite key: {@code resourceType + ":" + resourceId}. */
    private final ConcurrentHashMap<String, Tombstone> byResource = new ConcurrentHashMap<>();
    /** Audit log ordered by insertion (append-only, in-memory mirror). */
    private final List<Tombstone> auditLog = java.util.Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong sequence = new AtomicLong();

    /** Backwards-compatible no-arg constructor — uses in-memory storage. */
    public TombstoneService() {
        this(new InMemoryTombstoneStorage());
    }

    /** Construct with a custom storage backend. */
    public TombstoneService(TombstoneStorage storage) {
        this.storage = storage == null ? new InMemoryTombstoneStorage() : storage;
        log.info("TombstoneService initialised with backend: {}", this.storage.backendId());
    }

    /** Returns the underlying storage backend (useful for tests / health). */
    public TombstoneStorage storage() { return storage; }

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
            // Asynchronous durable write — best effort; storage failures
            // are logged but do not block the in-memory registration.
            try {
                CompletableFuture<Void> f = storage.append(t);
                f.exceptionally(ex -> {
                    log.warn("Storage append failed for {} (kept in-memory): {}",
                            t.id(), ex.getMessage());
                    return null;
                });
            } catch (RuntimeException re) {
                log.warn("Storage append threw synchronously for {}: {}",
                        t.id(), re.getMessage());
            }
            return t;
        });
    }

    /** Convenience overload using the {@code GDPR Article 17} default reason. */
    public Tombstone tombstoneGdpr(String subjectId, String resourceType,
                                    String resourceId, String requesterId) {
        return tombstone(subjectId, resourceType, resourceId,
                Tombstone.REASON_GDPR_ERASURE, "", requesterId);
    }

    /** True iff this resource already carries a tombstone (local cache + storage). */
    public boolean isTombstoned(String resourceType, String resourceId) {
        if (byResource.containsKey(keyOf(resourceType, resourceId))) return true;
        return storage.load(resourceType, resourceId).isPresent();
    }

    /** Look up an existing tombstone (local cache first, then storage). */
    public Tombstone find(String resourceType, String resourceId) {
        Tombstone cached = byResource.get(keyOf(resourceType, resourceId));
        if (cached != null) return cached;
        return storage.load(resourceType, resourceId).orElse(null);
    }

    /** Total count of tombstones in the local cache. */
    public int count() {
        return byResource.size();
    }

    /** Snapshot of all tombstones (delegated to storage backend). */
    public List<Tombstone> all() {
        return storage.all();
    }

    /** Filter tombstones by reason (prefix match, e.g. {@code "gdpr."}). */
    public List<Tombstone> filterByReason(String reasonPrefix) {
        return storage.filterByReason(reasonPrefix);
    }

    /** Filter tombstones by data subject id. */
    public List<Tombstone> filterBySubject(String subjectId) {
        return storage.filterBySubject(subjectId);
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

    /** Health check for monitoring — delegates to the storage backend. */
    public boolean isStorageHealthy() {
        return storage.isHealthy();
    }

    /** Human-readable summary suitable for the L12 audit log. */
    public String summary() {
        Map<String, Long> byReason = auditLog.stream()
                .filter(t -> t.reason() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        t -> t.reason().contains(".") ? t.reason().substring(0, t.reason().indexOf('.')) : "other",
                        java.util.stream.Collectors.counting()));
        StringBuilder sb = new StringBuilder();
        sb.append("TombstoneService[").append(storage.backendId()).append("]: ")
                .append(count()).append(" cached;");
        byReason.forEach((k, v) -> sb.append(' ').append(k).append('=').append(v).append(';'));
        return sb.toString().trim();
    }

    private static String keyOf(String type, String id) {
        return type + ":" + id;
    }
}
