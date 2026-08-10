package io.matrix.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SqliteMemoryBackendTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoad() {
        String dbPath = tempDir.resolve("test.db").toString();
        var backend = new SqliteMemoryBackend(dbPath);

        var entry = new HierarchicalMemory.MemoryEntry(
                "test-1", HierarchicalMemory.Level.L1_PATTERN,
                "test content", "test-domain", Set.of("tag1", "tag2"),
                0.8, 1000L, 2000L, 5, null, Set.of()
        );
        backend.save(entry);

        var loaded = backend.loadAll();
        assertEquals(1, loaded.size());
        var loadedEntry = loaded.get("test-1");
        assertNotNull(loadedEntry);
        assertEquals("test content", loadedEntry.content());
        assertEquals("test-domain", loadedEntry.domain());
        assertEquals(0.8, loadedEntry.importance());
        assertEquals(5, loadedEntry.accessCount());

        backend.close();
    }

    @Test
    void delete() {
        String dbPath = tempDir.resolve("test2.db").toString();
        var backend = new SqliteMemoryBackend(dbPath);

        var entry = new HierarchicalMemory.MemoryEntry(
                "test-2", HierarchicalMemory.Level.L0_ARTIFACT,
                "to delete", "test", Set.of(),
                0.5, 1000L, 2000L, 0, null, Set.of()
        );
        backend.save(entry);
        assertEquals(1, backend.loadAll().size());

        backend.delete("test-2");
        assertEquals(0, backend.loadAll().size());
        backend.close();
    }

    @Test
    void isHealthy() {
        String dbPath = tempDir.resolve("test3.db").toString();
        var backend = new SqliteMemoryBackend(dbPath);
        assertTrue(backend.isHealthy());
        backend.close();
    }
}
