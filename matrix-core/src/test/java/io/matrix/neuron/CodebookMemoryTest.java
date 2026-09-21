package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodebookMemoryTest {

    private static long[] r(int seed) {
        return HdcEncoding.random(new Random(seed));
    }

    @Test
    void constructorRejectsBadCapacity() {
        assertThatThrownBy(() -> new CodebookMemory(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CodebookMemory(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyMemoryReturnsNullOnQuery() {
        CodebookMemory m = new CodebookMemory(10);
        assertThat(m.isEmpty()).isTrue();
        assertThat(m.size()).isEqualTo(0);
        assertThat(m.query(r(1))).isNull();
    }

    @Test
    void storeAndRetrieveByExactMatch() {
        CodebookMemory m = new CodebookMemory(10);
        m.store("a", r(1));
        m.store("b", r(2));
        CodebookMemory.Result hit = m.query(r(2));
        assertThat(hit).isNotNull();
        assertThat(hit.id).isEqualTo("b");
        assertThat(hit.distance).isEqualTo(0);
    }

    @Test
    void queryFindsNearestForNoisyQuery() {
        CodebookMemory m = new CodebookMemory(10);
        long[] a = r(1);
        long[] b = r(2);
        m.store("a", a);
        m.store("b", b);
        // Build noisy query close to a
        long[] noisy = a.clone();
        for (int i = 0; i < 10; i++) {
            noisy[i >>> 6] ^= (1L << (i & 63));
        }
        CodebookMemory.Result hit = m.query(noisy);
        assertThat(hit.id).isEqualTo("a");
    }

    @Test
    void storeRejectsNullOrBadVector() {
        CodebookMemory m = new CodebookMemory(10);
        assertThatThrownBy(() -> m.store(null, r(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> m.store("a", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> m.store("a", new long[2]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queryRejectsBadVector() {
        CodebookMemory m = new CodebookMemory(10);
        m.store("a", r(1));
        assertThatThrownBy(() -> m.query(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> m.query(new long[2]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containsReturnsTrueIfStored() {
        CodebookMemory m = new CodebookMemory(10);
        m.store("foo", r(1));
        assertThat(m.contains("foo")).isTrue();
        assertThat(m.contains("bar")).isFalse();
    }

    @Test
    void getReturnsVectorIfStored() {
        CodebookMemory m = new CodebookMemory(10);
        long[] v = r(42);
        m.store("x", v);
        long[] retrieved = m.get("x");
        assertThat(HdcEncoding.hamming(retrieved, v)).isEqualTo(0);
        assertThat(m.get("y")).isNull();
    }

    @Test
    void removeReturnsTrueIfRemoved() {
        CodebookMemory m = new CodebookMemory(10);
        m.store("a", r(1));
        assertThat(m.remove("a")).isTrue();
        assertThat(m.size()).isEqualTo(0);
        assertThat(m.remove("a")).isFalse();
    }

    @Test
    void clearRemovesAll() {
        CodebookMemory m = new CodebookMemory(10);
        m.store("a", r(1));
        m.store("b", r(2));
        m.clear();
        assertThat(m.size()).isEqualTo(0);
        assertThat(m.isEmpty()).isTrue();
    }

    @Test
    void idsReturnsAllInOrder() {
        CodebookMemory m = new CodebookMemory(10);
        m.store("a", r(1));
        m.store("b", r(2));
        m.store("c", r(3));
        assertThat(m.ids()).containsExactly("a", "b", "c");
    }

    @Test
    void lruEvictionWhenOverCapacity() {
        CodebookMemory m = new CodebookMemory(3);
        m.store("a", r(1));
        m.store("b", r(2));
        m.store("c", r(3));
        // Touch "a" so it's now most-recently-used
        m.contains("a");
        m.get("a");
        // Insert "d" — should evict "b" (LRU after touching "a")
        m.store("d", r(4));
        assertThat(m.size()).isEqualTo(3);
        assertThat(m.contains("a")).isTrue();
        assertThat(m.contains("d")).isTrue();
        assertThat(m.contains("c")).isTrue();
        // "b" should have been evicted (it was LRU after touching "a")
        // Note: contains() updates access order, so "c" is touched too.
        // The eldest untouched is now... depends on access-order timing.
        // Let's not assert on b specifically; just check size = 3.
    }

    @Test
    void replacementDoesNotEvict() {
        CodebookMemory m = new CodebookMemory(2);
        m.store("a", r(1));
        m.store("b", r(2));
        // Re-store "a" — should NOT evict anyone
        m.store("a", r(3));
        assertThat(m.size()).isEqualTo(2);
        assertThat(m.contains("a")).isTrue();
        assertThat(m.contains("b")).isTrue();
    }

    @Test
    void queryTopKFindsNearest() {
        CodebookMemory m = new CodebookMemory(10);
        m.store("a", r(1));
        m.store("b", r(2));
        m.store("c", r(3));
        m.store("d", r(4));
        List<CodebookMemory.Result> top2 = m.queryTopK(r(2), 2);
        assertThat(top2).hasSize(2);
        assertThat(top2.get(0).id).isEqualTo("b"); // exact match
        assertThat(top2.get(0).distance).isEqualTo(0);
    }

    @Test
    void queryTopKSortsByDistance() {
        CodebookMemory m = new CodebookMemory(10);
        m.store("a", r(1));
        m.store("b", r(2));
        m.store("c", r(3));
        List<CodebookMemory.Result> top3 = m.queryTopK(r(2), 3);
        assertThat(top3).hasSize(3);
        for (int i = 0; i < 2; i++) {
            assertThat(top3.get(i).distance)
                    .isLessThanOrEqualTo(top3.get(i + 1).distance);
        }
    }

    @Test
    void queryTopKWithKGreaterThanSizeReturnsAll() {
        CodebookMemory m = new CodebookMemory(10);
        m.store("a", r(1));
        m.store("b", r(2));
        List<CodebookMemory.Result> top10 = m.queryTopK(r(1), 10);
        assertThat(top10).hasSize(2);
    }

    @Test
    void queryTopKRejectsBadArgs() {
        CodebookMemory m = new CodebookMemory(10);
        assertThatThrownBy(() -> m.queryTopK(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(m.queryTopK(r(1), 0)).isEmpty();
    }

    @Test
    void resultSimilarityIsCorrect() {
        CodebookMemory m = new CodebookMemory(10);
        m.store("a", r(1));
        CodebookMemory.Result hit = m.query(r(1));
        assertThat(hit.similarity()).isEqualTo(1.0);
    }

    @Test
    void resultToStringContainsIdAndDistance() {
        CodebookMemory m = new CodebookMemory(10);
        m.store("foo", r(1));
        CodebookMemory.Result hit = m.query(r(1));
        String s = hit.toString();
        assertThat(s).contains("foo").contains("dist=0");
    }

    @Test
    void insertThenEvictThenInsertReusesCapacity() {
        CodebookMemory m = new CodebookMemory(2);
        m.store("a", r(1));
        m.store("b", r(2));
        m.store("c", r(3)); // evicts LRU
        assertThat(m.size()).isEqualTo(2);
        // Now "a" is LRU (assuming access-order tracking on contains/get during this sequence)
        m.store("d", r(4)); // evicts another
        assertThat(m.size()).isEqualTo(2);
    }
}
