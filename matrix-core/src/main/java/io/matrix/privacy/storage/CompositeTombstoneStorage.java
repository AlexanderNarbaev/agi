package io.matrix.privacy.storage;

import io.matrix.privacy.Tombstone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Composite tombstone storage — fan-out to multiple backends.
 *
 * <p>Appends are written to ALL backends (write-all). Reads consult the
 * backends in order and return the first non-empty result. {@link #all()}
 * merges the lists from every backend and de-duplicates by tombstone id.
 *
 * <p>Typical configurations:
 * <ul>
 *   <li>Kafka + Postgres: Kafka is the audit stream, Postgres is the query layer.</li>
 *   <li>Postgres + S3: Postgres for live queries, S3 for long-term archive.</li>
 *   <li>In-memory + Kafka: in-memory for unit tests, Kafka for integration tests.</li>
 * </ul>
 *
 * <p>Failure mode: a write that fails on one backend is reported, but the
 * other backends still receive the write. The caller's {@link CompletableFuture}
 * completes when the first non-recoverable error occurs (most-failed-fast
 * mode) — set {@link #strictMode} to {@code false} to instead collect
 * errors and complete successfully when at least one backend wrote the tombstone.
 */
public final class CompositeTombstoneStorage implements TombstoneStorage {

    private static final Logger log = LoggerFactory.getLogger(CompositeTombstoneStorage.class);

    private final List<TombstoneStorage> backends;
    private volatile boolean strictMode = true;

    public CompositeTombstoneStorage(List<TombstoneStorage> backends) {
        if (backends == null || backends.isEmpty()) {
            throw new IllegalArgumentException("at least one backend required");
        }
        this.backends = List.copyOf(backends);
    }

    public CompositeTombstoneStorage withStrictMode(boolean strict) {
        this.strictMode = strict;
        return this;
    }

    public List<TombstoneStorage> backends() { return backends; }

    @Override
    public CompletableFuture<Void> append(Tombstone tombstone) {
        if (backends.size() == 1) {
            return backends.get(0).append(tombstone);
        }
        AtomicInteger errors = new AtomicInteger();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (TombstoneStorage backend : backends) {
            futures.add(backend.append(tombstone)
                    .handle((v, ex) -> {
                        if (ex != null) {
                            errors.incrementAndGet();
                            log.warn("Backend {} failed to append {}: {}",
                                    backend.backendId(), tombstone.id(), ex.getMessage());
                            if (strictMode) {
                                throw new StorageUnavailableException(
                                        "Backend " + backend.backendId() + " failed", ex);
                            }
                        }
                        return v;
                    }));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    @Override
    public Optional<Tombstone> load(String resourceType, String resourceId) {
        for (TombstoneStorage backend : backends) {
            Optional<Tombstone> r = backend.load(resourceType, resourceId);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    @Override
    public List<Tombstone> all() {
        java.util.Map<java.util.UUID, Tombstone> byId = new java.util.LinkedHashMap<>();
        for (TombstoneStorage backend : backends) {
            try {
                for (Tombstone t : backend.all()) {
                    byId.putIfAbsent(t.id(), t);
                }
            } catch (RuntimeException re) {
                log.warn("Backend {} all() failed: {}", backend.backendId(), re.getMessage());
            }
        }
        return byId.values().stream()
                .sorted((a, b) -> b.deletedAt().compareTo(a.deletedAt()))
                .toList();
    }

    @Override
    public List<Tombstone> filterByReason(String reasonPrefix) {
        return all().stream()
                .filter(t -> t.reason() != null && t.reason().startsWith(reasonPrefix))
                .toList();
    }

    @Override
    public List<Tombstone> filterBySubject(String subjectId) {
        return all().stream()
                .filter(t -> subjectId.equals(t.subjectId()))
                .toList();
    }

    @Override
    public boolean isHealthy() {
        for (TombstoneStorage backend : backends) {
            if (backend.isHealthy()) return true;
        }
        return false;
    }

    @Override
    public String backendId() {
        StringBuilder sb = new StringBuilder("composite(");
        for (int i = 0; i < backends.size(); i++) {
            if (i > 0) sb.append('+');
            sb.append(backends.get(i).backendId());
        }
        return sb.append(')').toString();
    }
}
