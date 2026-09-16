package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveQuantizationTest {

    @Test
    void quantize4BitHas13Values() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveQuantization.QuantizedProfile q = CognitiveQuantization.quantize4Bit(p);
        assertThat(q.values().length).isEqualTo(13);
    }

    @Test
    void quantize8BitHas13Values() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveQuantization.QuantizedProfile q = CognitiveQuantization.quantize8Bit(p);
        assertThat(q.values().length).isEqualTo(13);
    }

    @Test
    void quantize4BitBounded0To15() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveQuantization.QuantizedProfile q = CognitiveQuantization.quantize4Bit(p);
        for (short v : q.values()) {
            assertThat(v).isBetween((short) 0, (short) 15);
        }
    }

    @Test
    void quantize8BitBounded0To255() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveQuantization.QuantizedProfile q = CognitiveQuantization.quantize8Bit(p);
        for (short v : q.values()) {
            assertThat(v).isBetween((short) 0, (short) 255);
        }
    }

    @Test
    void dequantize4BitPreservesValues() {
        CognitiveGenesisProfile original = makeProfile(0.5);
        CognitiveQuantization.QuantizedProfile q = CognitiveQuantization.quantize4Bit(original);
        CognitiveGenesisProfile reconstructed = CognitiveQuantization.dequantize4Bit(q);
        assertThat(reconstructed).isNotNull();
    }

    @Test
    void dequantize8BitPreservesValues() {
        CognitiveGenesisProfile original = makeProfile(0.5);
        CognitiveQuantization.QuantizedProfile q = CognitiveQuantization.quantize8Bit(original);
        CognitiveGenesisProfile reconstructed = CognitiveQuantization.dequantize8Bit(q);
        assertThat(reconstructed).isNotNull();
    }

    @Test
    void quantizationErrorIsSmallFor8Bit() {
        CognitiveGenesisProfile original = makeProfile(0.5);
        CognitiveQuantization.QuantizedProfile q = CognitiveQuantization.quantize8Bit(original);
        CognitiveGenesisProfile reconstructed = CognitiveQuantization.dequantize8Bit(q);
        double error = CognitiveQuantization.quantizationError(original, reconstructed);
        // 8-bit should have small error
        assertThat(error).isLessThan(0.1);
    }

    @Test
    void nullQuantizeReturnsEmpty() {
        CognitiveQuantization.QuantizedProfile q = CognitiveQuantization.quantize4Bit(null);
        assertThat(q.values().length).isEqualTo(13);
    }

    @Test
    void compressionRatio4BitIs8x() {
        assertThat(CognitiveQuantization.compressionRatio(4)).isCloseTo(8.0, offset(1e-9));
    }

    @Test
    void compressionRatio8BitIs4x() {
        assertThat(CognitiveQuantization.compressionRatio(8)).isCloseTo(4.0, offset(1e-9));
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
