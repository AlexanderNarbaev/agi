package io.matrix.nas;

import io.matrix.nas.ArchitectureSpec.Activation;
import io.matrix.nas.ArchitectureSpec.LayerSpec;
import io.matrix.nas.ArchitectureSpec.LayerType;
import io.matrix.nas.ArchitectureSpec.MutationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchitectureSpecTest {

    private final Random rng = new Random(42);

    // ─── Creation ───

    @Test
    void ofShouldCreateSpecWithLayers() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0),
                new LayerSpec(LayerType.DENSE, 8, Activation.SIGMOID, 0));

        ArchitectureSpec spec = ArchitectureSpec.of(layers);

        assertThat(spec.layers()).hasSize(2);
        assertThat(spec.layerCount()).isEqualTo(2);
        assertThat(spec.totalNeurons()).isEqualTo(24);
        assertThat(spec.generation()).isZero();
    }

    @Test
    void randomShouldCreateValidArchitecture() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 32, rng);

        assertThat(spec.layers()).hasSize(3);
        assertThat(spec.totalNeurons()).isGreaterThan(0);
        assertThat(spec.complexity()).isGreaterThan(0);
    }

    @Test
    void randomShouldRejectInvalidLayerCount() {
        assertThatThrownBy(() -> ArchitectureSpec.random(0, 32, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Layer Spec Validation ───

    @Test
    void layerSpecShouldRejectInvalidSize() {
        assertThatThrownBy(() -> new LayerSpec(LayerType.DENSE, 0, Activation.RELU, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void layerSpecShouldRejectNegativeConnection() {
        assertThatThrownBy(() -> new LayerSpec(LayerType.DENSE, 8, Activation.RELU, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Complexity Metrics ───

    @Test
    void complexityShouldBeDepthTimesNeurons() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 10, Activation.RELU, 0),
                new LayerSpec(LayerType.DENSE, 20, Activation.RELU, 0));

        ArchitectureSpec spec = ArchitectureSpec.of(layers);

        assertThat(spec.complexity()).isEqualTo(60); // (10+20) * 2
    }

    @Test
    void maxLayerSizeShouldReturnLargest() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 8, Activation.RELU, 0),
                new LayerSpec(LayerType.DENSE, 32, Activation.RELU, 0),
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0));

        ArchitectureSpec spec = ArchitectureSpec.of(layers);

        assertThat(spec.maxLayerSize()).isEqualTo(32);
    }

    // ─── Mutation ───

    @Test
    void addLayerShouldInsertAtSpecifiedIndex() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0));

        ArchitectureSpec spec = ArchitectureSpec.of(layers);
        LayerSpec newLayer = new LayerSpec(LayerType.CONV1D, 8, Activation.TANH, 0);
        ArchitectureSpec mutated = spec.withMutation(new MutationResult.AddLayer(newLayer, 1));

        assertThat(mutated.layerCount()).isEqualTo(2);
        assertThat(mutated.layers().get(1).type()).isEqualTo(LayerType.CONV1D);
        assertThat(mutated.generation()).isEqualTo(1);
    }

    @Test
    void addLayerAtZeroShouldPrepend() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0));

        ArchitectureSpec spec = ArchitectureSpec.of(layers);
        LayerSpec newLayer = new LayerSpec(LayerType.ATTENTION, 4, Activation.SOFTMAX, 0);
        ArchitectureSpec mutated = spec.withMutation(new MutationResult.AddLayer(newLayer, 0));

        assertThat(mutated.layers().get(0).type()).isEqualTo(LayerType.ATTENTION);
        assertThat(mutated.layers().get(1).type()).isEqualTo(LayerType.DENSE);
    }

    @Test
    void removeLayerShouldDecreaseSize() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0),
                new LayerSpec(LayerType.DENSE, 8, Activation.SIGMOID, 0));

        ArchitectureSpec spec = ArchitectureSpec.of(layers);
        ArchitectureSpec mutated = spec.withMutation(new MutationResult.RemoveLayer(0));

        assertThat(mutated.layerCount()).isEqualTo(1);
        assertThat(mutated.layers().get(0).size()).isEqualTo(8);
    }

    @Test
    void removeLayerOnEmptyShouldReturnSame() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of());
        ArchitectureSpec mutated = spec.withMutation(new MutationResult.RemoveLayer(0));

        assertThat(mutated).isEqualTo(spec);
    }

    @Test
    void changeSizeShouldUpdateLayerSize() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0));

        ArchitectureSpec spec = ArchitectureSpec.of(layers);
        ArchitectureSpec mutated = spec.withMutation(new MutationResult.ChangeSize(0, 32));

        assertThat(mutated.layers().get(0).size()).isEqualTo(32);
        assertThat(mutated.layers().get(0).type()).isEqualTo(LayerType.DENSE);
    }

    @Test
    void changeActivationShouldUpdateActivation() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0));

        ArchitectureSpec spec = ArchitectureSpec.of(layers);
        ArchitectureSpec mutated = spec.withMutation(new MutationResult.ChangeActivation(0, Activation.GELU));

        assertThat(mutated.layers().get(0).activation()).isEqualTo(Activation.GELU);
    }

    @Test
    void changeLayerTypeShouldUpdateType() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0));

        ArchitectureSpec spec = ArchitectureSpec.of(layers);
        ArchitectureSpec mutated = spec.withMutation(new MutationResult.ChangeLayerType(0, LayerType.ATTENTION));

        assertThat(mutated.layers().get(0).type()).isEqualTo(LayerType.ATTENTION);
    }

    @Test
    void noOpShouldReturnSameSpec() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 16, rng);
        ArchitectureSpec mutated = spec.withMutation(new MutationResult.NoOp());

        assertThat(mutated).isEqualTo(spec);
    }

    @RepeatedTest(20)
    void randomMutationsShouldPreserveValidSpec() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 32, rng);
        MutationOperator op = new MutationOperator(rng);

        for (int i = 0; i < 10; i++) {
            MutationResult mutation = op.randomMutate(spec);
            spec = spec.withMutation(mutation);
            assertThat(spec.layerCount()).isGreaterThanOrEqualTo(0);
            assertThat(spec.totalNeurons()).isGreaterThanOrEqualTo(0);
        }
    }

    // ─── Serialization ───

    @Test
    void toPromptStringShouldContainAllLayers() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0),
                new LayerSpec(LayerType.CONV1D, 8, Activation.TANH, 1));

        ArchitectureSpec spec = ArchitectureSpec.of(layers);
        String prompt = spec.toPromptString();

        assertThat(prompt).contains("DENSE");
        assertThat(prompt).contains("size=16");
        assertThat(prompt).contains("RELU");
        assertThat(prompt).contains("CONV1D");
        assertThat(prompt).contains("TANH");
        assertThat(prompt).contains("layers=2");
    }

    @Test
    void toJsonShouldBeValidFormat() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0));

        ArchitectureSpec spec = ArchitectureSpec.of(layers);
        String json = spec.toJson();

        assertThat(json).contains("\"type\":\"DENSE\"");
        assertThat(json).contains("\"size\":16");
        assertThat(json).contains("\"activation\":\"RELU\"");
        assertThat(json).startsWith("{");
        assertThat(json).endsWith("}");
    }

    @Test
    void roundTripPromptStringShouldPreserveLayers() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0),
                new LayerSpec(LayerType.ATTENTION, 8, Activation.SOFTMAX, 0));

        ArchitectureSpec original = ArchitectureSpec.of(layers);
        String prompt = original.toPromptString();
        ArchitectureSpec parsed = ArchitectureSpec.fromPromptString(prompt);

        assertThat(parsed.layerCount()).isEqualTo(original.layerCount());
        assertThat(parsed.layers().get(0).type()).isEqualTo(LayerType.DENSE);
        assertThat(parsed.layers().get(0).size()).isEqualTo(16);
        assertThat(parsed.layers().get(0).activation()).isEqualTo(Activation.RELU);
        assertThat(parsed.layers().get(1).type()).isEqualTo(LayerType.ATTENTION);
    }

    @Test
    void fromPromptStringShouldRejectEmptyInput() {
        assertThatThrownBy(() -> ArchitectureSpec.fromPromptString("no layers here"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Equality ───

    @Test
    void equalSpecsShouldBeEqual() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0));
        ArchitectureSpec spec1 = ArchitectureSpec.of(layers);
        ArchitectureSpec spec2 = ArchitectureSpec.of(spec1.id(), layers, 0);

        assertThat(spec1).isEqualTo(spec2);
        assertThat(spec1.hashCode()).isEqualTo(spec2.hashCode());
    }

    @Test
    void differentSpecsShouldNotBeEqual() {
        ArchitectureSpec spec1 = ArchitectureSpec.random(3, 16, rng);
        ArchitectureSpec spec2 = ArchitectureSpec.random(3, 16, new Random(99));

        assertThat(spec1).isNotEqualTo(spec2);
    }
}
