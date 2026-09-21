package io.matrix.nas;

import io.matrix.nas.ArchitectureSpec.Activation;
import io.matrix.nas.ArchitectureSpec.LayerSpec;
import io.matrix.nas.ArchitectureSpec.LayerType;
import io.matrix.nas.ArchitectureSpec.MutationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class LlmArchitectureOptimizerTest {

    private final Random rng = new Random(42);
    private final MutationOperator mutationOperator = new MutationOperator(rng);

    // ─── CRISPE Prompt Generation ───

    @Test
    void crispePromptShouldContainAllSections() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);
        ArchitectureSpec spec = ArchitectureSpec.random(3, 16, rng);

        String prompt = optimizer.generateCrispePrompt(spec, List.of(100L, 150L, 200L), "accuracy");

        assertThat(prompt).contains("Neural Architecture Search expert");
        assertThat(prompt).contains("ROLE:");
        assertThat(prompt).contains("CURRENT ARCHITECTURE:");
        assertThat(prompt).contains("TASK:");
        assertThat(prompt).contains("APPROACH:");
        assertThat(prompt).contains("RESPONSE FORMAT");
        assertThat(prompt).contains("accuracy");
    }

    @Test
    void crispePromptShouldIncludeFitnessHistory() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);
        ArchitectureSpec spec = ArchitectureSpec.random(2, 8, rng);

        String prompt = optimizer.generateCrispePrompt(spec, List.of(100L, 200L, 300L), "latency");

        assertThat(prompt).contains("Fitness history");
        assertThat(prompt).contains("100");
        assertThat(prompt).contains("200");
        assertThat(prompt).contains("300");
    }

    @Test
    void crispePromptShouldIncludeArchitectureDetails() {
        var layers = List.of(
                new LayerSpec(LayerType.DENSE, 16, Activation.RELU, 0));
        ArchitectureSpec spec = ArchitectureSpec.of(layers);
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);

        String prompt = optimizer.generateCrispePrompt(spec, List.of(), "memory");

        assertThat(prompt).contains("DENSE");
        assertThat(prompt).contains("size=16");
        assertThat(prompt).contains("Complexity:");
        assertThat(prompt).contains("Total neurons:");
    }

    @Test
    void simplePromptShouldBeShorter() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);
        ArchitectureSpec spec = ArchitectureSpec.random(2, 8, rng);

        String simple = optimizer.generateSimplePrompt(spec);
        String crispe = optimizer.generateCrispePrompt(spec, List.of(), "accuracy");

        assertThat(simple.length()).isLessThan(crispe.length());
    }

    // ─── Response Parsing ───

    @Test
    void parseResponseShouldExtractAddLayer() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);

        String response = "{\"mutation\": \"ADD_LAYER\", \"layer_index\": 1, \"parameter\": \"DENSE:16:RELU\"}";
        MutationResult result = optimizer.parseResponse(response);

        assertThat(result).isInstanceOf(MutationResult.AddLayer.class);
        MutationResult.AddLayer add = (MutationResult.AddLayer) result;
        assertThat(add.index()).isEqualTo(1);
        assertThat(add.layer().type()).isEqualTo(LayerType.DENSE);
        assertThat(add.layer().size()).isEqualTo(16);
        assertThat(add.layer().activation()).isEqualTo(Activation.RELU);
    }

    @Test
    void parseResponseShouldExtractRemoveLayer() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);

        String response = "{\"mutation\": \"REMOVE_LAYER\", \"layer_index\": 0}";
        MutationResult result = optimizer.parseResponse(response);

        assertThat(result).isInstanceOf(MutationResult.RemoveLayer.class);
        assertThat(((MutationResult.RemoveLayer) result).index()).isZero();
    }

    @Test
    void parseResponseShouldExtractChangeSize() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);

        String response = "{\"mutation\": \"CHANGE_SIZE\", \"layer_index\": 0, \"parameter\": \"32\"}";
        MutationResult result = optimizer.parseResponse(response);

        assertThat(result).isInstanceOf(MutationResult.ChangeSize.class);
        MutationResult.ChangeSize cs = (MutationResult.ChangeSize) result;
        assertThat(cs.index()).isZero();
        assertThat(cs.newSize()).isEqualTo(32);
    }

    @Test
    void parseResponseShouldExtractChangeActivation() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);

        String response = "{\"mutation\": \"CHANGE_ACTIVATION\", \"layer_index\": 1, \"parameter\": \"GELU\"}";
        MutationResult result = optimizer.parseResponse(response);

        assertThat(result).isInstanceOf(MutationResult.ChangeActivation.class);
        MutationResult.ChangeActivation ca = (MutationResult.ChangeActivation) result;
        assertThat(ca.index()).isEqualTo(1);
        assertThat(ca.newActivation()).isEqualTo(Activation.GELU);
    }

    @Test
    void parseResponseShouldExtractChangeLayerType() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);

        String response = "{\"mutation\": \"CHANGE_LAYER_TYPE\", \"layer_index\": 0, \"parameter\": \"ATTENTION\"}";
        MutationResult result = optimizer.parseResponse(response);

        assertThat(result).isInstanceOf(MutationResult.ChangeLayerType.class);
        assertThat(((MutationResult.ChangeLayerType) result).newType()).isEqualTo(LayerType.ATTENTION);
    }

    @Test
    void parseResponseShouldHandleMarkdownCodeBlock() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);

        String response = "Here is my suggestion:\n```json\n{\"mutation\": \"CHANGE_SIZE\", \"layer_index\": 0, \"parameter\": \"16\"}\n```\nThis should help.";
        MutationResult result = optimizer.parseResponse(response);

        assertThat(result).isInstanceOf(MutationResult.ChangeSize.class);
    }

    @Test
    void parseResponseShouldReturnNoOpForInvalidJson() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);

        MutationResult result = optimizer.parseResponse("this is not json");
        assertThat(result).isInstanceOf(MutationResult.NoOp.class);
    }

    @Test
    void parseResponseShouldReturnNoOpForNull() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);

        MutationResult result = optimizer.parseResponse(null);
        assertThat(result).isInstanceOf(MutationResult.NoOp.class);
    }

    @Test
    void parseResponseShouldReturnNoOpForEmpty() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);

        MutationResult result = optimizer.parseResponse("");
        assertThat(result).isInstanceOf(MutationResult.NoOp.class);
    }

    @Test
    void parseResponseShouldHandleUnknownMutationType() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(""), mutationOperator);

        String response = "{\"mutation\": \"UNKNOWN_TYPE\", \"layer_index\": 0}";
        MutationResult result = optimizer.parseResponse(response);

        assertThat(result).isInstanceOf(MutationResult.NoOp.class);
    }

    // ─── Async Suggestion ───

    @Test
    void suggestMutationShouldReturnValidResult() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.completedFuture(
                        "{\"mutation\": \"CHANGE_SIZE\", \"layer_index\": 0, \"parameter\": \"16\"}"),
                mutationOperator);
        ArchitectureSpec spec = ArchitectureSpec.random(3, 32, rng);

        MutationResult result = optimizer.suggestMutationSync(spec, List.of(100L), "accuracy");

        assertThat(result).isInstanceOf(MutationResult.ChangeSize.class);
    }

    @Test
    void suggestMutationShouldFallbackOnLlmFailure() {
        var optimizer = new LlmArchitectureOptimizer(
                prompt -> CompletableFuture.failedFuture(new RuntimeException("LLM unavailable")),
                mutationOperator);
        ArchitectureSpec spec = ArchitectureSpec.random(3, 32, rng);

        MutationResult result = optimizer.suggestMutationSync(spec, List.of(100L), "accuracy");

        // Should fallback to random mutation (not NoOp unless spec is empty)
        assertThat(result).isNotNull();
    }
}
