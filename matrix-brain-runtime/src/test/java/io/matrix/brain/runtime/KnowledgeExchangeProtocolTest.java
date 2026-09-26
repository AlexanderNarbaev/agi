package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRUE-W14 — Knowledge Exchange Protocol tests.
 */
class KnowledgeExchangeProtocolTest {

    @Test
    void snapshot_extracts_input_and_answer_with_separator(@TempDir Path tmp) {
        PersistentHdcStore store = new PersistentHdcStore(
            tmp.resolve("kb.ndjson"), 256);
        store.teach("teach-1", "What is X? => 42");
        KnowledgeExchangeProtocol.Batch b =
            KnowledgeExchangeProtocol.snapshotToBatch(store, "node-A");
        assertThat(b.facts()).hasSize(1);
        assertThat(b.facts().get(0).input()).isEqualTo("What is X?");
        assertThat(b.facts().get(0).answer()).isEqualTo("42");
    }

    @Test
    void snapshot_handles_legacy_content_without_separator(@TempDir Path tmp) {
        PersistentHdcStore store = new PersistentHdcStore(
            tmp.resolve("kb.ndjson"), 256);
        store.teach("seed-1", "Paris is the capital of France");
        KnowledgeExchangeProtocol.Batch b =
            KnowledgeExchangeProtocol.snapshotToBatch(store, "node-A");
        assertThat(b.facts()).hasSize(1);
        // Legacy content without " => " gets the full content as both fields
        // (federation-side splitting is best-effort).
        assertThat(b.facts().get(0).input()).isEqualTo("Paris is the capital of France");
        assertThat(b.facts().get(0).answer()).isEqualTo("Paris is the capital of France");
    }

    @Test
    void batch_json_round_trip(@TempDir Path tmp) {
        List<KnowledgeExchangeProtocol.Fact> facts = new ArrayList<>();
        facts.add(new KnowledgeExchangeProtocol.Fact("f-1", "Q", "A", 0.95, 12345L));
        KnowledgeExchangeProtocol.Batch b = new KnowledgeExchangeProtocol.Batch("node-Z", facts);
        String json = b.toJsonArray();
        // Parse back
        KnowledgeExchangeProtocol.Fact roundTrip =
            KnowledgeExchangeProtocol.Fact.fromJsonLine(
                json.substring(json.indexOf("{"), json.indexOf("}") + 1));
        assertThat(roundTrip.input()).isEqualTo("Q");
        assertThat(roundTrip.answer()).isEqualTo("A");
        assertThat(roundTrip.confidence()).isEqualTo(0.95);
    }

    @Test
    void merge_into_preserves_existing_facts(@TempDir Path tmp) {
        PersistentHdcStore store = new PersistentHdcStore(
            tmp.resolve("kb.ndjson"), 256);
        // First, simulate that node-B's fact already exists locally (using
        // the same id pattern the federation produces).
        store.teach("fed-node-B-shared-fact", "Q1 => A1");
        int beforeSize = store.size();
        // Re-feed same fact from same node — should be deduplicated by id.
        KnowledgeExchangeProtocol.Batch dup = new KnowledgeExchangeProtocol.Batch(
            "node-B", List.of(new KnowledgeExchangeProtocol.Fact(
                "shared-fact", "Q1", "A1", 0.95, 0L)));
        int added = KnowledgeExchangeProtocol.mergeInto(store, dup);
        assertThat(added).isZero();
        assertThat(store.size()).isEqualTo(beforeSize);
    }

    @Test
    void merge_into_adds_new_facts_with_source_prefix(@TempDir Path tmp) {
        PersistentHdcStore store = new PersistentHdcStore(
            tmp.resolve("kb.ndjson"), 256);
        KnowledgeExchangeProtocol.Batch fresh = new KnowledgeExchangeProtocol.Batch(
            "node-X", List.of(new KnowledgeExchangeProtocol.Fact(
                "new-fact", "What is Y?", "Y answer", 0.9, 0L)));
        int added = KnowledgeExchangeProtocol.mergeInto(store, fresh);
        assertThat(added).isEqualTo(1);
        // Verify the entry is now queryable
        assertThat(store.snapshot().get("fed-node-X-new-fact"))
            .contains("What is Y? => Y answer");
    }
}
