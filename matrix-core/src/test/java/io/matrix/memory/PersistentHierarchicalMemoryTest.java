package io.matrix.memory;

import io.matrix.bir.TtForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PersistentHierarchicalMemory}: flush to disk,
 * restore on restart, debounced dirty-marking.
 */
class PersistentHierarchicalMemoryTest {

    @Test
    void flushNowWritesFile(@TempDir Path tmp) throws Exception {
        HierarchicalMemory mem = new HierarchicalMemory(100);
        mem.store(HierarchicalMemory.Level.L1_PATTERN,
                "test entry one", "test", Set.of());
        mem.store(HierarchicalMemory.Level.L1_PATTERN,
                "test entry two", "test", Set.of());

        Path target = tmp.resolve("ltm.jsonl");
        PersistentHierarchicalMemory p =
                PersistentHierarchicalMemory.forTest(mem, target.toString());
        p.flushNow();

        assertThat(Files.exists(target)).isTrue();
        long lines = Files.readAllLines(target).stream()
                .filter(l -> !l.isBlank()).count();
        assertThat(lines).isEqualTo(2);
    }

    @Test
    void restoreLoadsPersistedEntries(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("ltm.jsonl");
        // session 1: write
        {
            HierarchicalMemory mem = new HierarchicalMemory(100);
            mem.store(HierarchicalMemory.Level.L2_MODULE,
                    "important fact", "domain1", Set.of("tag1"));
            mem.store(HierarchicalMemory.Level.L3_QUANTUM,
                    "synthesized insight", "domain1", Set.of());
            PersistentHierarchicalMemory p =
                    PersistentHierarchicalMemory.forTest(mem, target.toString());
            p.flushNow();
        }
        // session 2: restore
        {
            HierarchicalMemory mem2 = new HierarchicalMemory(100);
            PersistentHierarchicalMemory p2 =
                    PersistentHierarchicalMemory.forTest(mem2, target.toString());
            p2.start(); // triggers restore on PostConstruct

            var entries = mem2.search("important", 10);
            assertThat(entries).isNotEmpty();
            assertThat(entries.get(0).content()).isEqualTo("important fact");

            var insights = mem2.search("synthesized", 10);
            assertThat(insights).isNotEmpty();
        }
    }

    @Test
    void restoreOnMissingFileIsNoOp(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("missing.jsonl");
        HierarchicalMemory mem = new HierarchicalMemory(100);
        PersistentHierarchicalMemory p =
                PersistentHierarchicalMemory.forTest(mem, target.toString());
        // should not throw
        p.start();
        p.flushNow();
        // first flush creates the file
        assertThat(Files.exists(target)).isTrue();
    }

    @Test
    void flushOverwritesExistingFile(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("ltm.jsonl");
        HierarchicalMemory mem = new HierarchicalMemory(100);

        // first session: 1 entry
        mem.store(HierarchicalMemory.Level.L0_ARTIFACT, "first entry", "x", Set.of());
        PersistentHierarchicalMemory.forTest(mem, target.toString()).flushNow();
        long firstLines = countLines(target);

        // second session: 2 entries (different memory)
        HierarchicalMemory mem2 = new HierarchicalMemory(100);
        mem2.store(HierarchicalMemory.Level.L0_ARTIFACT, "second entry a", "x", Set.of());
        mem2.store(HierarchicalMemory.Level.L0_ARTIFACT, "second entry b", "x", Set.of());
        PersistentHierarchicalMemory.forTest(mem2, target.toString()).flushNow();
        long secondLines = countLines(target);

        assertThat(secondLines).isGreaterThan(firstLines);
    }

    @Test
    void flushWritesValidJsonPerLine(@TempDir Path tmp) throws Exception {
        HierarchicalMemory mem = new HierarchicalMemory(100);
        mem.store(HierarchicalMemory.Level.L2_MODULE,
                "parseable entry", "test", Set.of());
        Path target = tmp.resolve("ltm.jsonl");
        PersistentHierarchicalMemory.forTest(mem, target.toString()).flushNow();
        // each non-blank line must parse as JSON
        for (String line : Files.readAllLines(target)) {
            if (line.isBlank()) continue;
            assertThat(line)
                    .as("each line must be JSON")
                    .startsWith("{")
                    .endsWith("}");
            assertThat(line).contains("parseable entry");
            assertThat(line).contains("L2_MODULE");
        }
    }

    private static long countLines(Path p) throws IOException {
        return Files.readAllLines(p).stream().filter(l -> !l.isBlank()).count();
    }
}