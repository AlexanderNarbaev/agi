package io.matrix.privacy;

import io.matrix.privacy.storage.InMemoryTombstoneStorage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TombstoneServiceWithStorageTest {

    @Test
    void serviceBackedByStorageDelegatesLoadsAndFilters() {
        InMemoryTombstoneStorage storage = new InMemoryTombstoneStorage();
        TombstoneService svc = new TombstoneService(storage);

        svc.tombstone("alice", "FnlPackage", "fnl-1", "gdpr.erasure", "sig-1", "op-1");
        svc.tombstone("alice", "FnlPackage", "fnl-2", "legal.hold", "sig-2", "op-1");
        svc.tombstone("bob", "Neuron", "n-1", "gdpr.subject_request", "sig-3", "op-2");

        // The service's local cache holds all 3.
        assertThat(svc.count()).isEqualTo(3);

        // Reads are delegated to the storage backend.
        assertThat(svc.all()).hasSize(3);
        assertThat(svc.filterByReason("gdpr.")).hasSize(2);
        assertThat(svc.filterBySubject("alice")).hasSize(2);
    }

    @Test
    void serviceExposesStorageBackendId() {
        InMemoryTombstoneStorage storage = new InMemoryTombstoneStorage();
        TombstoneService svc = new TombstoneService(storage);
        assertThat(svc.storage().backendId()).isEqualTo("memory");
    }

    @Test
    void serviceUsesInMemoryByDefault() {
        TombstoneService svc = new TombstoneService();
        assertThat(svc.storage().backendId()).isEqualTo("memory");
    }

    @Test
    void serviceHealthCheckDelegates() {
        TombstoneService svc = new TombstoneService();
        assertThat(svc.isStorageHealthy()).isTrue();
    }

    @Test
    void serviceSummaryIncludesBackendId() {
        TombstoneService svc = new TombstoneService();
        svc.tombstoneGdpr("alice", "X", "1", "op");
        String summary = svc.summary();
        assertThat(summary).contains("[memory]").contains("gdpr=1");
    }

    @Test
    void serviceIsTombstonedConsultsStorage() {
        InMemoryTombstoneStorage storage = new InMemoryTombstoneStorage();
        TombstoneService svc = new TombstoneService(storage);
        // Add via the service — caches locally.
        svc.tombstone("alice", "X", "1", "gdpr.erasure", "sig", "op");
        assertThat(svc.isTombstoned("X", "1")).isTrue();
    }

    @Test
    void serviceFindPrefersCacheOverStorage() {
        InMemoryTombstoneStorage storage = new InMemoryTombstoneStorage();
        TombstoneService svc = new TombstoneService(storage);
        Tombstone t = svc.tombstone("alice", "X", "1", "gdpr.erasure", "sig", "op");
        assertThat(svc.find("X", "1")).isEqualTo(t);
    }

    @Test
    void serviceBulkTombstoneReturnsAll() {
        InMemoryTombstoneStorage storage = new InMemoryTombstoneStorage();
        TombstoneService svc = new TombstoneService(storage);
        var created = svc.tombstoneAll("alice", "X",
                java.util.List.of("1", "2", "3"),
                "gdpr.erasure", "op");
        assertThat(created).hasSize(3);
        assertThat(svc.all()).hasSize(3);
    }

    @Test
    void serviceAllAndFilterDelegateToStorage() throws Exception {
        InMemoryTombstoneStorage storage = new InMemoryTombstoneStorage();
        TombstoneService svc = new TombstoneService(storage);
        // Insert a tombstone directly into the storage (bypassing the service).
        storage.append(new Tombstone(
                java.util.UUID.randomUUID(), "alice", "X", "1",
                "gdpr.erasure", "sig-direct", java.time.Instant.now(), "op-direct")).get();
        // The service should still see it through all() and filterByReason().
        assertThat(svc.all()).hasSize(1);
        assertThat(svc.filterByReason("gdpr.")).hasSize(1);
    }
}
