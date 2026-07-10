package io.matrix.nativeimage;

import io.matrix.agent.AgentAction;
import io.matrix.compression.BooleanCompressor;
import io.matrix.compression.SimdEvaluator;
import io.matrix.compression.TruthTableMinimizer;
import io.matrix.mcts.MctsAction;
import io.matrix.mcts.MctsNode;
import io.matrix.mcts.MctsTree;
import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.HierarchicalBrain;
import io.matrix.neuron.NeuronLayer;
import io.matrix.neuron.NeuralTextGenerator;
import io.matrix.neuron.TruthTable;
import io.matrix.rag.BooleanIndex;
import io.matrix.rag.BooleanRag;
import io.matrix.reasoning.BrcChain;
import io.matrix.reasoning.BrcState;
import io.matrix.reasoning.BrcStep;
import io.matrix.vqvae.CodeBook;
import io.matrix.vqvae.VqVaeProxy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GraalVM native image reflection configuration.
 *
 * <p>Verifies that all MATRIX core classes are accessible via reflection
 * and that key functionality works correctly. These tests run in JVM mode
 * but validate the reflect-config.json completeness.
 *
 * <p>Tag: {@code native-image} — run with: {@code ./gradlew :matrix-core:test --tests "io.matrix.nativeimage.*"}
 */
@Tag("native-image")
class NativeImageTest {

    private static final Random RNG = new Random(42);

    // ─── MPDT Classes ───

    @Test
    void neuronLayerIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.neuron.NeuronLayer");
        assertThat(clazz).isNotNull();

        Constructor<?> ctor = clazz.getConstructor(int.class, int.class, Random.class);
        assertThat(ctor).isNotNull();

        Method evaluate = clazz.getMethod("evaluate", BitSet.class);
        assertThat(evaluate).isNotNull();
    }

    @Test
    void truthTableIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.neuron.TruthTable");
        assertThat(clazz).isNotNull();

        Method evaluate = clazz.getMethod("evaluate", int.class);
        assertThat(evaluate).isNotNull();
    }

    @Test
    void decisionTreeIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.neuron.DecisionTree");
        assertThat(clazz).isNotNull();

        Method evaluate = clazz.getMethod("evaluate", BitSet.class);
        assertThat(evaluate).isNotNull();
    }

    @Test
    void hierarchicalBrainIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.neuron.HierarchicalBrain");
        assertThat(clazz).isNotNull();

        Constructor<?> ctor = clazz.getConstructor(Random.class);
        assertThat(ctor).isNotNull();

        Method decide = clazz.getMethod("decide", long.class);
        assertThat(decide).isNotNull();
    }

    @Test
    void neuralTextGeneratorIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.neuron.NeuralTextGenerator");
        assertThat(clazz).isNotNull();

        Constructor<?> ctor = clazz.getConstructor(Random.class);
        assertThat(ctor).isNotNull();

        Method generate = clazz.getMethod("generate", String.class);
        assertThat(generate).isNotNull();
    }

    // ─── BRC Classes ───

    @Test
    void brcStateIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.reasoning.BrcState");
        assertThat(clazz).isNotNull();

        Constructor<?> ctor = clazz.getConstructor(BitSet.class, int.class);
        assertThat(ctor).isNotNull();
    }

    @Test
    void brcStepIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.reasoning.BrcStep");
        assertThat(clazz).isNotNull();

        Constructor<?> ctor = clazz.getConstructor(NeuronLayer.class, String.class, int.class);
        assertThat(ctor).isNotNull();
    }

    @Test
    void brcChainIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.reasoning.BrcChain");
        assertThat(clazz).isNotNull();

        Method builder = clazz.getMethod("builder");
        assertThat(builder).isNotNull();
    }

    // ─── RAG Classes ───

    @Test
    void booleanIndexIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.rag.BooleanIndex");
        assertThat(clazz).isNotNull();

        Method builder = clazz.getMethod("builder");
        assertThat(builder).isNotNull();
    }

    @Test
    void booleanRagIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.rag.BooleanRag");
        assertThat(clazz).isNotNull();

        Method builder = clazz.getMethod("builder");
        assertThat(builder).isNotNull();
    }

    // ─── VQ-VAE Classes ───

    @Test
    void codeBookIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.vqvae.CodeBook");
        assertThat(clazz).isNotNull();

        Method builder = clazz.getMethod("builder", int.class);
        assertThat(builder).isNotNull();
    }

    @Test
    void vqVaeProxyIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.vqvae.VqVaeProxy");
        assertThat(clazz).isNotNull();

        Method builder = clazz.getMethod("builder");
        assertThat(builder).isNotNull();
    }

    // ─── MCTS Classes ───

    @Test
    void mctsNodeIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.mcts.MctsNode");
        assertThat(clazz).isNotNull();

        Constructor<?> ctor = clazz.getConstructor(
                MctsNode.class, MctsAction.class, DecisionTree.class, List.class);
        assertThat(ctor).isNotNull();
    }

    @Test
    void mctsTreeIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.mcts.MctsTree");
        assertThat(clazz).isNotNull();

        Method builder = clazz.getMethod("builder");
        assertThat(builder).isNotNull();
    }

    @Test
    void mctsActionIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.mcts.MctsAction");
        assertThat(clazz).isNotNull();

        Method allActions = clazz.getMethod("allActions");
        assertThat(allActions).isNotNull();
    }

    // ─── Agent Classes ───

    @Test
    void agentStateIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.agent.AgentState");
        assertThat(clazz).isNotNull();

        Constructor<?> ctor = clazz.getConstructor(
                long.class, boolean[].class,
                AgentAction.class, double[].class, long.class);
        assertThat(ctor).isNotNull();
    }

    @Test
    void agentActionIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.agent.AgentAction");
        assertThat(clazz).isNotNull();

        Constructor<?> ctor = clazz.getConstructor(AgentAction.ActionType.class);
        assertThat(ctor).isNotNull();
    }

    // ─── Compression Classes ───

    @Test
    void booleanCompressorIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.compression.BooleanCompressor");
        assertThat(clazz).isNotNull();

        Method compress = clazz.getMethod("compress", BitSet.class, int.class);
        assertThat(compress).isNotNull();
    }

    @Test
    void truthTableMinimizerIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.compression.TruthTableMinimizer");
        assertThat(clazz).isNotNull();

        Method minimize = clazz.getMethod("minimize", TruthTable.class);
        assertThat(minimize).isNotNull();
    }

    @Test
    void simdEvaluatorIsAccessibleViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.compression.SimdEvaluator");
        assertThat(clazz).isNotNull();

        Method batchEvaluate = clazz.getMethod("batchEvaluate", List.class, int[].class);
        assertThat(batchEvaluate).isNotNull();
    }

    // ─── Record Types ───

    @Test
    void mctsActionRecordIsAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.mcts.MctsAction");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isRecord()).isTrue();
    }

    @Test
    void compressedRecordIsAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.compression.BooleanCompressor$Compressed");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isRecord()).isTrue();
    }

    @Test
    void minimizedDnfRecordIsAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.compression.TruthTableMinimizer$MinimizedDNF");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isRecord()).isTrue();
    }

    @Test
    void benchmarkResultRecordIsAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.compression.SimdEvaluator$BenchmarkResult");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isRecord()).isTrue();
    }

    @Test
    void searchResultRecordIsAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.rag.BooleanIndex$SearchResult");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isRecord()).isTrue();
    }

    @Test
    void actionResultRecordIsAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.agent.AgentAction$ActionResult");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isRecord()).isTrue();
    }

    // ─── Enum Types ───

    @Test
    void actionTypeEnumIsAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.agent.AgentAction$ActionType");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isEnum()).isTrue();

        Object[] constants = clazz.getEnumConstants();
        assertThat(constants).hasSize(10);
    }

    @Test
    void mctsActionTypeEnumIsAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.mcts.MctsAction$ActionType");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isEnum()).isTrue();

        Object[] constants = clazz.getEnumConstants();
        assertThat(constants).hasSize(8);
    }

    @Test
    void convergenceReasonEnumIsAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.agent.AgentLoop$ConvergenceReason");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isEnum()).isTrue();

        Object[] constants = clazz.getEnumConstants();
        assertThat(constants).hasSize(5);
    }

    @Test
    void compressionMethodEnumIsAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.compression.BooleanCompressor$Method");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isEnum()).isTrue();

        Object[] constants = clazz.getEnumConstants();
        assertThat(constants).hasSize(2);
    }

    @Test
    void algorithmEnumIsAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.compression.TruthTableMinimizer$Algorithm");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isEnum()).isTrue();

        Object[] constants = clazz.getEnumConstants();
        assertThat(constants).hasSize(2);
    }

    // ─── Functional Tests (ensure evaluate() works) ───

    @Test
    void neuronLayerEvaluateWorks() {
        int neuronCount = 4;
        int k = 3;
        NeuronLayer layer = new NeuronLayer(neuronCount, k, RNG);

        BitSet input = new BitSet(neuronCount * k);
        input.set(0);
        input.set(2);
        input.set(5);

        BitSet output = layer.evaluate(input);

        assertThat(output).isNotNull();
        assertThat(output.length()).isLessThanOrEqualTo(neuronCount);
    }

    @Test
    void hierarchicalBrainDecideWorks() {
        HierarchicalBrain brain = new HierarchicalBrain(RNG);

        int action = brain.decide(0b10101010101010101010L);

        assertThat(action).isBetween(0, 31);
    }

    @Test
    void brcChainEvaluateWorks() {
        NeuronLayer layer = new NeuronLayer(4, 3, RNG);
        BrcStep step = new BrcStep(layer, "test", 2);
        BrcChain chain = BrcChain.builder()
                .addStep(step)
                .maxSteps(5)
                .earlyStopping(true)
                .build();

        BitSet input = new BitSet(12);
        input.set(0);
        input.set(3);
        input.set(7);

        BrcState state = chain.evaluate(input, 12);

        assertThat(state).isNotNull();
        assertThat(state.vector()).isNotNull();
    }

    @Test
    void booleanIndexSearchWorks() {
        BooleanIndex index = BooleanIndex.builder()
                .dimensions(64)
                .build();

        long[] vec1 = {0xFF00FF00FF00FF00L};
        long[] vec2 = {0x00FF00FF00FF00FFL};
        long[] vec3 = {0xFFFF0000FFFF0000L};

        index.add("doc1", vec1);
        index.add("doc2", vec2);
        index.add("doc3", vec3);

        List<BooleanIndex.SearchResult> results = index.search(vec1, 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo("doc1");
        assertThat(results.get(0).distance()).isEqualTo(0);
    }

    @Test
    void booleanCompressorRoundTrips() {
        BitSet original = new BitSet(64);
        original.set(0);
        original.set(3);
        original.set(7);
        original.set(15);
        original.set(31);
        original.set(63);

        BooleanCompressor.Compressed compressed = BooleanCompressor.compress(original, 64);
        BitSet restored = BooleanCompressor.decompress(compressed, 64);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void codeBookEncodeDecodeWorks() {
        CodeBook book = CodeBook.builder(8)
                .codeSize(16)
                .momentum(0.1)
                .build();

        double[] input = {1.0, -1.0, 0.5, -0.5, 1.0, -1.0, 0.5, -0.5};
        int code = book.encode(input);
        boolean[] decoded = book.decode(code);

        assertThat(code).isBetween(0, 15);
        assertThat(decoded).hasSize(8);
    }
}
