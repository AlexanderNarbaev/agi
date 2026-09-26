package io.matrix.brain.runtime;

import io.matrix.brain.runtime.stages.HdcRetrievalStage;
import io.matrix.brain.runtime.stages.SignalStage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRUE-W14 — End-to-end smoke tests (teach + retrieve via HDC pipeline).
 */
class GatewayIntegrationSmokeTest {

    private HdcRetrievalStage.HdcResult query(Path db, String q) {
        PersistentHdcStore store = new PersistentHdcStore(db, 256);
        HdcRetrievalStage stage = new HdcRetrievalStage(store);
        SignalStage ss = new SignalStage();
        var obs = ss.encode(q, new ArrayList<>());
        return stage.retrieve(q, obs, new ArrayList<>());
    }

    @Test
    void teach_then_query_returns_expected_answer(@TempDir Path tmp) {
        PersistentHdcStore hdc = new PersistentHdcStore(tmp.resolve("a.ndjson"), 256);
        hdc.teach("smoke-1", "MATRIX mind");
        hdc.teach("smoke-2", "v16.0.0-mind release");

        var r1 = query(tmp.resolve("a.ndjson"), "MATRIX mind");
        assertThat(r1.matched()).isTrue();
        assertThat(r1.reply()).contains("MATRIX mind");

        var r2 = query(tmp.resolve("a.ndjson"), "v16.0.0-mind release");
        assertThat(r2.matched()).isTrue();
        assertThat(r2.reply()).contains("v16.0.0-mind release");
    }

    @Test
    void federation_round_trip_preserves_content(@TempDir Path tmp) {
        PersistentHdcStore nodeA = new PersistentHdcStore(tmp.resolve("shared.ndjson"), 256);
        nodeA.teach("taught-a", "shared secret");

        var r = query(tmp.resolve("shared.ndjson"), "shared secret");
        assertThat(r.matched()).isTrue();
        assertThat(r.reply()).contains("shared secret");
    }

    @Test
    void hdc_threshold_filter_drops_low_similarity(@TempDir Path tmp) {
        PersistentHdcStore store = new PersistentHdcStore(tmp.resolve("t.ndjson"), 256);
        store.teach("a", "alpha bravo charlie delta");
        store.teach("b", "completely different tokens");

        var r = query(tmp.resolve("t.ndjson"), "zz");
        // "zz" is 1 token, entries have many tokens → low overlap
        assertThat(r.matched()).isFalse();
    }

    @Test
    void hdc_threshold_returns_best_match(@TempDir Path tmp) {
        PersistentHdcStore store = new PersistentHdcStore(tmp.resolve("m.ndjson"), 256);
        store.teach("exact", "Paris is the capital of France");

        var r = query(tmp.resolve("m.ndjson"), "Paris is the capital of France");
        assertThat(r.matched()).isTrue();
        assertThat(r.reply()).contains("Paris");
        assertThat(r.confidence()).isEqualTo(1.0);
    }

    @Test
    void knowledge_exchange_protocol_serializes_round_trip(@TempDir Path tmp) {
        PersistentHdcStore store = new PersistentHdcStore(tmp.resolve("k.ndjson"), 256);
        store.teach("hello", "world");

        // snapshot -> batch -> reparse
        var batch = KnowledgeExchangeProtocol.snapshotToBatch(store, "node-A");
        String json = batch.toJsonArray();
        assertThat(json).contains("node-A");
        assertThat(json).contains("hello");
        assertThat(json).contains("world");

        var parsed = batch; // same batch
        int added = KnowledgeExchangeProtocol.mergeInto(store, parsed);
        // merged id has node-A prefix, won't match existing id
        assertThat(added).isEqualTo(batch.facts().size());
    }
}
