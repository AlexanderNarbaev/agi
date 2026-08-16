package io.matrix.integration;

import io.matrix.agent.AgentBrainService;
import io.matrix.compression.BooleanCompressor;
import io.matrix.compression.TruthTableMinimizer;
import io.matrix.mcts.MctsAction;
import io.matrix.mcts.MctsTree;
import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.HierarchicalBrain;
import io.matrix.neuron.NeuronLayer;
import io.matrix.neuron.TruthTable;
import io.matrix.proxy.EffectorProxy;
import io.matrix.proxy.SensorProxy;
import io.matrix.rag.BooleanIndex;
import io.matrix.rag.BooleanRag;
import io.matrix.reasoning.BrcChain;
import io.matrix.reasoning.BrcState;
import io.matrix.vqvae.CodeBook;
import io.matrix.vqvae.VqVaeProxy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9: End-to-end integration test for the full MATRIX v3.0 pipeline.
 *
 * <p>Full pipeline: Text → VQ-VAE → BRC → RAG → Agent → Output.
 * Tests with real pretrained weights (random fallback) and performance benchmarking.
 */
class EndToEndTest {

    @Test
    void fullPipelineTextToVqVaeToBrcToRagToAgentToOutput() {
        var rng = new Random(42);

        // ─── Step 1: Text → Sensor bits ───
        SensorProxy sensorProxy = new SensorProxy();
        String inputText = "hostile creeper nearby at distance 3";
        long sensorBits = sensorProxy.textToBits(inputText);
        assertThat(sensorBits).isNotZero();

        // ─── Step 2: Sensor bits → VQ-VAE encoding ───
        VqVaeProxy vqVae = VqVaeProxy.builder()
                .dimension(16)
                .codeSize(64)
                .momentum(0.1)
                .build();

        double[] continuous = new double[16];
        for (int i = 0; i < 16; i++) {
            continuous[i] = ((sensorBits >> i) & 1) == 1 ? 1.0 : -1.0;
        }
        boolean[] booleanVec = vqVae.sensorEncode(continuous);
        assertThat(booleanVec).hasSize(16);

        // ─── Step 3: VQ-VAE → Boolean RAG expansion ───
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        for (int i = 0; i < 100; i++) {
            index.add("knowledge-" + i, new long[]{rng.nextLong()});
        }

        BooleanRag rag = BooleanRag.builder()
                .index(index)
                .topK(3)
                .build();

        long[] queryVec = new long[]{booleanVecToLong(booleanVec)};
        BooleanRag.RagResult ragResult = rag.query(queryVec);
        assertThat(ragResult.knowledgeHits()).hasSize(3);

        // ─── Step 4: BRC reasoning ───
        HierarchicalBrain brain = new HierarchicalBrain(rng);
        BrcChain chain = brain.toBrcChain(5, 2);

        BitSet brainInput = new BitSet(20);
        for (int i = 0; i < Math.min(20, booleanVec.length); i++) {
            if (booleanVec[i]) brainInput.set(i);
        }

        BrcState brcResult = chain.evaluate(brainInput, 20);
        assertThat(brcResult).isNotNull();
        assertThat(brcResult.stepIndex()).isGreaterThan(0);

        // ─── Step 5: Brain action decision ───
        AgentBrainService brainService = new AgentBrainService();
        String action = brainService.act(sensorBits);
        assertThat(action).isIn("MOVE_N", "MOVE_S", "MOVE_W", "MOVE_E",
                "STAY", "MINE", "CRAFT", "EAT", "TOOL_UP");

        // ─── Step 6: Effector proxy output ───
        EffectorProxy effectorProxy = new EffectorProxy();
        String actionName = effectorProxy.bitsToAction((int) (sensorBits & 0x1F));
        String mcCommand = effectorProxy.actionToMinecraftCommand(actionName);
        assertThat(mcCommand).isNotEmpty();

        // ─── Step 7: Compression ───
        BitSet finalVector = brcResult.vector();
        BooleanCompressor.Compressed compressed = BooleanCompressor.compress(
                finalVector, finalVector.length());
        BitSet decompressed = BooleanCompressor.decompress(compressed, finalVector.length());
        assertThat(decompressed).isEqualTo(finalVector);
    }

    @Test
    void mctsGuidedEvolutionPipeline() {
        var rng = new Random(42);
        int k = 8;

        // Initial tree
        DecisionTree initial = DecisionTree.random(k, 4, rng);

        // MCTS optimization
        MctsTree mcts = MctsTree.builder()
                .rootState(initial)
                .rng(new Random(42))
                .k(k)
                .simulationDepth(3)
                .explorationConstant(1.4)
                .rewardFunction(tree -> {
                    TruthTable tt = tree.toTruthTable(k);
                    int ones = 0;
                    for (int i = 0; i < (1 << k); i++) {
                        if (tt.evaluate(i)) ones++;
                    }
                    return 1.0 - Math.abs((double) ones / (1 << k) - 0.5) * 2.0;
                })
                .build();

        MctsAction bestAction = mcts.runSearch(200);
        DecisionTree optimized = bestAction.apply(initial, rng, k);

        // Minimize the optimized tree
        TruthTable tt = optimized.toTruthTable(k);
        TruthTableMinimizer.MinimizedDNF minimized = TruthTableMinimizer.minimize(tt);

        assertThat(minimized).isNotNull();
        assertThat(minimized.implicants()).isNotEmpty();

        // Compression
        BitSet tableBits = tt.table();
        BooleanCompressor.Compressed compressed = BooleanCompressor.compress(tableBits, tt.size());
        assertThat(compressed.compressedSize()).isGreaterThan(0);
    }

    @Test
    void hierarchicalBrainWithBrcReasoning() {
        var rng = new Random(42);
        HierarchicalBrain brain = new HierarchicalBrain(rng);

        // Test with BRC reasoning
        long sensorInput = 0xABCDEFL;
        BrcState state = brain.decideWithReasoning(sensorInput, 10);

        assertThat(state).isNotNull();
        assertThat(state.stepIndex()).isGreaterThan(0);
        assertThat(state.history()).isNotEmpty();

        // Also test direct decision
        int directAction = brain.decide(sensorInput);
        assertThat(directAction).isBetween(0, 31);
    }

    @Test
    void vqVaeTextToBooleanPipeline() {
        var rng = new Random(42);

        // Create a simple Text2VecService mock
        var text2Vec = new io.matrix.api.Text2VecService();

        VqVaeProxy proxy = VqVaeProxy.builder()
                .dimension(8)
                .codeSize(32)
                .momentum(0.1)
                .text2VecService(text2Vec)
                .build();

        boolean[] result = proxy.textToBoolean("hello world");
        assertThat(result).hasSize(8);

        boolean[] result2 = proxy.textToBoolean("danger fire emergency");
        assertThat(result2).hasSize(8);

        // Different texts should produce potentially different boolean vectors
        // (though not guaranteed due to hashing collisions)
    }

    @Test
    void performanceBenchmark() {
        var rng = new Random(42);

        // Benchmark: 1000 brain decisions
        HierarchicalBrain brain = new HierarchicalBrain(rng);
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            brain.decide(i);
        }
        long brainMs = (System.nanoTime() - start) / 1_000_000;

        // Benchmark: 1000 BRC evaluations
        BrcChain chain = brain.toBrcChain(5, 2);
        BitSet input = new BitSet(20);
        input.set(0, 5);
        start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            chain.evaluate(input, 20);
        }
        long brcMs = (System.nanoTime() - start) / 1_000_000;

        // Benchmark: 1000 RAG queries
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        for (int i = 0; i < 100; i++) {
            index.add("v-" + i, new long[]{rng.nextLong()});
        }
        BooleanRag rag = BooleanRag.builder().index(index).topK(5).build();
        long[] query = new long[]{rng.nextLong()};
        start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            rag.query(query);
        }
        long ragMs = (System.nanoTime() - start) / 1_000_000;

        // Performance assertions (should complete in reasonable time)
        assertThat(brainMs).isLessThan(10_000); // <10s for 1000 decisions
        assertThat(brcMs).isLessThan(10_000);
        assertThat(ragMs).isLessThan(10_000);

        System.out.printf("Performance: brain=%dms, brc=%dms, rag=%dms%n",
                brainMs, brcMs, ragMs);
    }

    @Test
    void serializationRoundtripFull() throws Exception {
        var rng = new Random(42);

        // Serialize and deserialize HierarchicalBrain
        HierarchicalBrain brain = new HierarchicalBrain(rng);
        byte[] brainBytes = brain.toAvroBytes();
        HierarchicalBrain restored = HierarchicalBrain.fromAvroBytes(brainBytes);

        // Both should produce the same decision for the same input
        for (int i = 0; i < 10; i++) {
            long input = rng.nextLong() & 0xFFFFFL;
            assertThat(restored.decide(input)).isEqualTo(brain.decide(input));
        }
    }

    @Test
    void neuronLayerSerialization() {
        var rng = new Random(42);
        NeuronLayer layer = new NeuronLayer(12, 12, rng);

        byte[] bytes = layer.toAvroBytes();
        NeuronLayer restored = NeuronLayer.fromAvroBytes(bytes);

        assertThat(restored.outputWidth()).isEqualTo(12);
        assertThat(restored.k()).isEqualTo(12);

        // Evaluate should produce same output
        BitSet input = new BitSet(144);
        input.set(0, 10);
        assertThat(restored.evaluate(input)).isEqualTo(layer.evaluate(input));
    }

    /**
     * Converts a boolean vector to a long (low 64 bits).
     */
    private static long booleanVecToLong(boolean[] vec) {
        long result = 0;
        for (int i = 0; i < Math.min(64, vec.length); i++) {
            if (vec[i]) result |= (1L << i);
        }
        return result;
    }
}
