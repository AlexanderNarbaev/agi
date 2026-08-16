package io.matrix.nas;

import io.matrix.nas.ArchitectureSpec.Activation;
import io.matrix.nas.ArchitectureSpec.LayerSpec;
import io.matrix.nas.ArchitectureSpec.LayerType;
import io.matrix.nas.ArchitectureSpec.MutationResult;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MutationOperatorTest {

    private final Random rng = new Random(42);
    private final MutationOperator op = new MutationOperator(rng);

    // ─── ADD_LAYER ───

    @Test
    void addLayerShouldCreateValidMutation() {
        ArchitectureSpec spec = ArchitectureSpec.random(2, 16, rng);
        MutationResult result = op.addLayer(spec);

        assertThat(result).isInstanceOf(MutationResult.AddLayer.class);
        MutationResult.AddLayer add = (MutationResult.AddLayer) result;
        assertThat(add.index()).isBetween(0, spec.layerCount());
        assertThat(add.layer().size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void addLayerShouldIncreaseSize() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 8, Activation.RELU, 0)));
        MutationResult.AddLayer add = (MutationResult.AddLayer) op.addLayer(spec);
        ArchitectureSpec mutated = spec.withMutation(add);

        assertThat(mutated.layerCount()).isEqualTo(2);
    }

    // ─── REMOVE_LAYER ───

    @Test
    void removeLayerShouldCreateValidMutation() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 16, rng);
        MutationResult result = op.removeLayer(spec);

        assertThat(result).isInstanceOf(MutationResult.RemoveLayer.class);
        assertThat(((MutationResult.RemoveLayer) result).index()).isBetween(0, 2);
    }

    @Test
    void removeLayerShouldDecreaseSize() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 8, Activation.RELU, 0),
                new LayerSpec(LayerType.DENSE, 16, Activation.SIGMOID, 0)));
        MutationResult.RemoveLayer rem = (MutationResult.RemoveLayer) op.removeLayer(spec);
        ArchitectureSpec mutated = spec.withMutation(rem);

        assertThat(mutated.layerCount()).isEqualTo(1);
    }

    // ─── CHANGE_SIZE ───

    @Test
    void changeSizeShouldCreateValidMutation() {
        ArchitectureSpec spec = ArchitectureSpec.random(2, 32, rng);
        MutationResult result = op.changeSize(spec);

        assertThat(result).isInstanceOf(MutationResult.ChangeSize.class);
        MutationResult.ChangeSize cs = (MutationResult.ChangeSize) result;
        assertThat(cs.index()).isBetween(0, 1);
        assertThat(cs.newSize()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void changeSizeShouldModifyLayer() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0)));
        MutationResult.ChangeSize cs = (MutationResult.ChangeSize) op.changeSize(spec);
        ArchitectureSpec mutated = spec.withMutation(cs);

        assertThat(mutated.layers().get(0).size()).isNotEqualTo(16);
    }

    // ─── CHANGE_ACTIVATION ───

    @Test
    void changeActivationShouldCreateValidMutation() {
        ArchitectureSpec spec = ArchitectureSpec.random(2, 16, rng);
        MutationResult result = op.changeActivation(spec);

        assertThat(result).isInstanceOf(MutationResult.ChangeActivation.class);
    }

    @Test
    void changeActivationShouldDifferFromOriginal() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 8, Activation.RELU, 0)));
        MutationResult.ChangeActivation ca = (MutationResult.ChangeActivation) op.changeActivation(spec);
        ArchitectureSpec mutated = spec.withMutation(ca);

        assertThat(mutated.layers().get(0).activation()).isNotEqualTo(Activation.RELU);
    }

    // ─── CHANGE_LAYER_TYPE ───

    @Test
    void changeLayerTypeShouldCreateValidMutation() {
        ArchitectureSpec spec = ArchitectureSpec.random(2, 16, rng);
        MutationResult result = op.changeLayerType(spec);

        assertThat(result).isInstanceOf(MutationResult.ChangeLayerType.class);
    }

    @Test
    void changeLayerTypeShouldDifferFromOriginal() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 8, Activation.RELU, 0)));
        MutationResult.ChangeLayerType clt = (MutationResult.ChangeLayerType) op.changeLayerType(spec);
        ArchitectureSpec mutated = spec.withMutation(clt);

        assertThat(mutated.layers().get(0).type()).isNotEqualTo(LayerType.DENSE);
    }

    // ─── Random Mutation ───

    @RepeatedTest(50)
    void randomMutateShouldProduceValidMutation() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 32, rng);
        MutationResult result = op.randomMutate(spec);

        assertThat(result).isNotNull();
        // Apply should not throw
        ArchitectureSpec mutated = spec.withMutation(result);
        assertThat(mutated).isNotNull();
    }

    @Test
    void randomMutateOnEmptyShouldAddLayer() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of());
        MutationResult result = op.randomMutate(spec);

        assertThat(result).isInstanceOf(MutationResult.AddLayer.class);
    }

    // ─── LLM-Guided Mutation ───

    @Test
    void llmGuidedMutateShouldAcceptValidSuggestion() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 32, rng);
        MutationResult suggestion = new MutationResult.ChangeSize(0, 16);

        MutationResult result = op.llmGuidedMutate(spec, suggestion);

        assertThat(result).isEqualTo(suggestion);
    }

    @Test
    void llmGuidedMutateShouldFallbackOnNull() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 32, rng);

        MutationResult result = op.llmGuidedMutate(spec, null);

        assertThat(result).isNotNull();
        assertThat(result).isNotInstanceOf(MutationResult.NoOp.class);
    }

    @Test
    void llmGuidedMutateShouldFallbackOnInvalidSuggestion() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 8, Activation.RELU, 0)));
        // Invalid: index out of bounds
        MutationResult invalid = new MutationResult.RemoveLayer(5);

        MutationResult result = op.llmGuidedMutate(spec, invalid);

        assertThat(result).isNotNull();
    }

    @Test
    void llmGuidedMutateShouldFallbackOnNoOp() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 32, rng);

        MutationResult result = op.llmGuidedMutate(spec, new MutationResult.NoOp());

        assertThat(result).isNotNull();
        assertThat(result).isNotInstanceOf(MutationResult.NoOp.class);
    }

    // ─── Validation ───

    @Test
    void isValidMutationShouldAcceptValidAdd() {
        ArchitectureSpec spec = ArchitectureSpec.random(2, 16, rng);
        MutationResult add = new MutationResult.AddLayer(
                new LayerSpec(LayerType.DENSE, 8, Activation.RELU, 0), 1);

        assertThat(op.isValidMutation(spec, add)).isTrue();
    }

    @Test
    void isValidMutationShouldRejectOutOfBoundsRemove() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 8, Activation.RELU, 0)));
        MutationResult rem = new MutationResult.RemoveLayer(5);

        assertThat(op.isValidMutation(spec, rem)).isFalse();
    }

    @Test
    void isValidMutationShouldRejectInvalidSize() {
        ArchitectureSpec spec = ArchitectureSpec.random(2, 16, rng);
        MutationResult cs = new MutationResult.ChangeSize(0, 0);

        assertThat(op.isValidMutation(spec, cs)).isFalse();
    }

    @Test
    void isValidMutationShouldAcceptNoOp() {
        ArchitectureSpec spec = ArchitectureSpec.random(2, 16, rng);
        assertThat(op.isValidMutation(spec, new MutationResult.NoOp())).isTrue();
    }

    // ─── Available Types ───

    @Test
    void availableTypesShouldReturnAll() {
        assertThat(op.availableTypes()).hasSize(5);
        assertThat(op.availableTypes()).contains(
                MutationOperator.MutationType.ADD_LAYER,
                MutationOperator.MutationType.REMOVE_LAYER,
                MutationOperator.MutationType.CHANGE_SIZE,
                MutationOperator.MutationType.CHANGE_ACTIVATION,
                MutationOperator.MutationType.CHANGE_LAYER_TYPE);
    }
}
