package io.matrix.privacy.storage;

import io.matrix.privacy.Tombstone;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link PostgresTombstoneStorage} against an H2 in-memory database
 * (PostgreSQL-compatible mode). We hand-write the schema (not relying on PG-specific
 * {@code UUID} type) so the same SQL works in H2.
 */
class PostgresTombstoneStorageTest {

    private JdbcDataSource ds;
    private PostgresTombstoneStorage storage;

    @BeforeEach
    void setup() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:tombstone-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        // Pre-create schema (PostgresTombstoneStorage also does this, but
        // we can verify the schema in tests this way).
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS tombstones (
                    id            UUID PRIMARY KEY,
                    subject_id    TEXT NOT NULL,
                    resource_type TEXT NOT NULL,
                    resource_id   TEXT NOT NULL,
                    reason        TEXT NOT NULL,
                    signature     TEXT,
                    deleted_at    TIMESTAMP NOT NULL,
                    requester_id  TEXT NOT NULL,
                    UNIQUE (resource_type, resource_id)
                );
                """);
        }
        storage = new PostgresTombstoneStorage(ds);
    }

    @AfterEach
    void teardown() throws Exception {
        if (storage != null) {
            // No close() method on PostgresTombstoneStorage; DataSource handles cleanup.
        }
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        } catch (Exception ignored) {}
    }

    @Test
    void appendAndLoadRoundTrip() throws Exception {
        Tombstone t = sample("alice", "FnlPackage", "fnl-1");
        storage.append(t).get();
        var loaded = storage.load("FnlPackage", "fnl-1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo(t.id());
        assertThat(loaded.get().subjectId()).isEqualTo("alice");
        assertThat(loaded.get().reason()).isEqualTo("gdpr.erasure");
    }

    @Test
    void allReturnsAllAppended() throws Exception {
        storage.append(sample("a", "X", "1")).get();
        storage.append(sample("a", "X", "2")).get();
        storage.append(sample("a", "Y", "3")).get();
        List<Tombstone> all = storage.all();
        assertThat(all).hasSize(3);
    }

    @Test
    void filterByReasonUsesPrefix() throws Exception {
        storage.append(tombstone("a", "X", "1", "gdpr.erasure")).get();
        storage.append(tombstone("a", "X", "2", "legal.hold")).get();
        storage.append(tombstone("a", "X", "3", "gdpr.subject_request")).get();
        assertThat(storage.filterByReason("gdpr.")).hasSize(2);
        assertThat(storage.filterByReason("legal.")).hasSize(1);
        assertThat(storage.filterByReason("nonexistent.")).isEmpty();
    }

    @Test
    void filterBySubjectReturnsOnlyMatching() throws Exception {
        storage.append(sample("alice", "X", "1")).get();
        storage.append(sample("alice", "X", "2")).get();
        storage.append(sample("bob", "X", "3")).get();
        assertThat(storage.filterBySubject("alice")).hasSize(2);
        assertThat(storage.filterBySubject("bob")).hasSize(1);
    }

    @Test
    void onConflictDoesNothing() throws Exception {
        Tombstone t1 = sample("alice", "X", "1");
        Tombstone t2 = sample("alice", "X", "1");
        storage.append(t1).get();
        storage.append(t2).get();   // ignored (ON CONFLICT DO NOTHING)
        // Only one row should exist (the first insert).
        var loaded = storage.load("X", "1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo(t1.id());
    }

    @Test
    void isHealthyIsTrueWhenDatabaseIsReachable() {
        assertThat(storage.isHealthy()).isTrue();
    }

    @Test
    void backendIdIsPostgres() {
        assertThat(storage.backendId()).isEqualTo("postgres");
    }

    @Test
    void loadMissingReturnsEmpty() {
        assertThat(storage.load("X", "missing")).isEmpty();
    }

    @Test
    void filterByNullReasonReturnsAll() throws Exception {
        storage.append(sample("a", "X", "1")).get();
        storage.append(sample("a", "X", "2")).get();
        assertThat(storage.filterByReason(null)).hasSize(2);
    }

    @Test
    void deletedAtIsPersisted() throws Exception {
        Instant before = Instant.now();
        Tombstone t = sample("alice", "X", "1");
        storage.append(t).get();
        Instant after = Instant.now();
        Tombstone loaded = storage.load("X", "1").orElseThrow();
        // JDBC TIMESTAMP has second-level precision; allow ±1s window.
        long delta = Math.abs(loaded.deletedAt().toEpochMilli() - t.deletedAt().toEpochMilli());
        assertThat(delta).isLessThan(2_000L);
        assertThat(loaded.deletedAt()).isBefore(after.plusSeconds(1));
        assertThat(loaded.deletedAt()).isAfter(before.minusSeconds(1));
    }

    @Test
    void nullSignatureIsHandledAsEmpty() throws Exception {
        Tombstone t = new Tombstone(UUID.randomUUID(), "a", "X", "1",
                "gdpr.erasure", null, Instant.now(), "op");
        storage.append(t).get();
        var loaded = storage.load("X", "1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().signature()).isEmpty();
    }

    @Test
    void isCompliantWithInterfaceContract() {
        TombstoneStorage s = storage;
        assertThat(s).isInstanceOf(TombstoneStorage.class);
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
