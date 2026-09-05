package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 106 — TextEmbedder unit tests. */
class TextEmbedderTest {

    @Test
    void cosineSimilarityIdenticalVectors() {
        float[] a = {1, 0, 0};
        float[] b = {1, 0, 0};
        assertThat(TextEmbedder.cosineSimilarity(a, b)).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void cosineSimilarityOrthogonalVectors() {
        float[] a = {1, 0, 0};
        float[] b = {0, 1, 0};
        assertThat(TextEmbedder.cosineSimilarity(a, b)).isCloseTo(0.0,
                org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void cosineSimilarityOppositeVectors() {
        float[] a = {1, 0, 0};
        float[] b = {-1, 0, 0};
        assertThat(TextEmbedder.cosineSimilarity(a, b)).isCloseTo(-1.0,
                org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void cosineSimilarityZeroVector() {
        float[] a = {0, 0, 0};
        float[] b = {1, 0, 0};
        assertThat(TextEmbedder.cosineSimilarity(a, b)).isZero();
    }

    @Test
    void cosineSimilarityLengthMismatch() {
        float[] a = {1, 0};
        float[] b = {1, 0, 0};
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> TextEmbedder.cosineSimilarity(a, b))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void l2DistanceBasic() {
        float[] a = {0, 0, 0};
        float[] b = {3, 4, 0};
        assertThat(TextEmbedder.l2Distance(a, b)).isCloseTo(5.0,
                org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void l2DistanceIdentical() {
        float[] a = {1, 2, 3};
        float[] b = {1, 2, 3};
        assertThat(TextEmbedder.l2Distance(a, b)).isCloseTo(0.0,
                org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void l2DistanceLengthMismatch() {
        float[] a = {1, 0};
        float[] b = {1, 0, 0};
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> TextEmbedder.l2Distance(a, b))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorStoresValues() {
        QwenOnnxBridge stub = new QwenOnnxBridge(java.nio.file.Path.of("/tmp/none"));
        TextEmbedder e = new TextEmbedder(stub, 768, 50256);
        assertThat(e.hiddenSize()).isEqualTo(768);
        assertThat(e.toString()).contains("768");
    }

    @Test
    void defaultHiddenSizeIsQwen05b() {
        QwenOnnxBridge stub = new QwenOnnxBridge(java.nio.file.Path.of("/tmp/none"));
        TextEmbedder e = new TextEmbedder(stub);
        assertThat(e.hiddenSize()).isEqualTo(896);
    }
}
