package io.matrix.simulation;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTree.Leaf;
import io.matrix.neuron.DecisionTree.Split;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static io.matrix.simulation.Direction.*;
import static org.assertj.core.api.Assertions.assertThat;

class AgentBrainTest {

    @Test
    void brainWithFourConstantNeuronsShouldReturnArgmax() {
        DecisionTree goNorth = new Leaf(true);
        DecisionTree goSouth = new Leaf(false);
        DecisionTree goWest = new Leaf(false);
        DecisionTree goEast = new Leaf(false);

        AgentBrain brain = new AgentBrain(goNorth, goSouth, goWest, goEast);
        assertThat(brain.act(0L)).isEqualTo(N);
    }

    @Test
    void brainWithAllFalseShouldStay() {
        AgentBrain brain = new AgentBrain(
                new Leaf(false), new Leaf(false),
                new Leaf(false), new Leaf(false));

        assertThat(brain.act(0L)).isEqualTo(STAY);
    }

    @Test
    void brainWithMultipleTrueShouldReturnFirstInOrder() {
        AgentBrain brain = new AgentBrain(
                new Leaf(false), new Leaf(true),
                new Leaf(true), new Leaf(false));

        assertThat(brain.act(0L)).isEqualTo(S);
    }

    @Test
    void brainShouldEvaluateAllNeuronsWithSameSensorInput() {
        DecisionTree northDetector = new Leaf(false);
        DecisionTree southDetector = new Leaf(false);
        DecisionTree westDetector = new Leaf(false);
        DecisionTree eastDetector = new Split(0, new Leaf(false), new Leaf(true));

        AgentBrain brain = new AgentBrain(northDetector, southDetector, westDetector, eastDetector);

        assertThat(brain.act(0L)).isEqualTo(STAY);
        assertThat(brain.act(1L)).isEqualTo(E);
    }

    @Test
    void brainShouldReturnIndividualNeuronTrees() {
        DecisionTree n = new Leaf(true);
        DecisionTree s = new Leaf(false);
        DecisionTree w = new Leaf(false);
        DecisionTree e = new Leaf(false);

        AgentBrain brain = new AgentBrain(n, s, w, e);
        assertThat(brain.nNeuron()).isSameAs(n);
        assertThat(brain.sNeuron()).isSameAs(s);
        assertThat(brain.wNeuron()).isSameAs(w);
        assertThat(brain.eNeuron()).isSameAs(e);
    }
}
