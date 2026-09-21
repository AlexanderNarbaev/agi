package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUN 115 — GenerationHistory unit tests. */
class GenerationHistoryTest {

    @Test
    void emptyHistory() {
        GenerationHistory h = new GenerationHistory(10);
        assertThat(h.get("u")).isEmpty();
        assertThat(h.keyCount()).isZero();
        assertThat(h.totalEntries()).isZero();
    }

    @Test
    void recordAndGet() {
        GenerationHistory h = new GenerationHistory(10);
        h.record("u1", "Hi", "Hello!", 50);
        h.record("u1", "Bye", "Goodbye!", 30);
        var entries = h.get("u1");
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).prompt()).isEqualTo("Hi");
        assertThat(entries.get(1).response()).isEqualTo("Goodbye!");
        assertThat(h.totalEntries()).isEqualTo(2);
    }

    @Test
    void evictsOldest() {
        GenerationHistory h = new GenerationHistory(3);
        for (int i = 0; i < 5; i++) {
            h.record("u", "p" + i, "r" + i, 10);
        }
        var entries = h.get("u");
        assertThat(entries).hasSize(3);
        // Oldest two should have been evicted
        assertThat(entries.get(0).prompt()).isEqualTo("p2");
        assertThat(entries.get(2).prompt()).isEqualTo("p4");
        assertThat(h.totalEvictions()).isEqualTo(2);
    }

    @Test
    void differentKeysTrackedSeparately() {
        GenerationHistory h = new GenerationHistory(10);
        h.record("alice", "x", "y", 5);
        h.record("bob", "x", "y", 5);
        assertThat(h.keyCount()).isEqualTo(2);
    }

    @Test
    void clearKey() {
        GenerationHistory h = new GenerationHistory(10);
        h.record("u1", "x", "y", 5);
        h.record("u2", "x", "y", 5);
        h.clear("u1");
        assertThat(h.get("u1")).isEmpty();
        assertThat(h.get("u2")).hasSize(1);
        assertThat(h.keyCount()).isEqualTo(1);
    }

    @Test
    void clearAll() {
        GenerationHistory h = new GenerationHistory(10);
        h.record("u1", "x", "y", 5);
        h.record("u2", "x", "y", 5);
        h.clearAll();
        assertThat(h.keyCount()).isZero();
        assertThat(h.totalEntries()).isZero();
    }

    @Test
    void invalidCapacityRejected() {
        assertThatThrownBy(() -> new GenerationHistory(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenerationHistory(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullKeyIsNoOp() {
        GenerationHistory h = new GenerationHistory(10);
        h.record(null, "x", "y", 5);
        assertThat(h.totalEntries()).isZero();
    }

    @Test
    void entryRecord() {
        GenerationHistory.Entry e = new GenerationHistory.Entry("p", "r", 100, 1234L);
        assertThat(e.prompt()).isEqualTo("p");
        assertThat(e.response()).isEqualTo("r");
        assertThat(e.latencyMs()).isEqualTo(100);
        assertThat(e.timestampMs()).isEqualTo(1234L);
    }

    @Test
    void accessor() {
        GenerationHistory h = new GenerationHistory(50);
        assertThat(h.maxPerKey()).isEqualTo(50);
    }
}
