package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveMultiResolutionAttentionTest {

    @Test
    void constructValid() {
        CognitiveMultiResolutionAttention mra =
            new CognitiveMultiResolutionAttention(64, 4, 42L);
        assertThat(mra.numResolutions()).isEqualTo(4);
    }

    @Test
    void invalidDimsThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveMultiResolutionAttention(0, 4, 1L)
        );
    }

    @Test
    void attendEmptyKeysReturnsQuery() {
        CognitiveMultiResolutionAttention mra =
            new CognitiveMultiResolutionAttention(64, 4, 42L);
        double[] query = new double[64];
        double[] result = mra.attendMultiResolution(query, new ArrayList<>(), new ArrayList<>());
        assertThat(result).isSameAs(query);
    }

    @Test
    void attendNullReturnsNull() {
        CognitiveMultiResolutionAttention mra =
            new CognitiveMultiResolutionAttention(64, 4, 42L);
        double[] result = mra.attendMultiResolution(null, null, null);
        assertThat(result).isNull();
    }

    @Test
    void attendProducesValidOutput() {
        CognitiveMultiResolutionAttention mra =
            new CognitiveMultiResolutionAttention(64, 4, 42L);
        double[] query = new double[64];
        for (int i = 0; i < 64; i++) query[i] = i / 64.0;
        List<double[]> keys = new ArrayList<>();
        List<double[]> values = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double[] k = new double[64];
            double[] v = new double[64];
            for (int j = 0; j < 64; j++) {
                k[j] = (j + i) / 64.0;
                v[j] = j * i / 64.0;
            }
            keys.add(k);
            values.add(v);
        }
        double[] result = mra.attendMultiResolution(query, keys, values);
        assertThat(result.length).isEqualTo(64);
    }

    @Test
    void attendMismatchedSizesReturnsQuery() {
        CognitiveMultiResolutionAttention mra =
            new CognitiveMultiResolutionAttention(64, 4, 42L);
        double[] query = new double[64];
        List<double[]> keys = new ArrayList<>();
        keys.add(new double[64]);
        List<double[]> values = new ArrayList<>();
        // Mismatched: 1 key, 0 values
        double[] result = mra.attendMultiResolution(query, keys, values);
        assertThat(result).isSameAs(query);
    }
}
