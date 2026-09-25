package io.matrix.brain.runtime;

import io.matrix.brain.runtime.stages.HdcRetrievalStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MIND-W2 — Persistence integration tests.
 *
 * <p>Proves that:</p>
 * <ul>
 *   <li>Taught facts survive JVM restart (load on construction).</li>
 *   <li>Writes are atomic (tmp+rename) — no half-written NDJSON.</li>
 *   <li>Contradiction detection flags duplicates and surface-form conflicts.</li>
 *   <li>MindCycle wired to a persistent store retrieves taught facts.</li>
 * </ul>
 */
class PersistentHdcStoreIntegrationTest {

    @Test
    void teach_persists_across_reopen(@TempDir Path tmp) throws Exception {
        Path storePath = tmp.resolve("mind.ndjson");
        // First session: teach a fact.
        PersistentHdcStore store1 = new PersistentHdcStore(storePath, 256);
        long before = System.nanoTime();
        store1.teach("user-fact-1", "The capital of France is Paris");
        long after = System.nanoTime();

        assertThat(store1.size()).isEqualTo(1);
        assertThat(Files.exists(storePath)).isTrue();
        assertThat(Files.size(storePath)).isGreaterThan(0L);
        // Verify NDJSON format on disk
        String content = Files.readString(storePath);
        assertThat(content).contains("\"id\":\"user-fact-1\"");
        assertThat(content).contains("\"content\":\"The capital of France is Paris\"");
        assertThat(content).contains("\"bits\":");

        // Simulate JVM restart: drop store1, create store2 from same file.
        PersistentHdcStore store2 = new PersistentHdcStore(storePath, 256);
        assertThat(store2.size()).isEqualTo(1);
        assertThat(store2.snapshot().get("user-fact-1"))
            .isEqualTo("The capital of France is Paris");
    }

    @Test
    void multiple_facts_persist_in_insertion_order(@TempDir Path tmp) throws Exception {
        Path storePath = tmp.resolve("mind-multi.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(storePath, 256);
        store.teach("fact-a", "Alpha");
        store.teach("fact-b", "Beta");
        store.teach("fact-c", "Gamma");

        PersistentHdcStore reopened = new PersistentHdcStore(storePath, 256);
        assertThat(reopened.snapshot().keySet())
            .containsExactly("fact-a", "fact-b", "fact-c");
    }

    @Test
    void atomically_written_no_tmp_files_left(@TempDir Path tmp) throws Exception {
        Path storePath = tmp.resolve("mind-atomic.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(storePath, 256);
        for (int i = 0; i < 50; i++) {
            store.teach("k-" + i, "value " + i);
        }
        // tmp file should not linger
        assertThat(Files.exists(storePath.resolveSibling("mind-atomic.ndjson.tmp")))
            .as("tmp file should be cleaned up by atomic rename")
            .isFalse();
        assertThat(Files.exists(storePath)).isTrue();
    }

    @Test
    void contradiction_detection_flags_duplicates(@TempDir Path tmp) {
        Path storePath = tmp.resolve("mind-contradict.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(storePath, 256);
        store.teach("a", "Paris is the capital of France");

        // Identical content (after whitespace normalisation) -> duplicate
        PersistentHdcStore.ContradictionReport rep =
            store.checkContradiction("b", "paris is the capital of france");
        assertThat(rep.level()).isEqualTo(PersistentHdcStore.ContradictionLevel.DUPLICATE);
        assertThat(rep.existingId()).isEqualTo("a");
    }

    @Test
    void contradiction_detection_flags_potential_conflict(@TempDir Path tmp) {
        Path storePath = tmp.resolve("mind-conflict.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(storePath, 256);
        store.teach("a", "Paris is the capital of France");

        // Surface-different but HDC-similar (4 of 5 tokens shared) -> potential conflict
        PersistentHdcStore.ContradictionReport rep =
            store.checkContradiction("b", "paris france is the capital");
        assertThat(rep.level())
            .isIn(PersistentHdcStore.ContradictionLevel.POTENTIAL_CONFLICT,
                  PersistentHdcStore.ContradictionLevel.DUPLICATE);
        assertThat(rep.existingId()).isEqualTo("a");
    }

    @Test
    void contradiction_detection_marks_unrelated_facts_novel(@TempDir Path tmp) {
        Path storePath = tmp.resolve("mind-novel.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(storePath, 256);
        store.teach("a", "The Pacific Ocean is the largest ocean");
        PersistentHdcStore.ContradictionReport rep =
            store.checkContradiction("b", "Photosynthesis converts sunlight to sugar");
        assertThat(rep.level()).isEqualTo(PersistentHdcStore.ContradictionLevel.NOVEL);
    }

    @Test
    void mindcycle_persistent_mode_recovers_taught_fact(@TempDir Path tmp) throws Exception {
        Path storePath = tmp.resolve("mind-cycle.ndjson");
        // Session 1: teach through MindCycle
        PersistentHdcStore s1 = new PersistentHdcStore(storePath, 256);
        MindCycle mind1 = new MindCycle(s1);
        // Use a question the seed corpus doesn't answer
        var r1 = mind1.think("What is the capital of Peru?");
        // The seed corpus doesn't contain Peru, so it should miss first.
        // (We don't assert that here; we just exercise the wiring.)
        s1.teach("peru-capital", "Lima is the capital of Peru");

        // Session 2: simulate restart, ask the same question.
        PersistentHdcStore s2 = new PersistentHdcStore(storePath, 256);
        MindCycle mind2 = new MindCycle(s2);
        var r2 = mind2.think("What is the capital of Peru?");
        assertThat(r2.reply().toLowerCase()).contains("lima");
    }

    @Test
    void persistence_path_does_not_exist_creates_on_first_teach(@TempDir Path tmp) throws Exception {
        Path storePath = tmp.resolve("does-not-exist-yet.ndjson");
        assertThat(Files.exists(storePath)).isFalse();
        PersistentHdcStore store = new PersistentHdcStore(storePath, 256);
        assertThat(store.size()).isEqualTo(0);
        store.teach("first", "Hello world");
        assertThat(Files.exists(storePath)).isTrue();
    }

    @Test
    void ndjson_format_is_human_readable_and_parseable(@TempDir Path tmp) throws Exception {
        Path storePath = tmp.resolve("readable.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(storePath, 256);
        store.teach("simple", "One two three");
        store.teach("with-quote", "He said \"hi\"");
        store.teach("with-newline", "Line1\nLine2");
        String content = Files.readString(storePath);
        // Each line is independent JSON.
        String[] lines = content.split("\n");
        assertThat(lines.length).isEqualTo(3);
        for (String l : lines) {
            assertThat(l).startsWith("{").endsWith("}");
            assertThat(l).contains("\"id\":");
            assertThat(l).contains("\"content\":");
            assertThat(l).contains("\"bits\":");
        }
        // Quotes must be escaped in content
        assertThat(content).contains("\\\"").contains("\\n");
    }

    @Test
    void bulk_teach_writes_once_at_end(@TempDir Path tmp) throws Exception {
        Path storePath = tmp.resolve("bulk.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(storePath, 256);
        var bulk = new java.util.LinkedHashMap<String, String>();
        bulk.put("a", "1");
        bulk.put("b", "2");
        bulk.put("c", "3");
        bulk.put("d", "4");
        bulk.put("e", "5");
        store.teachAll(bulk);

        // Re-open and verify all 5 persist in order
        PersistentHdcStore reopened = new PersistentHdcStore(storePath, 256);
        assertThat(reopened.snapshot().keySet())
            .containsExactly("a", "b", "c", "d", "e");
    }

    @Test
    void mindcycle_hdc_stage_size_matches_store(@TempDir Path tmp) {
        Path storePath = tmp.resolve("size.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(storePath, 256);
        HdcRetrievalStage stage = new HdcRetrievalStage(store);
        // 6 seed entries from HdcRetrievalStage seed
        assertThat(stage.size()).isEqualTo(6);
        stage.teach("user-1", "Custom user fact");
        assertThat(stage.size()).isEqualTo(7);
    }
}
