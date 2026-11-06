package io.matrix.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.5 — a forgetting is marked, not performed.
 *
 * <p>The mission's §8 is explicit: "forgetting must not silently destroy
 * historical evidence". The compaction of the memory backend issued a
 * DELETE, which is exactly that destruction — the evidence of what the
 * system once held, and of what it decided to let go, was gone.
 */
class TombstoneCompactionTest {

    @Test
    @DisplayName("the compaction forgets the entry, and the entry is still there")
    void theCompactionForgetsTheEntryAndTheEntryIsStillThere(@TempDir Path dir) {
        SqliteMemoryBackend backend = new SqliteMemoryBackend(dir.resolve("m.sqlite").toString());
        {
            backend.save(entry("m-1", 0.1, 1L));

            int forgotten = backend.compact(0.5, 1000);

            assertThat(forgotten).isEqualTo(1);
            // The entry is NOT gone: the evidence is retained.
            List<HierarchicalMemory.MemoryEntry> tombstones = backend.tombstones();
            assertThat(tombstones).hasSize(1);
            assertThat(tombstones.get(0).id()).isEqualTo("m-1");
            assertThat(tombstones.get(0).content()).isNotBlank();
        }
    }

    @Test
    @DisplayName("the live memory no longer returns the forgotten entry")
    void theLiveMemoryNoLongerReturnsTheForgottenEntry(@TempDir Path dir) {
        SqliteMemoryBackend backend = new SqliteMemoryBackend(dir.resolve("m.sqlite").toString());
        {
            backend.save(entry("m-1", 0.1, 1L));
            backend.save(entry("m-2", 0.9, System.currentTimeMillis()));

            backend.compact(0.5, 1000);

            assertThat(backend.listByLevel(0)).extracting(HierarchicalMemory.MemoryEntry::id)
                    .contains("m-2")
                    .doesNotContain("m-1");
            assertThat(backend.listByLevel(0)).extracting(HierarchicalMemory.MemoryEntry::id)
                    .doesNotContain("m-1");
        }
    }

    @Test
    @DisplayName("a forgotten entry can be restored")
    void aForgottenEntryCanBeRestored(@TempDir Path dir) {
        SqliteMemoryBackend backend = new SqliteMemoryBackend(dir.resolve("m.sqlite").toString());
        {
            backend.save(entry("m-1", 0.1, 1L));
            backend.compact(0.5, 1000);

            assertThat(backend.restore("m-1")).isTrue();

            assertThat(backend.listByLevel(0)).extracting(HierarchicalMemory.MemoryEntry::id)
                    .contains("m-1");
            assertThat(backend.tombstones()).isEmpty();
        }
    }

    @Test
    @DisplayName("the compaction is idempotent — a second pass forgets nothing new")
    void theCompactionIsIdempotent(@TempDir Path dir) {
        SqliteMemoryBackend backend = new SqliteMemoryBackend(dir.resolve("m.sqlite").toString());
        {
            backend.save(entry("m-1", 0.1, 1L));

            assertThat(backend.compact(0.5, 1000)).isEqualTo(1);
            assertThat(backend.compact(0.5, 1000)).isEqualTo(0);
            assertThat(backend.tombstones()).hasSize(1);
        }
    }

    @Test
    @DisplayName("the tombstone survives a restart")
    void theTombstoneSurvivesARestart(@TempDir Path dir) {
        Path db = dir.resolve("m.sqlite");
        try (SqliteMemoryBackend first = new SqliteMemoryBackend(db.toString())) {
            first.save(entry("m-1", 0.1, 1L));
            first.compact(0.5, 1000);
        }

        try (SqliteMemoryBackend backend = new SqliteMemoryBackend(db.toString())) {
            assertThat(backend.tombstones()).hasSize(1);
            assertThat(backend.listByLevel(0)).isEmpty();
        }
    }

    /** A memory entry with the given importance and creation time. */
    private HierarchicalMemory.MemoryEntry entry(String id, double importance, long createdAt) {
        return new HierarchicalMemory.MemoryEntry(
                id, HierarchicalMemory.Level.L0_ARTIFACT, "the content of " + id, "test",
                java.util.Set.of(), importance, createdAt, createdAt, 0, null, java.util.Set.of());
    }
}
