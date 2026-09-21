package io.matrix.research;

import io.matrix.imports.BitNetWeightUnpacker;
import io.matrix.imports.SafetensorsReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 43: Real BitNet b1.58 2B weight loading + unpacking test.
 *
 * <p>Loads actual gate_proj weights from microsoft/bitnet-b1.58-2B-4T,
 * unpacks them using our BitNetWeightUnpacker, and verifies the distribution
 * matches Microsoft's published numbers (~30% / -1, ~40% / 0, ~30% / +1).
 */
class BitNetRealLoadTest {

    private static final String MODEL_PATH =
            "/tmp/hf_cache/models--microsoft--bitnet-b1.58-2B-4T/snapshots/"
                    + "04c3b9ad9361b824064a1f25ea60a8be9599b127/model.safetensors";

    @Test
    void unpackRealGateProjLayer0ViaSafetensorsReader() throws IOException {
        // Use our newly-added loadTensorBytes to get raw bytes
        SafetensorsReader reader = new SafetensorsReader();
        try (FileChannel ch = FileChannel.open(Path.of(MODEL_PATH),
                StandardOpenOption.READ)) {
            SafetensorsReader.Header header = reader.readHeader(Path.of(MODEL_PATH));

            // Load the actual packed weight bytes
            byte[] packed = reader.loadTensorBytes(ch, header,
                    "model.layers.0.mlp.gate_proj.weight");
            // Expected shape: (1728, 2560) → packed length = 1728 * 2560 = 4423680 bytes
            assertThat(packed.length).isEqualTo(1728 * 2560);
            System.out.printf("[BitNet real] gate_proj packed: %d bytes%n", packed.length);

            // Load the bf16 scale
            float scale = reader.loadBf16Scale(ch, header,
                    "model.layers.0.mlp.gate_proj.weight_scale");
            System.out.printf("[BitNet real] gate_proj scale: %.6f%n", scale);
            assertThat(scale).isBetween(0.5f, 3.0f); // Real BitNet scales are ~1.0

            // Unpack the weights
            float[] dequantized = BitNetWeightUnpacker.unpack(packed, scale, 6912, 2560);
            assertThat(dequantized.length).isEqualTo(6912 * 2560);
            System.out.printf("[BitNet real] unpacked: %d floats (outDim=6912, inDim=2560)%n",
                    dequantized.length);

            // Count distribution
            int negCount = 0, zeroCount = 0, posCount = 0;
            for (float v : dequantized) {
                if (v < 0) negCount++;
                else if (v == 0) zeroCount++;
                else posCount++;
            }
            int total = negCount + zeroCount + posCount;
            double negRate = (double) negCount / total;
            double zeroRate = (double) zeroCount / total;
            double posRate = (double) posCount / total;
            System.out.printf("[BitNet real] distribution: %.1f%% -1, %.1f%% 0, %.1f%% +1%n",
                    negRate * 100, zeroRate * 100, posRate * 100);

            // Microsoft's published numbers (from deep-research verification):
            // -1: ~30.27%, 0: ~39.13%, +1: ~30.60%
            assertThat(negRate).isBetween(0.27, 0.34);
            assertThat(zeroRate).isBetween(0.36, 0.43);
            assertThat(posRate).isBetween(0.27, 0.34);

            // Verify scale: most non-zero values should be ±scale
            int atScale = 0;
            for (float v : dequantized) {
                if (Math.abs(Math.abs(v) - scale) < 0.01) atScale++;
            }
            assertThat(atScale).isGreaterThan(total / 2); // >50% of values are ±scale
            System.out.printf("[BitNet real] %d/%d (%.1f%%) values are ±scale%n",
                    atScale, total, 100.0 * atScale / total);
        }
    }

    @Test
    void loadTensorBytesReturnsRawBytesForUint8() throws IOException {
        SafetensorsReader reader = new SafetensorsReader();
        try (FileChannel ch = FileChannel.open(Path.of(MODEL_PATH),
                StandardOpenOption.READ)) {
            SafetensorsReader.Header header = reader.readHeader(Path.of(MODEL_PATH));

            // Verify we can read raw bytes for a U8 tensor
            byte[] packed = reader.loadTensorBytes(ch, header,
                    "model.layers.0.mlp.gate_proj.weight");
            assertThat(packed).isNotEmpty();
            // Values should be in 0-255 range (uint8)
            for (byte b : packed) {
                assertThat(b & 0xFF).isBetween(0, 255);
            }
            System.out.printf("[BitNet real] gate_proj raw bytes sample: %d, %d, %d, %d, %d%n",
                    packed[0] & 0xFF, packed[1] & 0xFF, packed[2] & 0xFF,
                    packed[3] & 0xFF, packed[4] & 0xFF);
        }
    }
}
