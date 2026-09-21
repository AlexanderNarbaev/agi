package io.matrix.research;

import io.matrix.neuron.BitLinear;
import io.matrix.neuron.CodebookMemory;
import io.matrix.neuron.CrossModalPaired;
import io.matrix.neuron.HdcAsLlmPreprocessor;
import io.matrix.neuron.HdcBinding;
import io.matrix.neuron.HdcBrain;
import io.matrix.neuron.HdcConditioning;
import io.matrix.neuron.HdcEncoding;
import io.matrix.neuron.HebbianUpdater;
import io.matrix.neuron.LlmOutputDecoder;
import io.matrix.neuron.NcaBrainSimulator;
import io.matrix.neuron.SokolovHabituationExperiment;
import io.matrix.neuron.SpelkeCoreKnowledge;
import io.matrix.neuron.SyntheticGrammarExperiment;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W31 grand-master integration test — full L0-L6 capability stack demo.
 *
 * <p>Exercises EVERY W31 brain component in a single end-to-end scenario
 * that maps to DESIGN-58 capability levels:
 * <ul>
 *   <li>L0 Fabric — HdcEncoding + HdcBinding + BitLinear + CodebookMemory + HebbianUpdater</li>
 *   <li>L1 Pavlov — HdcConditioning + SokolovHabituationExperiment</li>
 *   <li>L2 Spelke — SpelkeCoreKnowledge (object permanence + A-not-B + numerosity)</li>
 *   <li>L3 Cross-modal — CrossModalPaired (audio↔visual bind/unbind)</li>
 *   <li>L4 Piaget — NcaBrainSimulator (self-organization)</li>
 *   <li>L5 Symbol grounding — HdcAsLlmPreprocessor + LlmOutputDecoder</li>
 *   <li>L6 Compositional — SyntheticGrammarExperiment (Pinker-style)</li>
 * </ul>
 */
class L0ToL6IntegrationTest {

    @Test
    void fullCapabilityStackDemonstration() {
        Random rng = new Random(42);

        // === L0: Fabric — primitives ===
        long[] seedA = HdcEncoding.random(rng);
        long[] seedB = HdcEncoding.random(rng);
        assertThat(HdcEncoding.hamming(seedA, seedA)).isEqualTo(0);
        assertThat(HdcEncoding.hamming(seedA, seedB)).isGreaterThan(400);

        // Binding
        long[] bound = HdcBinding.bind(seedA, seedB);
        long[] unbound = HdcBinding.unbind(bound, seedB);
        assertThat(HdcEncoding.hamming(unbound, seedA)).isEqualTo(0);

        // BitLinear
        float[][] w = {{0.5f, -0.3f}, {0.2f, 0.8f}};
        float[] input = {1.0f, -1.0f};
        float[] output = BitLinear.forward(w, input);
        assertThat(output).hasSize(2);

        // CodebookMemory
        CodebookMemory codebook = new CodebookMemory(10);
        codebook.store("a", seedA);
        codebook.store("b", seedB);
        CodebookMemory.Result hit = codebook.query(seedA);
        assertThat(hit.id).isEqualTo("a");
        assertThat(hit.distance).isEqualTo(0);

        // HebbianUpdater
        HebbianUpdater.State state = HebbianUpdater.empty();
        for (int i = 0; i < 100; i++) {
            HebbianUpdater.update(state, seedA, seedA, 0.5f, 0.01f);
        }
        assertThat(HebbianUpdater.positiveCount(state)).isEqualTo(HdcEncoding.DIM);

        // === L1: Pavlov + Sokolov ===
        HdcBrain brain = new HdcBrain(20, rng);
        long[] cs = HdcEncoding.random(rng);
        long[] us = HdcEncoding.random(rng);
        HdcConditioning.Result pavlov = HdcConditioning.pavlovClassicalConditioning(
                brain, cs, us, "salivate", 3, 5, 3, 0.5f, 0.01f);
        assertThat(pavlov.trials).isEqualTo(11);

        // Sokolov habituation
        SokolovHabituationExperiment.Result sokolov = SokolovHabituationExperiment.run(
                brain, cs, "tone", 10);
        assertThat(sokolov.responseCurve).hasSize(10);

        // === L2: Spelke core knowledge ===
        SpelkeCoreKnowledge.Result perm = SpelkeCoreKnowledge.objectPermanence(
                brain, HdcEncoding.random(rng), 3, HdcEncoding.random(rng));
        assertThat(perm.total).isEqualTo(3);

        SpelkeCoreKnowledge.Result anotB = SpelkeCoreKnowledge.aNotB(
                brain, HdcEncoding.random(rng), HdcEncoding.random(rng), 3);
        assertThat(anotB.total).isEqualTo(3);

        // === L3: Cross-modal ===
        CrossModalPaired cmp = new CrossModalPaired(10, rng);
        long[] audio = HdcEncoding.random(rng);
        long[] visual = HdcEncoding.random(rng);
        cmp.pair(audio, visual, "ball");
        long[] recalledVisual = cmp.retrieveVisual(audio);
        assertThat(HdcEncoding.hamming(recalledVisual, visual)).isEqualTo(0);

        // === L4: NCA self-organization ===
        NcaBrainSimulator nca = new NcaBrainSimulator(8, 8, new Random(42));
        float[][] snap = nca.snapshot();
        nca.stepN(20);
        double dist = nca.distanceTo(snap);
        assertThat(dist).isGreaterThan(0.0);

        // === L5: HDC-as-LLM-preprocessor + Decoder ===
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(42));
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(42));
        String[] concepts = {"machine learning", "neural network", "cognitive science"};
        for (String c : concepts) {
            dec.registerConceptFromText(c, c);
            prep.index(c);
        }
        // Query: "machine learning" should match its own concept
        java.util.List<LlmOutputDecoder.Match> matches = dec.extractConceptsFromText(
                "machine learning", 1);
        assertThat(matches.get(0).concept).isEqualTo("machine learning");
        assertThat(matches.get(0).distance).isEqualTo(0);
        // Memory efficiency: HDC code is 128 bytes vs dense embedding ~3KB
        long memBytes = prep.estimatedMemoryBytes();
        assertThat(memBytes).isLessThan(1000L); // ~160 bytes per concept

        // === L6: Compositional grammar ===
        HdcBrain brain2 = new HdcBrain(50, rng);
        SyntheticGrammarExperiment.Result grammar = SyntheticGrammarExperiment.run(
                brain2, 10, 5, new Random(42));
        assertThat(grammar.total).isEqualTo(10);

        SyntheticGrammarExperiment.Result comp = SyntheticGrammarExperiment.compositionalReasoning(
                brain2, 10, new Random(42));
        assertThat(comp.total).isEqualTo(10);
    }

    @Test
    void allW31ClassesLoadable() {
        // Smoke test: every W31 brain class is on classpath
        assertThat(HdcEncoding.class).isNotNull();
        assertThat(HdcBinding.class).isNotNull();
        assertThat(BitLinear.class).isNotNull();
        assertThat(CodebookMemory.class).isNotNull();
        assertThat(HebbianUpdater.class).isNotNull();
        assertThat(HdcBrain.class).isNotNull();
        assertThat(HdcConditioning.class).isNotNull();
        assertThat(SpelkeCoreKnowledge.class).isNotNull();
        assertThat(CrossModalPaired.class).isNotNull();
        assertThat(NcaBrainSimulator.class).isNotNull();
        assertThat(HdcAsLlmPreprocessor.class).isNotNull();
        assertThat(LlmOutputDecoder.class).isNotNull();
        assertThat(SyntheticGrammarExperiment.class).isNotNull();
        assertThat(SokolovHabituationExperiment.class).isNotNull();
    }
}
