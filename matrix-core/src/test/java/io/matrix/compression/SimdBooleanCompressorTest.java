package io.matrix.compression;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SimdBooleanCompressorTest {

    // ─── Roundtrip tests ───

    @Test
    void roundtripPackUnpackShouldPreserveAllBits() {
        Random rng = new Random(42);
        for (int size : new int[]{1, 8, 63, 64, 65, 127, 128, 129, 255, 256, 257, 511, 512, 1024}) {
            boolean[] original = new boolean[size];
            for (int i = 0; i < size; i++) {
                original[i] = rng.nextBoolean();
            }

            long[] packed = SimdBooleanCompressor.packSIMD(original);
            boolean[] restored = SimdBooleanCompressor.unpackSIMD(packed, size);

            assertThat(restored)
                    .as("roundtrip for size=%d", size)
                    .isEqualTo(original);
        }
    }

    @Test
    void roundtripLargeArrayShouldPreserveAllBits() {
        int size = 1024;
        boolean[] original = new boolean[size];
        Random rng = new Random(123);
        for (int i = 0; i < size; i++) {
            original[i] = rng.nextBoolean();
        }

        long[] packed = SimdBooleanCompressor.packSIMD(original);
        boolean[] restored = SimdBooleanCompressor.unpackSIMD(packed, size);

        assertThat(restored).isEqualTo(original);
    }

    // ─── Edge cases ───

    @Test
    void emptyArrayShouldReturnEmptyPacked() {
        long[] packed = SimdBooleanCompressor.packSIMD(new boolean[0]);
        assertThat(packed).isEmpty();

        boolean[] unpacked = SimdBooleanCompressor.unpackSIMD(packed, 0);
        assertThat(unpacked).isEmpty();
    }

    @Test
    void singleBitTrueShouldRoundtrip() {
        boolean[] original = {true};
        long[] packed = SimdBooleanCompressor.packSIMD(original);
        assertThat(packed).hasSize(1);
        assertThat(packed[0] & 1L).isEqualTo(1L);

        boolean[] restored = SimdBooleanCompressor.unpackSIMD(packed, 1);
        assertThat(restored).containsExactly(true);
    }

    @Test
    void singleBitFalseShouldRoundtrip() {
        boolean[] original = {false};
        long[] packed = SimdBooleanCompressor.packSIMD(original);
        assertThat(packed).hasSize(1);

        boolean[] restored = SimdBooleanCompressor.unpackSIMD(packed, 1);
        assertThat(restored).containsExactly(false);
    }

    @Test
    void fullLongWordBoundaryShouldBeExact() {
        // 64 bits = exactly 1 long
        boolean[] original = new boolean[64];
        for (int i = 0; i < 64; i++) {
            original[i] = (i % 3 == 0); // sparse pattern
        }

        long[] packed = SimdBooleanCompressor.packSIMD(original);
        assertThat(packed).hasSize(1);

        boolean[] restored = SimdBooleanCompressor.unpackSIMD(packed, 64);
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void allTrueAllFalseShouldRoundtrip() {
        for (boolean fill : new boolean[]{true, false}) {
            boolean[] original = new boolean[200];
            for (int i = 0; i < 200; i++) {
                original[i] = fill;
            }

            long[] packed = SimdBooleanCompressor.packSIMD(original);
            boolean[] restored = SimdBooleanCompressor.unpackSIMD(packed, 200);

            assertThat(restored)
                    .as("all %s roundtrip", fill)
                    .isEqualTo(original);
        }
    }

    // ─── Batch pack/unpack ───

    @Test
    void batchPackShouldProduceConsistentResults() {
        List<boolean[]> arrays = new ArrayList<>();
        Random rng = new Random(99);
        for (int a = 0; a < 5; a++) {
            boolean[] arr = new boolean[128];
            for (int i = 0; i < 128; i++) {
                arr[i] = rng.nextBoolean();
            }
            arrays.add(arr);
        }

        long[][] packed = SimdBooleanCompressor.batchPack(arrays);
        assertThat(packed.length).isEqualTo(5);

        // Verify each individually matches
        for (int a = 0; a < 5; a++) {
            boolean[] restored = SimdBooleanCompressor.unpackSIMD(packed[a], 128);
            assertThat(restored)
                    .as("batch pack array %d", a)
                    .isEqualTo(arrays.get(a));
        }
    }

    @Test
    void batchUnpackShouldProduceConsistentResults() {
        int bitCount = 256;
        Random rng = new Random(77);
        List<long[]> packedList = new ArrayList<>();
        List<boolean[]> originals = new ArrayList<>();

        for (int a = 0; a < 5; a++) {
            boolean[] arr = new boolean[bitCount];
            for (int i = 0; i < bitCount; i++) {
                arr[i] = rng.nextBoolean();
            }
            originals.add(arr);
            packedList.add(SimdBooleanCompressor.packSIMD(arr));
        }

        List<boolean[]> restored = SimdBooleanCompressor.batchUnpack(packedList, bitCount);

        assertThat(restored).hasSize(5);
        for (int a = 0; a < 5; a++) {
            assertThat(restored.get(a))
                    .as("batch unpack array %d", a)
                    .isEqualTo(originals.get(a));
        }
    }

    @Test
    void emptyBatchShouldReturnEmpty() {
        long[][] packed = SimdBooleanCompressor.batchPack(List.of());
        assertThat(packed).isEmpty();

        List<boolean[]> unpacked = SimdBooleanCompressor.batchUnpack(List.of(), 0);
        assertThat(unpacked).isEmpty();
    }

    // ─── BitSet roundtrip ───

    @Test
    void bitSetRoundtripShouldPreserveAllBits() {
        int bitCount = 512;
        BitSet original = new BitSet(bitCount);
        Random rng = new Random(55);
        // Set ~40% of bits
        for (int i = 0; i < 200; i++) {
            original.set(rng.nextInt(bitCount));
        }

        long[] packed = SimdBooleanCompressor.packBitSet(original, bitCount);
        BitSet restored = SimdBooleanCompressor.unpackBitSet(packed, bitCount);

        // Compare up to bitCount
        for (int i = 0; i < bitCount; i++) {
            assertThat(restored.get(i))
                    .as("bit %d", i)
                    .isEqualTo(original.get(i));
        }
    }

    @Test
    void emptyBitSetShouldRoundtrip() {
        BitSet original = new BitSet();
        long[] packed = SimdBooleanCompressor.packBitSet(original, 0);
        assertThat(packed).isEmpty();

        BitSet restored = SimdBooleanCompressor.unpackBitSet(packed, 0);
        assertThat(restored.isEmpty()).isTrue();
    }

    @Test
    void singleBitBitSetShouldRoundtrip() {
        BitSet original = new BitSet(1);
        original.set(0);

        long[] packed = SimdBooleanCompressor.packBitSet(original, 1);
        assertThat(packed).hasSize(1);
        assertThat(packed[0] & 1L).isEqualTo(1L);

        BitSet restored = SimdBooleanCompressor.unpackBitSet(packed, 1);
        assertThat(restored.get(0)).isTrue();
    }

    // ─── Performance / smoke ───

    @Test
    void largeArrayShouldProcessWithinReasonableTime() {
        int size = 8192;
        boolean[] original = new boolean[size];
        Random rng = new Random(123);
        for (int i = 0; i < size; i++) {
            original[i] = rng.nextBoolean();
        }

        long start = System.nanoTime();
        long[] packed = SimdBooleanCompressor.packSIMD(original);
        boolean[] restored = SimdBooleanCompressor.unpackSIMD(packed, size);
        long elapsed = System.nanoTime() - start;

        assertThat(restored).isEqualTo(original);
        // Should complete well under 1 second
        assertThat(elapsed).isLessThan(1_000_000_000L);
    }

    // ─── SIMD info ───

    @Test
    void simdLaneCountShouldBePositive() {
        int lanes = SimdBooleanCompressor.laneCount();
        assertThat(lanes).isPositive();
    }

    @Test
    void simdInfoShouldContainSpeciesDetails() {
        String info = SimdBooleanCompressor.info();
        assertThat(info).contains("SimdBooleanCompressor[")
                .contains("IntSpecies")
                .contains("LongSpecies")
                .contains("lanes=")
                .contains("bitSize=");
    }

    // ─── Packed size correctness ───

    @Test
    void packedArraySizeShouldMatchCeilDivision() {
        for (int bitCount : new int[]{0, 1, 63, 64, 65, 127, 128, 500, 1024}) {
            boolean[] bits = new boolean[bitCount];
            long[] packed = SimdBooleanCompressor.packSIMD(bits);
            int expectedLongs = (bitCount + 63) >>> 6;
            assertThat(packed).hasSize(expectedLongs);
        }
    }

    // ─── Deterministic output ───

    @Test
    void packShouldBeDeterministic() {
        boolean[] original = new boolean[256];
        Random rng = new Random(42);
        for (int i = 0; i < 256; i++) {
            original[i] = rng.nextBoolean();
        }

        long[] first = SimdBooleanCompressor.packSIMD(original);
        long[] second = SimdBooleanCompressor.packSIMD(original);

        assertThat(second).isEqualTo(first);
    }
}
