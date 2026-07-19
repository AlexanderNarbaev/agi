package io.matrix.imports;

import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TensorProjectorTest {

    @Test
    void shouldProduceNeuronsForConstantTensor() {
        // Constant tensor — min == max → project still produces neurons (uses midpoint).
        SafetensorsReader.Tensor t =
                new SafetensorsReader.Tensor("c", "F32", new int[]{8}, new float[]{0.5f, 0.5f, 0.5f, 0.5f,
                        0.5f, 0.5f, 0.5f, 0.5f});
        TensorProjector p = new TensorProjector(64);  // budget = 64 → k ≤ 6
        TensorProjector.Projection proj = p.project(t);
        assertThat(proj.neuronCount()).isGreaterThan(0);
        assertThat(proj.truthTables()).allSatisfy(tt -> {
            assertThat(tt.k()).isGreaterThanOrEqualTo(1).isLessThanOrEqualTo(6);
        });
    }

    @Test
    void shouldPickSmallKForLargeBudget() {
        // Default budget = 65_536 → k ≈ 16
        SafetensorsReader.Tensor t = buildFloatTensor("big", 1024);
        TensorProjector p = new TensorProjector();
        TensorProjector.Projection proj = p.project(t);
        assertThat(proj.truthTables().get(0).k()).isEqualTo(10);  // smallest covering 2^10=1024
    }

    @Test
    void shouldBoundKByKMax20() {
        // Force a budget bigger than 2^20 → k still capped at 20.
        SafetensorsReader.Tensor t = buildFloatTensor("huge", 200);
        TensorProjector p = new TensorProjector(1 << 30);
        TensorProjector.Projection proj = p.project(t);
        assertThat(proj.truthTables().get(0).k()).isLessThanOrEqualTo(TruthTable.K_MAX);
    }

    @Test
    void emptyTensorShouldProduceEmptyProjection() {
        SafetensorsReader.Tensor t = new SafetensorsReader.Tensor("empty", "F32", new int[]{0}, new float[0]);
        TensorProjector p = new TensorProjector();
        TensorProjector.Projection proj = p.project(t);
        assertThat(proj.neuronCount()).isZero();
        assertThat(proj.truthTables()).isEmpty();
    }

    @Test
    void shouldThrowOnNonPositiveBudget() {
        assertThatThrownBy(() -> new TensorProjector(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TensorProjector(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void citationStringShouldEmbedProvenance() {
        SafetensorsReader.Tensor t = buildFloatTensor("x.weight", 64);
        TensorProjector p = new TensorProjector();
        TensorProjector.Projection proj = p.project(t);
        assertThat(proj.citation()).contains("x.weight").contains("F32");
    }

    private static SafetensorsReader.Tensor buildFloatTensor(String name, int length) {
        float[] data = new float[length];
        for (int i = 0; i < length; i++) data[i] = (i % 7 - 3) * 0.1f;
        return new SafetensorsReader.Tensor(name, "F32", new int[]{length}, data);
    }
}
