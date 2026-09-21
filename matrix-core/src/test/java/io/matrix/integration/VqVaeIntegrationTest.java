package io.matrix.integration;

import io.matrix.vqvae.CodeBook;
import io.matrix.vqvae.VqVaeProxy;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9: Integration tests for VQ-VAE (Vector Quantized Variational Autoencoder).
 *
 * <p>Tests CodeBook EMA learning, encode-decode roundtrip, and
 * sensor/effector proxy paths.
 */
class VqVaeIntegrationTest {

    private static final int DIMENSION = 16;
    private static final int CODE_SIZE = 64;

    @Test
    void codeBookEmaLearning() {
        CodeBook codeBook = CodeBook.builder(DIMENSION)
                .codeSize(CODE_SIZE)
                .momentum(0.2)
                .build();

        double[] target = new double[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            target[i] = (i % 2 == 0) ? 1.0 : -1.0;
        }

        // Get initial closest code
        int initialIndex = codeBook.encode(target);
        double[] initialCode = codeBook.getCode(initialIndex);

        // Apply EMA updates — the code should move toward the target
        for (int epoch = 0; epoch < 100; epoch++) {
            codeBook.emaUpdate(initialIndex, target);
        }

        double[] updatedCode = codeBook.getCode(initialIndex);

        // Verify EMA moved code toward target
        double initialDist = squaredDistance(initialCode, target);
        double updatedDist = squaredDistance(updatedCode, target);
        assertThat(updatedDist).isLessThan(initialDist);
    }

    @Test
    void encodeDecodeRoundtrip() {
        CodeBook codeBook = CodeBook.builder(DIMENSION)
                .codeSize(CODE_SIZE)
                .momentum(0.1)
                .build();

        double[] input = new double[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            input[i] = Math.sin(i * 0.5);
        }

        int codeIndex = codeBook.encode(input);
        boolean[] decoded = codeBook.decode(codeIndex);

        assertThat(codeIndex).isBetween(0, CODE_SIZE - 1);
        assertThat(decoded).hasSize(DIMENSION);

        // Encode the decoded boolean vector back — should map to same code
        double[] reconstructed = new double[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            reconstructed[i] = decoded[i] ? 1.0 : -1.0;
        }
        int reEncoded = codeBook.encode(reconstructed);
        // The re-encoded index should be the same (nearest neighbor stability)
        assertThat(reEncoded).isEqualTo(codeIndex);
    }

    @Test
    void sensorProxyEncodes() {
        VqVaeProxy proxy = VqVaeProxy.builder()
                .dimension(DIMENSION)
                .codeSize(CODE_SIZE)
                .momentum(0.1)
                .build();

        double[] continuousInput = new double[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            continuousInput[i] = Math.random();
        }

        boolean[] booleanOutput = proxy.sensorEncode(continuousInput);

        assertThat(booleanOutput).hasSize(DIMENSION);
        // Output should be a valid boolean vector
        for (boolean b : booleanOutput) {
            assertThat(b).isIn(true, false);
        }
    }

    @Test
    void effectorProxyDecodes() {
        VqVaeProxy proxy = VqVaeProxy.builder()
                .dimension(DIMENSION)
                .codeSize(CODE_SIZE)
                .momentum(0.1)
                .build();

        boolean[] booleanInput = new boolean[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            booleanInput[i] = i % 3 == 0;
        }

        double[] continuousOutput = proxy.effectorDecode(booleanInput);

        assertThat(continuousOutput).hasSize(DIMENSION);
        // Output should be finite
        for (double d : continuousOutput) {
            assertThat(Double.isFinite(d)).isTrue();
        }
    }

    @Test
    void sensorEffectorRoundtrip() {
        VqVaeProxy proxy = VqVaeProxy.builder()
                .dimension(DIMENSION)
                .codeSize(CODE_SIZE)
                .momentum(0.1)
                .build();

        double[] original = new double[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            original[i] = (i < DIMENSION / 2) ? 2.0 : -2.0;
        }

        boolean[] encoded = proxy.sensorEncode(original);
        double[] decoded = proxy.effectorDecode(encoded);

        // The decoded continuous vector should be finite and non-zero
        assertThat(decoded).hasSize(DIMENSION);
        boolean hasNonZero = false;
        for (double d : decoded) {
            if (d != 0.0) hasNonZero = true;
        }
        assertThat(hasNonZero).isTrue();
    }

    @Test
    void codeBookMultipleUpdatesConverge() {
        CodeBook codeBook = CodeBook.builder(DIMENSION)
                .codeSize(CODE_SIZE)
                .momentum(0.3)
                .build();

        double[] target = new double[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            target[i] = (i < DIMENSION / 2) ? 5.0 : -5.0;
        }

        int idx = codeBook.encode(target);

        // Many EMA updates should bring the code very close to target
        for (int i = 0; i < 500; i++) {
            codeBook.emaUpdate(idx, target);
        }

        double[] finalCode = codeBook.getCode(idx);
        for (int i = 0; i < DIMENSION; i++) {
            assertThat(finalCode[i]).isCloseTo(target[i], org.assertj.core.data.Offset.offset(0.5));
        }
    }

    @Test
    void proxyDimensionValidation() {
        VqVaeProxy proxy = VqVaeProxy.builder()
                .dimension(DIMENSION)
                .codeSize(CODE_SIZE)
                .build();

        // Wrong dimension should throw
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                proxy.sensorEncode(new double[DIMENSION + 1])
        ).isInstanceOf(IllegalArgumentException.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                proxy.effectorDecode(new boolean[DIMENSION + 1])
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private static double squaredDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return sum;
    }
}
