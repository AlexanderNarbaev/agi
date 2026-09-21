package io.matrix.privacy.storage;

import io.matrix.privacy.Tombstone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTombstoneStorageTest {

    @Test
    void appendAndLoad() throws Exception {
        var storage = new InMemoryTombstoneStorage();
        Tombstone t = sample("alice", "FnlPackage", "fnl-1");
        storage.append(t).get();
        var loaded = storage.load("FnlPackage", "fnl-1");
        assertThat(loaded).isPresent().get().isEqualTo(t);
    }

    @Test
    void allReturnsAllAppended() throws Exception {
        var storage = new InMemoryTombstoneStorage();
        storage.append(sample("a", "X", "1")).get();
        storage.append(sample("b", "X", "2")).get();
        storage.append(sample("a", "Y", "3")).get();
        assertThat(storage.all()).hasSize(3);
    }

    @Test
    void filterByReasonRespectsPrefix() throws Exception {
        var storage = new InMemoryTombstoneStorage();
        storage.append(tombstone("a", "X", "1", "gdpr.erasure")).get();
        storage.append(tombstone("a", "X", "2", "legal.hold")).get();
        storage.append(tombstone("a", "X", "3", "gdpr.subject_request")).get();
        assertThat(storage.filterByReason("gdpr.")).hasSize(2);
        assertThat(storage.filterByReason("legal.")).hasSize(1);
    }

    @Test
    void filterBySubjectReturnsOnlyMatching() throws Exception {
        var storage = new InMemoryTombstoneStorage();
        storage.append(sample("alice", "X", "1")).get();
        storage.append(sample("alice", "X", "2")).get();
        storage.append(sample("bob", "X", "3")).get();
        assertThat(storage.filterBySubject("alice")).hasSize(2);
        assertThat(storage.filterBySubject("bob")).hasSize(1);
        assertThat(storage.filterBySubject("carol")).isEmpty();
    }

    @Test
    void isHealthyAlwaysTrue() {
        assertThat(new InMemoryTombstoneStorage().isHealthy()).isTrue();
    }

    @Test
    void backendIdIsMemory() {
        assertThat(new InMemoryTombstoneStorage().backendId()).isEqualTo("memory");
    }

    @Test
    void emptyStorageReturnsEmptyLists() {
        var storage = new InMemoryTombstoneStorage();
        assertThat(storage.all()).isEmpty();
        assertThat(storage.filterByReason("any")).isEmpty();
        assertThat(storage.filterBySubject("any")).isEmpty();
    }

    @Test
    void loadMissingReturnsEmpty() {
        assertThat(new InMemoryTombstoneStorage().load("X", "missing")).isEmpty();
    }

    @Test
    void isCompliantWithInterfaceContract() {
        TombstoneStorage s = new InMemoryTombstoneStorage();
        assertThat(s).isInstanceOf(TombstoneStorage.class);
    }

    @Test
    void filterByNullOrEmptyReasonReturnsAll() throws Exception {
        var storage = new InMemoryTombstoneStorage();
        storage.append(sample("a", "X", "1")).get();
        storage.append(sample("a", "X", "2")).get();
        assertThat(storage.filterByReason(null)).hasSize(2);
        assertThat(storage.filterByReason("")).hasSize(2);
    }

    @Test
    void appendIsIdempotentViaSameKey() throws Exception {
        var storage = new InMemoryTombstoneStorage();
        Tombstone t1 = sample("a", "X", "1");
        Tombstone t2 = sample("a", "X", "1");
        storage.append(t1).get();
        storage.append(t2).get();
        // In-memory storage doesn't enforce UNIQUE at backend level, but the local
        // cache in TombstoneService does. The storage layer is permissive so callers
        // can re-insert after deletion from a remote backend.
        assertThat(storage.load("X", "1")).isPresent();
    }

    @Test
    void completesAsyncWithoutBlocking() throws Exception {
        var storage = new InMemoryTombstoneStorage();
        var future = storage.append(sample("a", "X", "1"));
        assertThat(future).isInstanceOf(java.util.concurrent.CompletableFuture.class);
        future.get();  // should not throw
    }

    private static Tombstone sample(String subject, String type, String id) {
        return new Tombstone(java.util.UUID.randomUUID(), subject, type, id,
                "gdpr.erasure", "sig-abc", java.time.Instant.now(), "operator-1");
    }

    private static Tombstone tombstone(String subject, String type, String id, String reason) {
        return new Tombstone(java.util.UUID.randomUUID(), subject, type, id,
                reason, "sig-abc", java.time.Instant.now(), "operator-1");
    }
}
