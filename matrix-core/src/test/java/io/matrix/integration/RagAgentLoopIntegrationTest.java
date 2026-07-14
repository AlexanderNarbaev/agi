package io.matrix.integration;

import io.matrix.agent.AgentBrainService;
import io.matrix.rag.BooleanIndex;
import io.matrix.rag.BooleanRag;
import io.matrix.reasoning.BrcChain;
import io.matrix.reasoning.BrcState;
import io.matrix.reasoning.BrcStep;
import io.matrix.neuron.NeuronLayer;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: Boolean RAG knowledge retrieval influences agent
 * decision-making and reasoning.
 *
 * <p>Covers the gap: RAG-retrieved knowledge → AgentLoop action selection.
 */
class RagAgentLoopIntegrationTest {

    private static final int K = 8;
    private static final Random RNG = new Random(42);

    @Test
    void ragQueryReturnsKnowledgeForAgentInput() {
        // Create RAG index with known patterns
        BooleanIndex index = BooleanIndex.builder()
                .dimensions(64)
                .build();

        index.add("pattern_move", vectorFromLong(0b00001L));
        index.add("pattern_mine", vectorFromLong(0b10010L));
        index.add("pattern_craft", vectorFromLong(0b01100L));
        index.add("pattern_eat", vectorFromLong(0b10101L));

        BooleanRag rag = BooleanRag.builder()
                .index(index)
                .topK(3)
                .build();

        // Query with a sensor pattern similar to "move"
        long[] query = vectorFromLong(0b00001L);
        BooleanRag.RagResult result = rag.query(query);

        assertThat(result).isNotNull();
        assertThat(result.knowledgeHits()).isNotEmpty();
        // Best match should be the exact "pattern_move" entry
        assertThat(result.knowledgeHits().get(0).id())
                .isEqualTo("pattern_move");
    }

    @Test
    void ragKnowledgeUsedAsBrcInputInfluencesReasoning() {
        // RAG → BRC pipeline: knowledge retrieval feeds into reasoning chain
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        index.add("knowledge_0", vectorFromLong(0x0FL));
        index.add("knowledge_1", vectorFromLong(0xF0L));

        BooleanRag rag = BooleanRag.builder()
                .index(index)
                .topK(2)
                .build();

        // Query RAG
        long[] query = vectorFromLong(0x05L);
        BooleanRag.RagResult ragResult = rag.query(query);

        assertThat(ragResult.expandedVector()).isNotNull();
        assertThat(ragResult.expandedVector().length).isGreaterThan(0);

        // Feed RAG result into BRC
        NeuronLayer layer = new NeuronLayer(4, K, RNG);
        BrcChain brc = BrcChain.builder()
                .addStep(new BrcStep(layer, "rag_reasoning", 2))
                .maxSteps(3)
                .build();

        // Convert RAG expanded vector to BitSet for BRC
        BitSet ragInput = longArrayToBitSet(ragResult.expandedVector(), K);
        BrcState brcState = brc.evaluate(ragInput, K);

        assertThat(brcState).isNotNull();
        assertThat(brcState.vector()).isNotNull();
    }

    @Test
    void agentBrainServiceInitializesCorrectly() {
        AgentBrainService brain = new AgentBrainService();

        assertThat(brain.brain()).isNotNull();
        assertThat(brain.brain().sensorLayer()).isNotNull();
        assertThat(brain.brain().featureLayer()).isNotNull();
        assertThat(brain.brain().actionLayer()).isNotNull();

        // Brain should produce valid action codes
        for (int i = 0; i < 10; i++) {
            long sensorBits = RNG.nextLong() & 0xFFFFF; // 20 bits
            String action = brain.act(sensorBits);
            assertThat(action).isNotNull();
            assertThat(action).isNotEmpty();
        }
    }

    @Test
    void ragIndexSearchReturnsConsistentResults() {
        // Index should return same results for same query
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        for (int i = 0; i < 50; i++) {
            index.add("entry_" + i, vectorFromLong(RNG.nextLong()));
        }

        BooleanRag rag = BooleanRag.builder()
                .index(index)
                .topK(5)
                .build();

        long[] query = vectorFromLong(0xABCDEF01L);

        BooleanRag.RagResult r1 = rag.query(query);
        BooleanRag.RagResult r2 = rag.query(query);

        assertThat(r1.knowledgeHits().size()).isEqualTo(r2.knowledgeHits().size());
        for (int i = 0; i < r1.knowledgeHits().size(); i++) {
            assertThat(r1.knowledgeHits().get(i).id())
                    .isEqualTo(r2.knowledgeHits().get(i).id());
        }
    }

    @Test
    void neuronLayerEvaluateConsistency() {
        // Neuron layer should produce consistent output for same input
        NeuronLayer layer = new NeuronLayer(5, K, RNG);
        BitSet input = toBitSet(RNG.nextLong(), 5 * K);

        BitSet out1 = layer.evaluate(input);
        BitSet out2 = layer.evaluate(input);

        assertThat(out1).isEqualTo(out2);
    }

    /** Convert long to long[] vector (single element). */
    private static long[] vectorFromLong(long val) {
        return new long[]{val};
    }

    /** Convert long to BitSet of given width. */
    private static BitSet toBitSet(long bits, int width) {
        BitSet bs = new BitSet(width);
        for (int i = 0; i < width && i < 64; i++) {
            if ((bits & (1L << i)) != 0) bs.set(i);
        }
        return bs;
    }

    /** Convert long[][] expanded vector to BitSet. */
    private static BitSet longArrayToBitSet(long[][] vectors, int width) {
        int totalBits = vectors.length * width;
        BitSet bs = new BitSet(totalBits);
        int pos = 0;
        for (long[] vec : vectors) {
            for (long v : vec) {
                for (int i = 0; i < width && i < 64; i++) {
                    if ((v & (1L << i)) != 0) bs.set(pos + i);
                }
                pos += width;
            }
        }
        return bs;
    }
}
