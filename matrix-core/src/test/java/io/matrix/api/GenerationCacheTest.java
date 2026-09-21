package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUN 101 — GenerationCache unit tests. */
class GenerationCacheTest {

    @Test
    void emptyCacheMisses() {
        GenerationCache c = new GenerationCache(10);
        assertThat(c.get("hello")).isNull();
        assertThat(c.misses()).isEqualTo(1);
        assertThat(c.hits()).isZero();
    }

    @Test
    void putAndGet() {
        GenerationCache c = new GenerationCache(10);
        c.put("hello", "world");
        assertThat(c.get("hello")).isEqualTo("world");
        assertThat(c.hits()).isEqualTo(1);
        assertThat(c.size()).isEqualTo(1);
    }

    @Test
    void lruEviction() {
        GenerationCache c = new GenerationCache(2);
        c.put("a", "1");
        c.put("b", "2");
        c.put("c", "3");  // evicts "a"
        assertThat(c.get("a")).isNull();
        assertThat(c.get("b")).isEqualTo("2");
        assertThat(c.get("c")).isEqualTo("3");
        assertThat(c.evictions()).isEqualTo(1);
    }

    @Test
    void accessUpdatesLru() {
        GenerationCache c = new GenerationCache(2);
        c.put("a", "1");
        c.put("b", "2");
        c.get("a");  // promotes a
        c.put("c", "3");  // evicts b
        assertThat(c.get("a")).isEqualTo("1");
        assertThat(c.get("b")).isNull();
    }

    @Test
    void clearResets() {
        GenerationCache c = new GenerationCache(10);
        c.put("a", "1");
        c.put("b", "2");
        c.clear();
        assertThat(c.size()).isZero();
        assertThat(c.get("a")).isNull();
    }

    @Test
    void invalidCapacityRejected() {
        assertThatThrownBy(() -> new GenerationCache(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void putNullIsNoOp() {
        GenerationCache c = new GenerationCache(10);
        c.put(null, "v");
        c.put("k", null);
        assertThat(c.size()).isZero();
    }

    @Test
    void getOrComputeCachesResult() {
        GenerationCache c = new GenerationCache(10);
        int[] calls = {0};
        String r1 = c.getOrCompute("hello", p -> {
            calls[0]++;
            return "world";
        });
        String r2 = c.getOrCompute("hello", p -> {
            calls[0]++;
            return "different";  // shouldn't be called
        });
        assertThat(r1).isEqualTo("world");
        assertThat(r2).isEqualTo("world");
        assertThat(calls[0]).isEqualTo(1);
    }

    @Test
    void hitRateCalculation() {
        GenerationCache c = new GenerationCache(10);
        c.put("a", "1");
        c.get("a");  // hit
        c.get("a");  // hit
        c.get("b");  // miss
        assertThat(c.hits()).isEqualTo(2);
        assertThat(c.misses()).isEqualTo(1);
        assertThat(c.hitRate()).isCloseTo(2.0 / 3.0,
                org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void accessors() {
        GenerationCache c = new GenerationCache(50);
        assertThat(c.capacity()).isEqualTo(50);
        assertThat(c.size()).isZero();
    }
}
