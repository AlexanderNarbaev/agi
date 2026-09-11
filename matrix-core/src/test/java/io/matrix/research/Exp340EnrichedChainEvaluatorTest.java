package io.matrix.research;

import io.matrix.imports.BooleanChainRunner;
import io.matrix.imports.ChainEnrichedOutput;
import io.matrix.imports.EnrichedChainEvaluator;
import io.matrix.imports.TruthTableLayer;
import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.Neurotransmitter;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RUN 340 — DESIGN-20 ChainEnrichedOutput + EnrichedChainEvaluator.
 *
 * <p>Acceptance criteria from DESIGN-20 §8:
 *  - ChainEnrichedOutput exposes magnitude/chemical/tag per layer
 *  - EnrichedChainEvaluator wraps BooleanChainRunner
 *  - Pure function: same input → same output (CONSTITUTION I)
 *  - Qwen 24-layer chain produces enriched output with magnitude +
 *    chemical vector per neuron
 */
class Exp340EnrichedChainEvaluatorTest {

    @Test
    void evaluatorWrapsSmallChain() {
        // Build a small chain manually (2 layers, 4 neurons each)
        BooleanChainRunner chain = buildSmallChain(2, 4, 0xABCDL);
        EnrichedChainEvaluator eval = new EnrichedChainEvaluator(chain);

        boolean[] input = new boolean[8];
        for (int i = 0; i < input.length; i++) input[i] = (i & 1) == 0;

        ChainEnrichedOutput out = eval.evaluateEnriched(input);

        assertThat(out).isNotNull();
        assertThat(out.bits()).isNotEmpty();
        assertThat(out.layerCount()).isEqualTo(2);
        assertThat(out.magnitudePerLayer()[0]).hasSize(4);
        assertThat(out.magnitudePerLayer()[1]).hasSize(4);
        assertThat(out.chemicalPerLayer()[0][0]).hasSize(EnrichedNeuron.CHEMICAL_DIM);
        assertThat(out.tagsPerLayer()[0][0]).isNotNull();
    }

    @Test
    void evaluatorDeterministic() {
        BooleanChainRunner chain = buildSmallChain(3, 8, 0xDEADBEEFL);
        EnrichedChainEvaluator eval = new EnrichedChainEvaluator(chain);

        boolean[] input = new boolean[]{true, false, true, false, true, true, false, true};

        ChainEnrichedOutput a = eval.evaluateEnriched(input);
        ChainEnrichedOutput b = eval.evaluateEnriched(input);

        assertThat(a.bits()).isEqualTo(b.bits());
        assertThat(a.meanMagnitude()).isEqualTo(b.meanMagnitude());
        assertThat(a.meanNovelty()).isEqualTo(b.meanNovelty());
        assertThat(a.meanCertainty()).isEqualTo(b.meanCertainty());
    }

    @Test
    void enrichedOutputOnRealQwenChain() throws Exception {
        // Load Qwen 24-layer chain (auto-skip if safetensors absent)
        BooleanChainRunner chain = loadQwenOrSkip();
        EnrichedChainEvaluator eval = new EnrichedChainEvaluator(chain);

        boolean[] input = new boolean[256];
        Random rng = new Random(0xCAFE);
        for (int i = 0; i < input.length; i++) input[i] = rng.nextBoolean();

        ChainEnrichedOutput out = eval.evaluateEnriched(input);

        assertThat(out.layerCount()).isEqualTo(24);
        assertThat(out.totalNeurons()).isGreaterThan(1000);
        assertThat(out.meanMagnitude()).isBetween(0.0, 1.0);
        assertThat(out.meanExcitation()).isBetween(0.0, 1.0);
        assertThat(out.meanNovelty()).isBetween(0.0, 1.0);
        assertThat(out.meanCertainty()).isBetween(0.0, 1.0);

        // Print summary
        System.out.printf("[Exp340] Qwen 24-layer enriched: totalNeurons=%d, " +
                "meanMag=%.4f, meanExc=%.4f, meanNov=%.4f, meanCert=%.4f%n",
                out.totalNeurons(), out.meanMagnitude(),
                out.meanExcitation(), out.meanNovelty(), out.meanCertainty());

        // Count neurotransmitter tags
        java.util.Map<Neurotransmitter, Integer> tagCounts = new java.util.HashMap<>();
        for (Neurotransmitter[] layer : out.tagsPerLayer()) {
            for (Neurotransmitter t : layer) {
                tagCounts.merge(t, 1, Integer::sum);
            }
        }
        System.out.printf("[Exp340] tag distribution: %s%n", tagCounts);
    }

    @Test
    void differentInputsProduceDifferentEnrichedOutputs() {
        BooleanChainRunner chain = buildSmallChain(2, 4, 0x1234L);
        EnrichedChainEvaluator eval = new EnrichedChainEvaluator(chain);

        boolean[] allZeros = new boolean[8]; // all false
        boolean[] allOnes = new boolean[8];
        for (int i = 0; i < 8; i++) allOnes[i] = true; // all true

        ChainEnrichedOutput outZeros = eval.evaluateEnriched(allZeros);
        ChainEnrichedOutput outOnes = eval.evaluateEnriched(allOnes);

        // For a 4-neuron layer with k=8, only neuron 0 sees the input
        // bits (sliceStart=0..7); neurons 1..3 see all-zeros because the
        // chain input is only 8 bits long. So:
        // - allZeros: every neuron sees 0/8 bits → excitation = 0.0
        // - allOnes: neuron 0 sees 8/8 (1.0), neurons 1..3 see 0/8 → mean = 0.25
        assertThat(outZeros.meanExcitation()).isEqualTo(0.0);
        assertThat(outOnes.meanExcitation()).isEqualTo(0.25);

        // Inhibition is the complement
        assertThat(outZeros.meanInhibition()).isEqualTo(1.0);
        assertThat(outOnes.meanInhibition()).isEqualTo(0.75);
    }

    @Test
    void chainEnrichedOutputValidatesStructure() {
        BooleanChainRunner chain = buildSmallChain(1, 2, 0xAL);
        EnrichedChainEvaluator eval = new EnrichedChainEvaluator(chain);

        boolean[] input = new boolean[]{true, false};
        ChainEnrichedOutput out = eval.evaluateEnriched(input);

        assertThat(out.layerCount()).isEqualTo(1);
        assertThat(out.totalNeurons()).isEqualTo(2);
    }

    // -- helpers --

    private static BooleanChainRunner buildSmallChain(int layerCount, int neuronsPerLayer, long seed) {
        List<TruthTableLayer> layers = new ArrayList<>();
        Random rng = new Random(seed);
        for (int li = 0; li < layerCount; li++) {
            List<TruthTable> neurons = new ArrayList<>();
            for (int ni = 0; ni < neuronsPerLayer; ni++) {
                int k = 8;
                BitSet bs = new BitSet(1 << k);
                int card = rng.nextInt(1 << k);
                for (int i = 0; i < card; i++) bs.set(rng.nextInt(1 << k));
                neurons.add(TruthTable.of(k, bs));
            }
            layers.add(new TruthTableLayer(neurons, 8));
        }
        return new BooleanChainRunner("test", "(test)", layers);
    }

    private static BooleanChainRunner loadQwenOrSkip() {
        Path cwd = Paths.get("").toAbsolutePath();
        for (Path p = cwd; p != null; p = p.getParent()) {
            for (String rel : new String[]{
                    "models/external/qwen2.5-0.5b/model.safetensors",
                    "models/hf_cache/qwen05b/model.safetensors"}) {
                Path candidate = p.resolve(rel);
                if (Files.exists(candidate)) {
                    return BooleanChainRunner.loadFromSafetensors(candidate, "model", 1 << 14);
                }
            }
        }
        assumeTrue(false, "Qwen safetensors not present — skipping");
        return null;
    }
}
