package io.matrix.compression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.BitSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BooleanCompressorTest {

    // ─── RLE compression tests ───

    @Test
    void bitmaskCompressSparseVectorShouldAchieveHighCompression() {
        // 5% density: 50 ones out of 1000 bits
        BitSet sparse = new BitSet(1000);
        Random rng = new Random(42);
        for (int i = 0; i < 50; i++) {
            sparse.set(rng.nextInt(1000));
        }
        BooleanCompressor.Compressed result = BooleanCompressor.compress(sparse, 1000);
        assertThat(result.method()).isEqualTo(BooleanCompressor.Method.BITMASK);
        double ratio = result.compressionRatio();
        assertThat(ratio).isGreaterThan(2.0);
    }

    @Test
    void bitmaskCompressAllZerosShouldAchieveMaxCompression() {
        BitSet zeros = new BitSet(8192);
        BooleanCompressor.Compressed result = BooleanCompressor.compress(zeros, 8192);
        assertThat(result.method()).isEqualTo(BooleanCompressor.Method.BITMASK);
        assertThat(result.compressedSize()).isLessThan(16); // header + single count=0
    }

    @Test
    void rleCompressAllOnesShouldAchieveMaxCompression() {
        BitSet ones = new BitSet(8192);
        ones.set(0, 8192);
        BooleanCompressor.Compressed result = BooleanCompressor.compress(ones, 8192);
        assertThat(result.method()).isEqualTo(BooleanCompressor.Method.RLE);
        assertThat(result.compressedSize()).isLessThan(16);
    }

    @Test
    void rleRoundtripShouldPreserveData() {
        BitSet original = new BitSet(2048);
        Random rng = new Random(123);
        for (int i = 0; i < 100; i++) {
            original.set(rng.nextInt(2048));
        }
        BooleanCompressor.Compressed compressed = BooleanCompressor.compress(original, 2048);
        BitSet restored = BooleanCompressor.decompress(compressed, 2048);
        assertThat(restored).isEqualTo(original);
    }

    // ─── Bitmask compression tests ───

    @Test
    void rleCompressDenseVectorShouldUseRle() {
        // 90% density → RLE (dense vectors have long runs)
        BitSet dense = new BitSet(1024);
        dense.set(0, 1024);
        // clear 10% randomly
        Random rng = new Random(42);
        for (int i = 0; i < 102; i++) {
            dense.clear(rng.nextInt(1024));
        }
        BooleanCompressor.Compressed result = BooleanCompressor.compress(dense, 1024);
        assertThat(result.method()).isEqualTo(BooleanCompressor.Method.RLE);
    }

    @Test
    void denseRoundtripShouldPreserveData() {
        BitSet original = new BitSet(512);
        original.set(0, 512);
        Random rng = new Random(99);
        for (int i = 0; i < 50; i++) {
            original.clear(rng.nextInt(512));
        }
        BooleanCompressor.Compressed compressed = BooleanCompressor.compress(original, 512);
        BitSet restored = BooleanCompressor.decompress(compressed, 512);
        assertThat(restored).isEqualTo(original);
    }

    // ─── Auto-select tests ───

    @Test
    void autoSelectShouldPickBitmaskForSparseVectors() {
        // <10% density → bitmask (position encoding achieves >2x compression)
        BitSet sparse = new BitSet(4096);
        Random rng = new Random(77);
        for (int i = 0; i < 200; i++) { // ~5% density
            sparse.set(rng.nextInt(4096));
        }
        BooleanCompressor.Compressed result = BooleanCompressor.compress(sparse, 4096);
        assertThat(result.method()).isEqualTo(BooleanCompressor.Method.BITMASK);
        assertThat(result.compressionRatio()).isGreaterThan(2.0);
    }

    @Test
    void autoSelectShouldPickRleForDenseVectors() {
        // >50% density → RLE (long runs of 1s are efficient)
        BitSet dense = new BitSet(2048);
        dense.set(0, 2048);
        Random rng = new Random(55);
        for (int i = 0; i < 200; i++) {
            dense.clear(rng.nextInt(2048));
        }
        BooleanCompressor.Compressed result = BooleanCompressor.compress(dense, 2048);
        assertThat(result.method()).isEqualTo(BooleanCompressor.Method.RLE);
    }

    @Test
    void roundtripForVariousSizesShouldPreserveData() {
        int[] sizes = {64, 256, 1024, 4096, 16384};
        for (int size : sizes) {
            BitSet original = new BitSet(size);
            Random rng = new Random(size);
            for (int i = 0; i < size / 10; i++) {
                original.set(rng.nextInt(size));
            }
            BooleanCompressor.Compressed compressed = BooleanCompressor.compress(original, size);
            BitSet restored = BooleanCompressor.decompress(compressed, size);
            assertThat(restored)
                    .as("roundtrip for size=%d", size)
                    .isEqualTo(original);
        }
    }

    // ─── Edge cases ───

    @Test
    void emptyBitSetShouldCompress() {
        BitSet empty = new BitSet();
        BooleanCompressor.Compressed result = BooleanCompressor.compress(empty, 0);
        assertThat(result.compressedSize()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void singleBitShouldCompress() {
        BitSet single = new BitSet(1);
        single.set(0);
        BooleanCompressor.Compressed result = BooleanCompressor.compress(single, 1);
        BitSet restored = BooleanCompressor.decompress(result, 1);
        assertThat(restored.get(0)).isTrue();
    }

    @Test
    void compressionRatioShouldBePositive() {
        BitSet bs = new BitSet(1024);
        bs.set(0, 512); // 50% density
        BooleanCompressor.Compressed result = BooleanCompressor.compress(bs, 1024);
        assertThat(result.compressionRatio()).isGreaterThan(0);
    }

    @Test
    void negativeLengthShouldThrow() {
        BitSet bs = new BitSet(10);
        assertThatThrownBy(() -> BooleanCompressor.compress(bs, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
