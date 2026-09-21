package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ProfileKVCacheTest {

    @Test
    void emptyCacheHasZeroSize() {
        ProfileKVCache cache = new ProfileKVCache(4, 16, 1L);
        assertThat(cache.size()).isEqualTo(0);
        assertThat(cache.pageCount()).isEqualTo(0);
    }

    @Test
    void appendIncreasesSize() {
        ProfileKVCache cache = new ProfileKVCache(4, 16, 1L);
        cache.append(makeProfile(0.1));
        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.pageCount()).isEqualTo(1);
    }

    @Test
    void pageSizeRespected() {
        ProfileKVCache cache = new ProfileKVCache(2, 16, 1L);
        cache.append(makeProfile(0.1));
        cache.append(makeProfile(0.2));
        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.pageCount()).isEqualTo(1);
        cache.append(makeProfile(0.3));
        assertThat(cache.pageCount()).isEqualTo(2);
    }

    @Test
    void lruEviction() {
        ProfileKVCache cache = new ProfileKVCache(1, 2, 1L);
        // Page 1 (idx 0)
        cache.append(makeProfile(0.1));
        // Page 2 (idx 1)
        cache.append(makeProfile(0.2));
        // Force eviction
        cache.append(makeProfile(0.3));
        // Should evict page 1 (LRU), keep page 2 and new page
        assertThat(cache.pageCount()).isEqualTo(2);
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void invalidPageSizeThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new ProfileKVCache(0, 16, 1L)
        );
    }

    @Test
    void invalidNumPagesThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new ProfileKVCache(4, 0, 1L)
        );
    }

    @Test
    void nullAppendIsNoOp() {
        ProfileKVCache cache = new ProfileKVCache(4, 16, 1L);
        cache.append(null);
        assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    void allReturnsInInsertionOrder() {
        ProfileKVCache cache = new ProfileKVCache(4, 16, 1L);
        for (int i = 0; i < 10; i++) cache.append(makeProfile(i / 10.0));
        List<CognitiveGenesisProfile> all = cache.all();
        assertThat(all.size()).isEqualTo(10);
        for (int i = 0; i < 10; i++) {
            assertThat(all.get(i).phiBinary()).isEqualTo(i / 10.0);
        }
    }

    @Test
    void getByIndex() {
        ProfileKVCache cache = new ProfileKVCache(2, 16, 1L);
        for (int i = 0; i < 5; i++) cache.append(makeProfile(i));
        assertThat(cache.get(0).phiBinary()).isEqualTo(0.0);
        assertThat(cache.get(3).phiBinary()).isEqualTo(3.0);
        assertThat(cache.get(100)).isNull();
    }

    @Test
    void capacityComputed() {
        ProfileKVCache cache = new ProfileKVCache(4, 8, 1L);
        assertThat(cache.capacity()).isEqualTo(32);
    }

    @Test
    void utilizationAfterAppend() {
        ProfileKVCache cache = new ProfileKVCache(2, 4, 1L);
        cache.append(makeProfile(0.1));
        cache.append(makeProfile(0.2));
        // 2 profiles in 1 page (capacity 2), utilization = 2/2 = 1.0
        assertThat(cache.utilization()).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void seedRecorded() {
        ProfileKVCache cache = new ProfileKVCache(4, 16, 42L);
        assertThat(cache.seed()).isEqualTo(42L);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
