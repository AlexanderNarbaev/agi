package io.matrix.privacy.storage;

import io.matrix.privacy.Tombstone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory tombstone storage — default backend for tests and dev runs.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap}; all returned lists are
 * immutable snapshots so callers cannot accidentally mutate the audit log.
 *
 * <p>This backend is the canonical reference for tests and is used as a
 * fallback when no remote backend is configured (e.g. local dev with no
 * Postgres/Kafka available).
 */
public final class InMemoryTombstoneStorage implements TombstoneStorage {

    private final ConcurrentHashMap<String, Tombstone> byKey = new ConcurrentHashMap<>();
    private final List<Tombstone> auditLog =
            Collections.synchronizedList(new ArrayList<>());

    @Override
    public CompletableFuture<Void> append(Tombstone tombstone) {
        byKey.put(keyOf(tombstone.resourceType(), tombstone.resourceId()), tombstone);
        auditLog.add(tombstone);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Optional<Tombstone> load(String resourceType, String resourceId) {
        return Optional.ofNullable(byKey.get(keyOf(resourceType, resourceId)));
    }

    @Override
    public List<Tombstone> all() {
        return List.copyOf(auditLog);
    }

    @Override
    public List<Tombstone> filterByReason(String reasonPrefix) {
        if (reasonPrefix == null || reasonPrefix.isEmpty()) return all();
        return auditLog.stream()
                .filter(t -> t.reason() != null && t.reason().startsWith(reasonPrefix))
                .toList();
    }

    @Override
    public List<Tombstone> filterBySubject(String subjectId) {
        if (subjectId == null) return List.of();
        return auditLog.stream()
                .filter(t -> subjectId.equals(t.subjectId()))
                .toList();
    }

    @Override
    public boolean isHealthy() { return true; }

    @Override
    public String backendId() { return "memory"; }

    public int size() { return byKey.size(); }

    private static String keyOf(String type, String id) {
        return type + ":" + id;
    }
}
