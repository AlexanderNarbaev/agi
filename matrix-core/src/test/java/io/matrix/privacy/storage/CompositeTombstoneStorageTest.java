package io.matrix.privacy.storage;

import io.matrix.privacy.Tombstone;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeTombstoneStorageTest {

    @Test
    void requiresAtLeastOneBackend() {
        assertThatThrownBy(() -> new CompositeTombstoneStorage(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresNonNullBackends() {
        assertThatThrownBy(() -> new CompositeTombstoneStorage(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void singleBackendBehavesLikeUnderlying() throws Exception {
        var mem = new InMemoryTombstoneStorage();
        var composite = new CompositeTombstoneStorage(List.of(mem));
        Tombstone t = sample("a", "X", "1");
        composite.append(t).get();
        assertThat(composite.load("X", "1")).isPresent().get().isEqualTo(t);
        assertThat(composite.all()).hasSize(1);
    }

    @Test
    void appendFanOutsToAllBackends() throws Exception {
        var mem1 = new InMemoryTombstoneStorage();
        var mem2 = new InMemoryTombstoneStorage();
        var composite = new CompositeTombstoneStorage(List.of(mem1, mem2));
        Tombstone t = sample("a", "X", "1");
        composite.append(t).get();
        assertThat(mem1.load("X", "1")).isPresent();
        assertThat(mem2.load("X", "1")).isPresent();
    }

    @Test
    void allMergesAndDeDuplicatesById() throws Exception {
        var mem1 = new InMemoryTombstoneStorage();
        var mem2 = new InMemoryTombstoneStorage();
        Tombstone t1 = sample("a", "X", "1");
        mem1.append(t1).get();
        mem2.append(t1).get();  // same id in both
        var composite = new CompositeTombstoneStorage(List.of(mem1, mem2));
        assertThat(composite.all()).hasSize(1);
    }

    @Test
    void loadReturnsFirstNonEmpty() throws Exception {
        var mem1 = new InMemoryTombstoneStorage();
        var mem2 = new InMemoryTombstoneStorage();
        Tombstone t1 = sample("a", "X", "1");
        mem1.append(t1).get();
        var composite = new CompositeTombstoneStorage(List.of(mem1, mem2));
        var loaded = composite.load("X", "1");
        assertThat(loaded).isPresent().get().isEqualTo(t1);
    }

    @Test
    void isHealthyIsTrueIfAnyBackendIsHealthy() {
        var healthy1 = new InMemoryTombstoneStorage();
        var healthy2 = new InMemoryTombstoneStorage();
        var composite = new CompositeTombstoneStorage(List.of(healthy1, healthy2));
        assertThat(composite.isHealthy()).isTrue();
    }

    @Test
    void backendIdListsAllUnderlyingIds() {
        var composite = new CompositeTombstoneStorage(List.of(
                new InMemoryTombstoneStorage(), new InMemoryTombstoneStorage()));
        assertThat(composite.backendId()).isEqualTo("composite(memory+memory)");
    }

    @Test
    void filterByReasonRespectsBackendResults() throws Exception {
        var mem1 = new InMemoryTombstoneStorage();
        var mem2 = new InMemoryTombstoneStorage();
        mem1.append(tombstone("a", "X", "1", "gdpr.erasure")).get();
        mem2.append(tombstone("a", "X", "2", "legal.hold")).get();
        var composite = new CompositeTombstoneStorage(List.of(mem1, mem2));
        assertThat(composite.filterByReason("gdpr.")).hasSize(1);
        assertThat(composite.filterByReason("legal.")).hasSize(1);
    }

    @Test
    void filterBySubjectMergesAcrossBackends() throws Exception {
        var mem1 = new InMemoryTombstoneStorage();
        var mem2 = new InMemoryTombstoneStorage();
        mem1.append(sample("alice", "X", "1")).get();
        mem2.append(sample("alice", "X", "2")).get();
        mem2.append(sample("bob", "X", "3")).get();
        var composite = new CompositeTombstoneStorage(List.of(mem1, mem2));
        assertThat(composite.filterBySubject("alice")).hasSize(2);
        assertThat(composite.filterBySubject("bob")).hasSize(1);
    }

    @Test
    void nonStrictModeContinuesOnError() throws Exception {
        var mem = new InMemoryTombstoneStorage();
        var failing = new TombstoneStorage() {
            @Override public java.util.concurrent.CompletableFuture<Void> append(Tombstone t) {
                return java.util.concurrent.CompletableFuture.failedFuture(
                        new RuntimeException("simulated failure"));
            }
            @Override public java.util.Optional<Tombstone> load(String t, String id) { return java.util.Optional.empty(); }
            @Override public List<Tombstone> all() { return List.of(); }
            @Override public List<Tombstone> filterByReason(String p) { return List.of(); }
            @Override public List<Tombstone> filterBySubject(String s) { return List.of(); }
            @Override public boolean isHealthy() { return false; }
            @Override public String backendId() { return "failing"; }
        };
        var composite = new CompositeTombstoneStorage(List.of(failing, mem)).withStrictMode(false);
        Tombstone t = sample("a", "X", "1");
        composite.append(t).get();
        // The tombstone was written to the working backend.
        assertThat(mem.load("X", "1")).isPresent();
    }

    @Test
    void backendsAccessorReturnsUnmodifiableList() {
        var mem = new InMemoryTombstoneStorage();
        var composite = new CompositeTombstoneStorage(List.of(mem));
        var list = composite.backends();
        assertThat(list).hasSize(1);
        assertThat(list).containsExactly(mem);
        assertThatThrownBy(() -> list.add(new InMemoryTombstoneStorage()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static Tombstone sample(String subject, String type, String id) {
        return new Tombstone(UUID.randomUUID(), subject, type, id,
                "gdpr.erasure", "sig-abc", Instant.now(), "operator-1");
    }

    private static Tombstone tombstone(String subject, String type, String id, String reason) {
        return new Tombstone(UUID.randomUUID(), subject, type, id,
                reason, "sig-abc", Instant.now(), "operator-1");
    }
}
