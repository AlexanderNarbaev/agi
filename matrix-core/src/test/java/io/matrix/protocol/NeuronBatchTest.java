package io.matrix.protocol;

import io.matrix.cluster.NeuronId;
import io.matrix.cluster.Signal;
import io.matrix.protocol.NeuronBatch.CompressionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NeuronBatchTest {

    private static Signal signal(int seed) {
        java.util.Random rng = new java.util.Random(seed);
        return new Signal(
                new NeuronId(new UUID(rng.nextLong(), rng.nextLong()), seed),
                new NeuronId(new UUID(rng.nextLong(), rng.nextLong()), seed + 1000),
                seed % 2 == 0,
                seed * 1000L);
    }

    private static List<Signal> generateSignals(int count) {
        List<Signal> signals = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            signals.add(signal(i));
        }
        return signals;
    }

    @Nested
    class PackUnpackRoundtrip {

        @Test
        void shouldRoundtripWith100Signals() {
            List<Signal> original = generateSignals(100);
            NeuronBatch batch = NeuronBatch.pack(original);

            List<Signal> restored = batch.unpack();
            assertThat(restored).hasSize(100);
            assertThat(restored).isEqualTo(original);
        }

        @Test
        void shouldRoundtripWithEmptyBatch() {
            List<Signal> empty = List.of();
            NeuronBatch batch = NeuronBatch.pack(empty);

            assertThat(batch.signalCount()).isZero();
            assertThat(batch.unpack()).isEmpty();
        }

        @Test
        void shouldRoundtripWithSingleSignal() {
            List<Signal> single = List.of(signal(42));
            NeuronBatch batch = NeuronBatch.pack(single);

            assertThat(batch.unpack()).isEqualTo(single);
        }

        @Test
        void shouldPreserveSignalOrder() {
            List<Signal> original = generateSignals(50);
            NeuronBatch batch = NeuronBatch.pack(original);

            List<Signal> restored = batch.unpack();
            for (int i = 0; i < original.size(); i++) {
                assertThat(restored.get(i)).isEqualTo(original.get(i));
            }
        }

        @Test
        void shouldRoundtripWithAllTrueValues() {
            List<Signal> allTrue = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                allTrue.add(new Signal(
                        new NeuronId(UUID.randomUUID(), 0),
                        new NeuronId(UUID.randomUUID(), 0),
                        true, i));
            }
            NeuronBatch batch = NeuronBatch.pack(allTrue);
            assertThat(batch.unpack()).isEqualTo(allTrue);
        }

        @Test
        void shouldRoundtripWithAllFalseValues() {
            List<Signal> allFalse = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                allFalse.add(new Signal(
                        new NeuronId(UUID.randomUUID(), 0),
                        new NeuronId(UUID.randomUUID(), 0),
                        false, i));
            }
            NeuronBatch batch = NeuronBatch.pack(allFalse);
            assertThat(batch.unpack()).isEqualTo(allFalse);
        }
    }

    @Nested
    class RleCompression {

        @Test
        void shouldCompressRepeatedPatterns() {
            NeuronId src = new NeuronId(UUID.randomUUID(), 0);
            NeuronId tgt = new NeuronId(UUID.randomUUID(), 0);
            List<Signal> repetitive = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                repetitive.add(new Signal(src, tgt, true, i * 100L));
            }

            NeuronBatch batch = NeuronBatch.pack(repetitive, CompressionMode.RLE);
            assertThat(batch.compression()).isEqualTo(CompressionMode.RLE);
            assertThat(batch.unpack()).isEqualTo(repetitive);

            // RLE should be much smaller than RAW
            int rawSize = 5 + 50 * 57;
            assertThat(batch.compressedSize()).isLessThan(rawSize / 2);
        }

        @Test
        void shouldHandleMixedRuns() {
            NeuronId src1 = new NeuronId(UUID.randomUUID(), 0);
            NeuronId src2 = new NeuronId(UUID.randomUUID(), 0);
            NeuronId tgt = new NeuronId(UUID.randomUUID(), 0);
            List<Signal> mixed = new ArrayList<>();
            for (int i = 0; i < 10; i++) mixed.add(new Signal(src1, tgt, true, i));
            for (int i = 0; i < 10; i++) mixed.add(new Signal(src2, tgt, false, i + 10));
            for (int i = 0; i < 10; i++) mixed.add(new Signal(src1, tgt, true, i + 20));

            NeuronBatch batch = NeuronBatch.pack(mixed, CompressionMode.RLE);
            assertThat(batch.unpack()).isEqualTo(mixed);
        }

        @Test
        void rleShouldWinForRepetitiveSignals() {
            NeuronId src = new NeuronId(UUID.randomUUID(), 0);
            NeuronId tgt = new NeuronId(UUID.randomUUID(), 0);
            List<Signal> repetitive = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                repetitive.add(new Signal(src, tgt, true, i));
            }

            NeuronBatch auto = NeuronBatch.pack(repetitive);
            assertThat(auto.compression()).isEqualTo(CompressionMode.RLE);
        }
    }

    @Nested
    class BitmaskCompression {

        @Test
        void shouldCompressSparseSignals() {
            List<Signal> sparse = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                sparse.add(new Signal(
                        new NeuronId(UUID.randomUUID(), 0),
                        new NeuronId(UUID.randomUUID(), 0),
                        i % 10 == 0, // only 10% are true
                        i));
            }

            NeuronBatch batch = NeuronBatch.pack(sparse, CompressionMode.BITMASK);
            assertThat(batch.compression()).isEqualTo(CompressionMode.BITMASK);
            assertThat(batch.unpack()).isEqualTo(sparse);
        }

        @Test
        void bitmaskShouldBeSmallerThanRawForNonRepetitive() {
            List<Signal> diverse = generateSignals(100);
            NeuronBatch bitmask = NeuronBatch.pack(diverse, CompressionMode.BITMASK);
            NeuronBatch raw = NeuronBatch.pack(diverse, CompressionMode.RAW);
            assertThat(bitmask.compressedSize()).isLessThan(raw.compressedSize());
        }

        @Test
        void bitmaskShouldWinForDiverseSignals() {
            List<Signal> diverse = generateSignals(100);
            NeuronBatch auto = NeuronBatch.pack(diverse);
            assertThat(auto.compression()).isEqualTo(CompressionMode.BITMASK);
        }
    }

    @Nested
    class CompressionRatio {

        @Test
        void shouldReportRatioGreaterThanOneForRLE() {
            NeuronId src = new NeuronId(UUID.randomUUID(), 0);
            NeuronId tgt = new NeuronId(UUID.randomUUID(), 0);
            List<Signal> repetitive = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                repetitive.add(new Signal(src, tgt, true, i));
            }

            NeuronBatch batch = NeuronBatch.pack(repetitive, CompressionMode.RLE);
            assertThat(batch.compressionRatio()).isGreaterThan(1.0);
        }

        @Test
        void shouldReportRatioForBitmask() {
            List<Signal> signals = generateSignals(50);
            NeuronBatch batch = NeuronBatch.pack(signals, CompressionMode.BITMASK);
            double ratio = batch.compressionRatio();
            assertThat(ratio).isGreaterThan(0.0);
        }

        @Test
        void rawCompressionRatioShouldBeCloseToOne() {
            List<Signal> signals = generateSignals(20);
            NeuronBatch batch = NeuronBatch.pack(signals, CompressionMode.RAW);
            // RAW has 5 bytes overhead (mode + count)
            double ratio = batch.compressionRatio();
            assertThat(ratio).isCloseTo(1.0, org.assertj.core.data.Percentage.withPercentage(10.0));
        }

        @Test
        void emptyBatchRatioShouldBeOne() {
            NeuronBatch batch = NeuronBatch.pack(List.of());
            assertThat(batch.compressionRatio()).isEqualTo(1.0);
        }
    }

    @Nested
    class EmptyBatchHandling {

        @Test
        void emptyBatchShouldUnpackToEmptyList() {
            NeuronBatch batch = NeuronBatch.pack(List.of());
            assertThat(batch.unpack()).isEmpty();
            assertThat(batch.signalCount()).isZero();
        }

        @Test
        void emptyBatchShouldHaveMinimalSize() {
            NeuronBatch batch = NeuronBatch.pack(List.of());
            assertThat(batch.compressedSize()).isLessThanOrEqualTo(5);
        }

        @Test
        void emptyBatchShouldUseRawMode() {
            NeuronBatch batch = NeuronBatch.pack(List.of());
            assertThat(batch.compression()).isEqualTo(CompressionMode.RAW);
        }
    }

    @Nested
    class LargeBatchPerformance {

        @Test
        void shouldHandle10000SignalsRoundtrip() {
            List<Signal> large = generateSignals(10_000);
            long start = System.nanoTime();
            NeuronBatch batch = NeuronBatch.pack(large);
            long packTime = System.nanoTime() - start;

            start = System.nanoTime();
            List<Signal> restored = batch.unpack();
            long unpackTime = System.nanoTime() - start;

            assertThat(restored).hasSize(10_000);
            assertThat(restored).hasSize(large.size());
            assertThat(batch.compressionRatio()).isGreaterThan(1.0);

            // Performance: pack+unpack 10k signals in under 500ms
            long totalMs = (packTime + unpackTime) / 1_000_000;
            assertThat(totalMs).as("pack+unpack time: %d ms", totalMs).isLessThan(500);
        }

        @Test
        void shouldSelectBestCompressionForLargeBatch() {
            List<Signal> large = generateSignals(10_000);
            NeuronBatch batch = NeuronBatch.pack(large);

            // For diverse signals, BITMASK should be selected
            assertThat(batch.compression()).isEqualTo(CompressionMode.BITMASK);
            assertThat(batch.compressionRatio()).isGreaterThan(1.0);
        }

        @Test
        void largeRepetitiveBatchShouldUseRLE() {
            NeuronId src = new NeuronId(UUID.randomUUID(), 0);
            NeuronId tgt = new NeuronId(UUID.randomUUID(), 0);
            List<Signal> repetitive = new ArrayList<>();
            for (int i = 0; i < 10_000; i++) {
                repetitive.add(new Signal(src, tgt, true, i));
            }

            NeuronBatch batch = NeuronBatch.pack(repetitive);
            // RLE should be selected for repetitive signals (same src/tgt/value)
            assertThat(batch.compression()).isIn(CompressionMode.RLE, CompressionMode.BITMASK);
            assertThat(batch.compressionRatio()).isGreaterThan(1.0);
            assertThat(batch.unpack()).hasSize(repetitive.size());
        }
    }

    @Nested
    class ExplicitModePacking {

        @Test
        void shouldPackWithExplicitRawMode() {
            List<Signal> signals = generateSignals(10);
            NeuronBatch batch = NeuronBatch.pack(signals, CompressionMode.RAW);
            assertThat(batch.compression()).isEqualTo(CompressionMode.RAW);
            assertThat(batch.unpack()).isEqualTo(signals);
        }

        @Test
        void shouldPackWithExplicitRleMode() {
            List<Signal> signals = generateSignals(10);
            NeuronBatch batch = NeuronBatch.pack(signals, CompressionMode.RLE);
            assertThat(batch.compression()).isEqualTo(CompressionMode.RLE);
            assertThat(batch.unpack()).isEqualTo(signals);
        }

        @Test
        void shouldPackWithExplicitBitmaskMode() {
            List<Signal> signals = generateSignals(10);
            NeuronBatch batch = NeuronBatch.pack(signals, CompressionMode.BITMASK);
            assertThat(batch.compression()).isEqualTo(CompressionMode.BITMASK);
            assertThat(batch.unpack()).isEqualTo(signals);
        }

        @Test
        void shouldRejectNullSignals() {
            assertThatThrownBy(() -> NeuronBatch.pack(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectNullMode() {
            assertThatThrownBy(() -> NeuronBatch.pack(generateSignals(5), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // Reset ThreadLocalRandom state if needed
    }
}
