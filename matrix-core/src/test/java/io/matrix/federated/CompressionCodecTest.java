package io.matrix.federated;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CompressionCodecTest {

    @Test
    void compressDecompressRoundtrip() {
        var codec = new CompressionCodec();
        boolean[] original = {true, false, true, true, false, false, false, true};
        byte[] compressed = codec.compress(original);
        boolean[] decompressed = codec.decompress(compressed);
        assertArrayEquals(original, decompressed);
    }

    @Test
    void compressEmptyArray() {
        var codec = new CompressionCodec();
        byte[] compressed = codec.compress(new boolean[0]);
        assertEquals(0, compressed.length);
    }

    @Test
    void compressAllTrue() {
        var codec = new CompressionCodec();
        boolean[] allTrue = {true, true, true, true, true};
        byte[] compressed = codec.compress(allTrue);
        boolean[] decompressed = codec.decompress(compressed);
        assertArrayEquals(allTrue, decompressed);
    }

    @Test
    void compressAllFalse() {
        var codec = new CompressionCodec();
        boolean[] allFalse = {false, false, false, false, false};
        byte[] compressed = codec.compress(allFalse);
        boolean[] decompressed = codec.decompress(compressed);
        assertArrayEquals(allFalse, decompressed);
    }

    @Test
    void compressionRatioPositive() {
        var codec = new CompressionCodec();
        boolean[] data = {true, true, true, false, false, false};
        double ratio = codec.compressionRatio(data);
        assertTrue(ratio > 0 && ratio <= 1.0);
    }

    @Test
    void compressionRatioEmptyIsOne() {
        var codec = new CompressionCodec();
        assertEquals(1.0, codec.compressionRatio(new boolean[0]));
    }
}
