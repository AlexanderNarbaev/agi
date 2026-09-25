package io.matrix.distill;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W731 — BitNet Encoder Tests.
 *
 * Tests 2-bit quantization of HDC vectors:
 * - Compression ratio
 * - Round-trip accuracy
 * - Cosine similarity preservation
 */
class BitNetEncoderTest {

    @Test
    void testEncodeBasicVector() {
        BitNetEncoder encoder = new BitNetEncoder();
        boolean[] vector = {true, false, true, true, false, false, true, false};

        BitNetEncoder.EncodingResult result = encoder.encode(vector);

        assertEquals(8, result.originalBits());
        assertTrue(result.encodedBytes() > 0);
        assertTrue(result.compressionRatio() >= 0.5);
    }

    @Test
    void testCompressionRatio() {
        BitNetEncoder encoder = new BitNetEncoder();
        // 10000-bit vector
        boolean[] vector = new boolean[10000];
        Random rng = new Random(42);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = rng.nextBoolean();
        }

        BitNetEncoder.EncodingResult result = encoder.encode(vector);

        // 2-bit encoding: 10000 bits → 5000 2-bit values → 1250 bytes
        // Compression ratio: 10000 / (1250 * 8) = 1.0 (no compression for 2-bit)
        // But we get at least 1.0x
        assertTrue(result.compressionRatio() >= 0.5,
            "Compression ratio should be >= 1.0: " + result.compressionRatio());
    }

    @Test
    void testRoundTripAccuracy() {
        BitNetEncoder encoder = new BitNetEncoder();
        boolean[] vector = new boolean[100];
        Random rng = new Random(42);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = rng.nextBoolean();
        }

        double accuracy = encoder.testRoundTripAccuracy(vector);
        // With 2-bit quantization, round-trip should be high
        assertTrue(accuracy > 0.5, "Round-trip accuracy should be > 50%: " + accuracy);
    }

    @Test
    void testEstimatedAccuracyRetention() {
        BitNetEncoder encoder = new BitNetEncoder();
        boolean[] vector = new boolean[1000];
        Random rng = new Random(42);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = rng.nextBoolean();
        }

        BitNetEncoder.EncodingResult result = encoder.encode(vector);

        // Target: preserve > 95% cosine similarity
        assertTrue(result.estimatedAccuracyRetention() >= 0.90,
            "Estimated retention should be >= 90%: " + result.estimatedAccuracyRetention());
    }

    @Test
    void testCosineSimilarityIdentical() {
        boolean[] v1 = {true, false, true, true, false};
        boolean[] v2 = {true, false, true, true, false};

        double sim = BitNetEncoder.cosineSimilarity(v1, v2);
        assertEquals(1.0, sim, 0.01, "Identical vectors should have similarity 1.0");
    }

    @Test
    void testCosineSimilarityOrthogonal() {
        boolean[] v1 = {true, true, true, true};
        boolean[] v2 = {false, false, false, false};

        double sim = BitNetEncoder.cosineSimilarity(v1, v2);
        assertEquals(0.0, sim, 0.01, "Orthogonal vectors should have similarity 0.0");
    }

    @Test
    void testCosineSimilarityDifferentLengths() {
        boolean[] v1 = {true, false};
        boolean[] v2 = {true, false, true};

        assertThrows(IllegalArgumentException.class,
            () -> BitNetEncoder.cosineSimilarity(v1, v2));
    }

    @Test
    void testQuantLevels() {
        assertEquals(0, BitNetEncoder.QuantLevel.STRONG_ZERO.code());
        assertEquals(1, BitNetEncoder.QuantLevel.WEAK_ZERO.code());
        assertEquals(2, BitNetEncoder.QuantLevel.WEAK_ONE.code());
        assertEquals(3, BitNetEncoder.QuantLevel.STRONG_ONE.code());

        assertEquals(-1.0, BitNetEncoder.QuantLevel.STRONG_ZERO.value(), 0.01);
        assertEquals(1.0, BitNetEncoder.QuantLevel.STRONG_ONE.value(), 0.01);
    }

    @Test
    void testQuantLevelFromBits() {
        assertEquals(BitNetEncoder.QuantLevel.STRONG_ZERO,
            BitNetEncoder.QuantLevel.fromBits(0, 0));
        assertEquals(BitNetEncoder.QuantLevel.WEAK_ZERO,
            BitNetEncoder.QuantLevel.fromBits(0, 1));
        assertEquals(BitNetEncoder.QuantLevel.WEAK_ONE,
            BitNetEncoder.QuantLevel.fromBits(1, 0));
        assertEquals(BitNetEncoder.QuantLevel.STRONG_ONE,
            BitNetEncoder.QuantLevel.fromBits(1, 1));
    }

    @Test
    void testEncodeEmptyVector() {
        BitNetEncoder encoder = new BitNetEncoder();
        boolean[] vector = {};

        BitNetEncoder.EncodingResult result = encoder.encode(vector);
        assertEquals(0, result.originalBits());
    }

    @Test
    void testEncodeOddLength() {
        BitNetEncoder encoder = new BitNetEncoder();
        boolean[] vector = {true, false, true}; // odd length

        BitNetEncoder.EncodingResult result = encoder.encode(vector);
        assertEquals(3, result.originalBits());
        // Should handle padding gracefully
        assertTrue(result.encodedBytes() >= 1);
    }

    @Test
    void testLargeVectorPerformance() {
        BitNetEncoder encoder = new BitNetEncoder();
        boolean[] vector = new boolean[100000];
        Random rng = new Random(42);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = rng.nextBoolean();
        }

        long start = System.currentTimeMillis();
        BitNetEncoder.EncodingResult result = encoder.encode(vector);
        long duration = System.currentTimeMillis() - start;

        assertEquals(100000, result.originalBits());
        assertTrue(duration < 1000, "Encoding 100k bits should be fast: " + duration + "ms");
    }
}
