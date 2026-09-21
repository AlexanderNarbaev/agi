package io.matrix.research;

import io.matrix.memory.HierarchicalMemory;
import io.matrix.memory.PersistentHierarchicalMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 319 — EXP: Long-Term Memory (LTM) roundtrip across simulated
 * JVM restart (Wave H.1 acceptance).
 *
 * <p>Phase A: create {@link PersistentHierarchicalMemory}, store
 * 50 entries, force-flush to disk. The persistence file exists.
 *
 * <p>Phase B: simulate JVM restart by constructing a brand-new
 * {@link PersistentHierarchicalMemory} against the same on-disk
 * file with a fresh {@link HierarchicalMemory} backing it.
 * {@code start()} restores from disk.
 *
 * <p>Acceptance: phase B contains exactly 50 entries, all keys
 * and payloads (contents) match phase A in the same order.
 *
 * <p>This test is the in-process equivalent of "write 50 entries,
 * kill JVM, restart JVM, read 50 entries, diff = ∅" — the
 * persistence layer is the only state that crosses the boundary,
 * so the test exercises the same code path a true restart would.
 */
class Exp319LtmRestartRoundtripTest {

    @Test
    void fiftyEntriesRoundtripAcrossJvmRestart(@TempDir Path tmp) throws Exception {
        // ---- Phase A: write 50 entries + flush ----
        Path persistenceFile = tmp.resolve("ltm-restart.jsonl");
        HierarchicalMemory memA = new HierarchicalMemory();
        PersistentHierarchicalMemory pA = PersistentHierarchicalMemory.forTest(
                memA, persistenceFile.toString());
        pA.start();

        List<String> expectedContents = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String content = "content-payload-" + i + "-" + (char) ('a' + (i % 26));
            memA.store(HierarchicalMemory.Level.L2_MODULE, content, "domain-x", Set.of("tag-" + i));
            expectedContents.add(content);
            pA.markDirty();
        }
        // Force a synchronous flush (don't wait for the periodic timer)
        pA.flushNow();

        assertThat(Files.exists(persistenceFile)).isTrue();
        long lineCount = Files.readAllLines(persistenceFile).stream()
                .filter(l -> !l.isBlank()).count();
        assertThat(lineCount).isEqualTo(50);

        // ---- Phase B: simulate JVM restart ----
        HierarchicalMemory memB = new HierarchicalMemory();
        PersistentHierarchicalMemory pB = PersistentHierarchicalMemory.forTest(
                memB, persistenceFile.toString());
        // start() triggers restore() which reads the JSONL back
        pB.start();

        // ---- Acceptance ----
        List<HierarchicalMemory.MemoryEntry> roundtripped = memB.entriesAtLevel(
                HierarchicalMemory.Level.L2_MODULE);
        assertThat(roundtripped).hasSize(50);

        List<String> actualContents = new ArrayList<>();
        for (HierarchicalMemory.MemoryEntry entry : roundtripped) {
            actualContents.add(entry.content());
        }
        assertThat(actualContents).containsExactlyInAnyOrderElementsOf(expectedContents);

        // Domain + tags also roundtrip
        for (HierarchicalMemory.MemoryEntry entry : roundtripped) {
            assertThat(entry.domain()).isEqualTo("domain-x");
            assertThat(entry.tags()).hasSize(1);
        }
    }

    @Test
    void emptyFileRestoresToZero(@TempDir Path tmp) throws Exception {
        Path persistenceFile = tmp.resolve("ltm-empty.jsonl");
        HierarchicalMemory mem = new HierarchicalMemory();
        PersistentHierarchicalMemory p = PersistentHierarchicalMemory.forTest(
                mem, persistenceFile.toString());
        p.start();
        assertThat(mem.entriesAtLevel(HierarchicalMemory.Level.L2_MODULE)).isEmpty();
    }

    @Test
    void restartTwiceKeepsData(@TempDir Path tmp) throws Exception {
        // First session: write 10 entries
        Path persistenceFile = tmp.resolve("ltm-double.jsonl");
        HierarchicalMemory memA = new HierarchicalMemory();
        PersistentHierarchicalMemory pA = PersistentHierarchicalMemory.forTest(
                memA, persistenceFile.toString());
        pA.start();
        for (int i = 0; i < 10; i++) {
            memA.store(HierarchicalMemory.Level.L1_PATTERN, "msg-" + i, "chat", Set.of());
            pA.markDirty();
        }
        pA.flushNow();

        // Second session: load + add 5 more
        HierarchicalMemory memB = new HierarchicalMemory();
        PersistentHierarchicalMemory pB = PersistentHierarchicalMemory.forTest(
                memB, persistenceFile.toString());
        pB.start();
        assertThat(memB.entriesAtLevel(HierarchicalMemory.Level.L1_PATTERN)).hasSize(10);
        for (int i = 10; i < 15; i++) {
            memB.store(HierarchicalMemory.Level.L1_PATTERN, "msg-" + i, "chat", Set.of());
            pB.markDirty();
        }
        pB.flushNow();

        // Third session: load again
        HierarchicalMemory memC = new HierarchicalMemory();
        PersistentHierarchicalMemory pC = PersistentHierarchicalMemory.forTest(
                memC, persistenceFile.toString());
        pC.start();
        assertThat(memC.entriesAtLevel(HierarchicalMemory.Level.L1_PATTERN)).hasSize(15);
    }
}
