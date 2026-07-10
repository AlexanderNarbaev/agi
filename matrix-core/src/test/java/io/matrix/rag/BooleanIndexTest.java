package io.matrix.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BooleanIndexTest {

    private BooleanIndex index;

    @BeforeEach
    void setup() {
        index = BooleanIndex.builder().dimensions(64).build();
    }

    // --- Construction ---

    @Test
    void shouldBuildWith64Dimensions() {
        var idx = BooleanIndex.builder().dimensions(64).build();
        assertThat(idx.dimensions()).isEqualTo(64);
        assertThat(idx.size()).isZero();
    }

    @Test
    void shouldBuildWith128Dimensions() {
        var idx = BooleanIndex.builder().dimensions(128).build();
        assertThat(idx.dimensions()).isEqualTo(128);
    }

    @Test
    void shouldRejectInvalidDimensions() {
        assertThatThrownBy(() -> BooleanIndex.builder().dimensions(33).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BooleanIndex.builder().dimensions(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Add / Remove ---

    @Test
    void shouldAddVector() {
        long[] vec = {0xFFL};
        index.add("doc1", vec);
        assertThat(index.size()).isEqualTo(1);
        assertThat(index.contains("doc1")).isTrue();
    }

    @Test
    void shouldAdd128BitVector() {
        var idx128 = BooleanIndex.builder().dimensions(128).build();
        long[] vec = {0xAAAAAAAAAAAAAAAAL, 0x5555555555555555L};
        idx128.add("doc128", vec);
        assertThat(idx128.size()).isEqualTo(1);
    }

    @Test
    void shouldOverwriteExistingId() {
        index.add("doc1", new long[]{0xFFL});
        index.add("doc1", new long[]{0x0FL});
        assertThat(index.size()).isEqualTo(1);
        assertThat(index.get("doc1")).isEqualTo(new long[]{0x0FL});
    }

    @Test
    void shouldRemoveVector() {
        index.add("doc1", new long[]{0xFFL});
        boolean removed = index.remove("doc1");
        assertThat(removed).isTrue();
        assertThat(index.size()).isZero();
        assertThat(index.contains("doc1")).isFalse();
    }

    @Test
    void shouldReturnFalseWhenRemovingNonexistent() {
        assertThat(index.remove("missing")).isFalse();
    }

    @Test
    void shouldRejectNullId() {
        assertThatThrownBy(() -> index.add(null, new long[]{0L}))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullVector() {
        assertThatThrownBy(() -> index.add("doc1", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectWrongDimensionVector() {
        assertThatThrownBy(() -> index.add("doc1", new long[]{0L, 0L}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Hamming Distance ---

    @Test
    void shouldComputeZeroDistanceForIdenticalVectors() {
        long[] vec = {0xABCDEF1234567890L};
        index.add("a", vec);
        index.add("b", vec);

        var results = index.search(vec, 10);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).distance()).isZero();
        assertThat(results.get(1).distance()).isZero();
    }

    @Test
    void shouldComputeMaxDistanceForComplementVectors() {
        long[] vecA = {0xFFFFFFFFFFFFFFFFL};
        long[] vecB = {0x0000000000000000L};
        index.add("allOnes", vecA);
        index.add("allZeros", vecB);

        var results = index.search(vecA, 10);
        assertThat(results).hasSize(2);
        // allOnes to itself = 0, to allZeros = 64
        assertThat(results.get(0).distance()).isZero();
        assertThat(results.get(1).distance()).isEqualTo(64);
    }

    @Test
    void shouldComputeCorrectHammingDistance() {
        long[] vecA = {0b10101010L};
        long[] vecB = {0b11110000L};
        index.add("a", vecA);
        index.add("b", vecB);

        // XOR = 01011010 => popcount = 4
        var results = index.search(vecA, 10);
        assertThat(results.get(1).distance()).isEqualTo(4);
    }

    @Test
    void shouldCompute128BitHammingDistance() {
        var idx128 = BooleanIndex.builder().dimensions(128).build();
        long[] vecA = {0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL};
        long[] vecB = {0x0000000000000000L, 0x0000000000000000L};
        idx128.add("a", vecA);
        idx128.add("b", vecB);

        var results = idx128.search(vecA, 10);
        assertThat(results.get(1).distance()).isEqualTo(128);
    }

    // --- Top-K Search ---

    @Test
    void shouldReturnTopKResults() {
        long[] query = {0x00L};
        index.add("close", new long[]{0x01L});  // dist=1
        index.add("mid", new long[]{0x0FL});     // dist=4
        index.add("far", new long[]{0xFFL});     // dist=8

        var results = index.search(query, 2);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo("close");
        assertThat(results.get(0).distance()).isEqualTo(1);
        assertThat(results.get(1).id()).isEqualTo("mid");
        assertThat(results.get(1).distance()).isEqualTo(4);
    }

    @Test
    void shouldReturnAllWhenKExceedsSize() {
        index.add("a", new long[]{0x01L});
        index.add("b", new long[]{0x02L});

        var results = index.search(new long[]{0x00L}, 100);
        assertThat(results).hasSize(2);
    }

    @Test
    void shouldReturnEmptyWhenIndexIsEmpty() {
        var results = index.search(new long[]{0xFFL}, 5);
        assertThat(results).isEmpty();
    }

    @Test
    void shouldReturnResultsSortedByDistance() {
        index.add("far2", new long[]{0xFFL});
        index.add("close", new long[]{0x01L});
        index.add("far1", new long[]{0xF0L});

        var results = index.search(new long[]{0x00L}, 10);
        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i).distance())
                    .isGreaterThanOrEqualTo(results.get(i - 1).distance());
        }
    }

    // --- Persistence ---

    @Test
    void shouldSerializeAndDeserialize() throws Exception {
        index.add("doc1", new long[]{0xABCDEF1234567890L});
        index.add("doc2", new long[]{0x1234567890ABCDEL});

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        index.serialize(out);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        var restored = BooleanIndex.deserialize(in);

        assertThat(restored.size()).isEqualTo(2);
        assertThat(restored.dimensions()).isEqualTo(64);
        assertThat(restored.get("doc1")).isEqualTo(new long[]{0xABCDEF1234567890L});
        assertThat(restored.get("doc2")).isEqualTo(new long[]{0x1234567890ABCDEL});
    }

    @Test
    void shouldSerialize128BitIndex() throws Exception {
        var idx128 = BooleanIndex.builder().dimensions(128).build();
        long[] vec = {0xAAAAL, 0xBBBBL};
        idx128.add("wide", vec);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        idx128.serialize(out);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        var restored = BooleanIndex.deserialize(in);

        assertThat(restored.dimensions()).isEqualTo(128);
        assertThat(restored.get("wide")).isEqualTo(vec);
    }

    @Test
    void shouldPreserveSearchAfterDeserialization() throws Exception {
        index.add("close", new long[]{0x01L});
        index.add("far", new long[]{0xFFL});

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        index.serialize(out);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        var restored = BooleanIndex.deserialize(in);

        var results = restored.search(new long[]{0x00L}, 1);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("close");
    }

    // --- Thread Safety ---

    @Test
    void shouldHandleConcurrentAdds() throws Exception {
        int threadCount = 8;
        int opsPerThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        String id = "t" + threadId + "_v" + i;
                        index.add(id, new long[]{(long) i});
                    }
                } catch (Exception e) {
                    failed.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
        assertThat(failed.get()).isFalse();
        assertThat(index.size()).isEqualTo(threadCount * opsPerThread);
    }

    @Test
    void shouldHandleConcurrentReadsAndWrites() throws Exception {
        index.add("seed", new long[]{0xAAAAAAAAAAAAAAAAL});

        int threadCount = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);

        // Writers
        for (int t = 0; t < threadCount / 2; t++) {
            final int id = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 200; i++) {
                        index.add("w" + id + "_" + i, new long[]{(long) i});
                    }
                } catch (Exception e) {
                    failed.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Readers
        for (int t = 0; t < threadCount / 2; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 200; i++) {
                        index.search(new long[]{0xAAAAAAAAAAAAAAAAL}, 5);
                    }
                } catch (Exception e) {
                    failed.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
        assertThat(failed.get()).isFalse();
    }

    // --- Edge Cases ---

    @Test
    void shouldGetNullForMissingId() {
        assertThat(index.get("missing")).isNull();
    }

    @Test
    void shouldListAllIds() {
        index.add("a", new long[]{1L});
        index.add("b", new long[]{2L});
        index.add("c", new long[]{3L});

        List<String> ids = index.ids();
        assertThat(ids).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void shouldClearIndex() {
        index.add("a", new long[]{1L});
        index.add("b", new long[]{2L});
        index.clear();

        assertThat(index.size()).isZero();
        assertThat(index.ids()).isEmpty();
    }
}
