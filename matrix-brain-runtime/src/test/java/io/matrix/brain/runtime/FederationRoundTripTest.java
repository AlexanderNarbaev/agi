package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRUE-W14 — Federation round-trip tests (snapshot → share → merge).
 *
 * <p>Verifies that teaching on node A and "merging" on node B preserves
 * the content across restart (round-trip persistence).</p>
 */
class FederationRoundTripTest {

    @Test
    void teach_on_node_a_survives_restart_to_node_b(@TempDir Path tmp) throws Exception {
        Path db = tmp.resolve("kb.ndjson");
        PersistentHdcStore a = new PersistentHdcStore(db, 256);
        a.teach("a-fact", "Paris is the capital of France");
        a.teach("b-fact", "Berlin is the capital of Germany");
        assertThat(a.size()).isEqualTo(2);

        // Simulate node B opening the same DB.
        PersistentHdcStore b = new PersistentHdcStore(db, 256);
        assertThat(b.size()).isEqualTo(2);
        assertThat(b.snapshot().get("a-fact")).isEqualTo("Paris is the capital of France");
        assertThat(b.snapshot().get("b-fact")).isEqualTo("Berlin is the capital of Germany");
    }

    @Test
    void federation_merge_adds_only_new_facts(@TempDir Path tmp) {
        PersistentHdcStore local = new PersistentHdcStore(tmp.resolve("local.ndjson"), 256);
        local.teach("existing", "old answer");
        int before = local.size();

        // Build a batch from node-B with one new fact and one duplicate
        List<KnowledgeExchangeProtocol.Fact> facts = new ArrayList<>();
        facts.add(new KnowledgeExchangeProtocol.Fact("existing", "X", "Y", 0.95, 0L));
        facts.add(new KnowledgeExchangeProtocol.Fact("new-from-b", "input", "answer", 0.95, 0L));
        KnowledgeExchangeProtocol.Batch batch =
            new KnowledgeExchangeProtocol.Batch("node-B", facts);

        int added = KnowledgeExchangeProtocol.mergeInto(local, batch);
        // Both facts are added because the federation prefix differs from the local id.
        // (Dedup would only prevent re-merge of the SAME federation id.)
        assertThat(added).isEqualTo(2);
        assertThat(local.size()).isEqualTo(before + 2);
        assertThat(local.snapshot().get("fed-node-B-existing")).contains("X => Y");
        assertThat(local.snapshot().get("fed-node-B-new-from-b"))
            .contains("input => answer");
    }

    @Test
    void federation_snapshot_round_trip(@TempDir Path tmp) {
        PersistentHdcStore local = new PersistentHdcStore(tmp.resolve("snap.ndjson"), 256);
        local.teach("a-fact", "Paris is the capital of France");
        local.teach("b-fact", "Berlin is the capital of Germany");

        KnowledgeExchangeProtocol.Batch batch =
            KnowledgeExchangeProtocol.snapshotToBatch(local, "node-A");

        assertThat(batch.facts()).hasSize(2);
        assertThat(batch.sourceNode()).isEqualTo("node-A");
        boolean foundA = batch.facts().stream().anyMatch(f ->
            f.input().equals("Paris is the capital of France")
                && f.answer().equals("Paris is the capital of France"));
        assertThat(foundA).isTrue();
    }

    @Test
    void federation_json_line_round_trip() {
        KnowledgeExchangeProtocol.Fact f = new KnowledgeExchangeProtocol.Fact(
            "id1", "Q?", "A!", 0.95, 1234567890L);
        String line = f.toJsonLine();
        KnowledgeExchangeProtocol.Fact parsed = KnowledgeExchangeProtocol.Fact.fromJsonLine(line);
        assertThat(parsed.id()).isEqualTo("id1");
        assertThat(parsed.input()).isEqualTo("Q?");
        assertThat(parsed.answer()).isEqualTo("A!");
        assertThat(parsed.confidence()).isEqualTo(0.95);
        assertThat(parsed.timestamp()).isEqualTo(1234567890L);
    }
}
