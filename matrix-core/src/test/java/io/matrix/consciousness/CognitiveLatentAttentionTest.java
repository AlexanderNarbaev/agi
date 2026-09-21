package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveLatentAttentionTest {

    @Test
    void constructValid() {
        CognitiveLatentAttention mla = new CognitiveLatentAttention(64, 8, 16, 42L);
        assertThat(mla.dim()).isEqualTo(64);
        assertThat(mla.numHeads()).isEqualTo(8);
        assertThat(mla.headDim()).isEqualTo(8);
        assertThat(mla.latentDim()).isEqualTo(16);
    }

    @Test
    void invalidDivisibilityThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveLatentAttention(63, 8, 16, 1L)
        );
    }

    @Test
    void compressReturnsCorrectSize() {
        CognitiveLatentAttention mla = new CognitiveLatentAttention(64, 8, 16, 42L);
        double[] v = new double[64];
        for (int i = 0; i < 64; i++) v[i] = i / 64.0;
        double[] latent = mla.compress(v, true);
        assertThat(latent.length).isEqualTo(16);
    }

    @Test
    void compressKeyAndValue() {
        CognitiveLatentAttention mla = new CognitiveLatentAttention(64, 8, 16, 42L);
        double[] v = new double[64];
        double[] latentK = mla.compress(v, true);
        double[] latentV = mla.compress(v, false);
        assertThat(latentK).isNotNull();
        assertThat(latentV).isNotNull();
    }

    @Test
    void decompressReturnsCorrectSize() {
        CognitiveLatentAttention mla = new CognitiveLatentAttention(64, 8, 16, 42L);
        double[] latent = new double[16];
        double[] decomp = mla.decompress(latent, true);
        assertThat(decomp.length).isEqualTo(64);
    }

    @Test
    void nullCompressReturnsNull() {
        CognitiveLatentAttention mla = new CognitiveLatentAttention(64, 8, 16, 42L);
        assertThat(mla.compress(null, true)).isNull();
    }

    @Test
    void compressionRatioCorrect() {
        CognitiveLatentAttention mla = new CognitiveLatentAttention(64, 8, 16, 42L);
        // Standard MHA: 2 × 8 × 8 = 128
        // MLA: 16
        // Ratio: 128 / 16 = 8
        assertThat(mla.compressionRatio()).isEqualTo(8.0);
    }

    @Test
    void seedRecorded() {
        CognitiveLatentAttention mla = new CognitiveLatentAttention(64, 8, 16, 42L);
        assertThat(mla.seed()).isEqualTo(42L);
    }
}
