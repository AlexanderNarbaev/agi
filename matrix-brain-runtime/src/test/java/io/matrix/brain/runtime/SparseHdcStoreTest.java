package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.BitSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRUE-W11 — Sparse-HDC store tests.
 */
class SparseHdcStoreTest {

    @Test
    void teach_then_retrieve_finds_same_content(@TempDir Path tmp) {
        SparseHdcStore store = new SparseHdcStore(tmp.resolve("sparse.ndjson"), 256, 32);
        store.teach("e1", "Paris is the capital of France");
        store.teach("e2", "Berlin is the capital of Germany");
        store.teach("e3", "gravity is 9.8 m/s^2");

        BitSet query = store.encode("capital of France");
        SparseHdcStore.Entry best = store.findBest(query, 0.30);
        assertThat(best).isNotNull();
        assertThat(best.id()).isEqualTo("e1");
    }

    @Test
    void cosine_identical_is_one() {
        SparseHdcStore store = new SparseHdcStore(Paths.get("/tmp"), 64, 8);
        BitSet a = new BitSet(64);
        a.set(1); a.set(5); a.set(10);
        assertThat(SparseHdcStore.cosine(a, a)).isEqualTo(1.0);
    }

    @Test
    void cosine_disjoint_is_zero() {
        SparseHdcStore store = new SparseHdcStore(Paths.get("/tmp"), 64, 8);
        BitSet a = new BitSet(64); a.set(0, 4);
        BitSet b = new BitSet(64); b.set(4, 8);
        assertThat(SparseHdcStore.cosine(a, b)).isEqualTo(0.0);
    }

    @Test
    void empty_store_returns_null_findBest() {
        SparseHdcStore store = new SparseHdcStore(Paths.get("/tmp"), 64, 8);
        BitSet query = new BitSet(64);
        assertThat(store.findBest(query, 0.30)).isNull();
    }

    @Test
    void persist_and_reload_round_trip(@TempDir Path tmp) {
        Path p = tmp.resolve("round.ndjson");
        SparseHdcStore s1 = new SparseHdcStore(p, 256, 32);
        s1.teach("k1", "hello");
        s1.teach("k2", "world");
        s1.teach("k3", "test");
        assertThat(s1.size()).isEqualTo(3);

        SparseHdcStore s2 = new SparseHdcStore(p, 256, 32);
        assertThat(s2.size()).isEqualTo(3);
        for (Map.Entry<String, SparseHdcStore.Entry> e : s2.snapshot().entrySet()) {
            assertThat(e.getValue().content()).isIn("hello", "world", "test");
        }
    }

    @Test
    void encode_sparse_uses_at_most_k_bits_per_token() {
        SparseHdcStore store = new SparseHdcStore(Paths.get("/tmp"), 256, 8);
        BitSet bits = store.encode("hello world test one two three");
        // At most 6 tokens × 8 bits = 48 bits (correct)
        assertThat(bits.cardinality()).isLessThanOrEqualTo(48);
    }
}
