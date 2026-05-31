package io.matrix.simulation;

import io.matrix.neuron.DecisionTree;

import java.util.BitSet;

/**
 * Brain of an agent: 4 MPDT neurons specialized for N/S/W/E directions.
 *
 * <p>Action selection: evaluate all 4 neurons on the same sensor input,
 * return the direction of the first neuron outputting {@code true}.
 * If none output true, return {@link Direction#STAY}.
 */
public class AgentBrain {

    private final DecisionTree nNeuron;
    private final DecisionTree sNeuron;
    private final DecisionTree wNeuron;
    private final DecisionTree eNeuron;

    public AgentBrain(DecisionTree nNeuron, DecisionTree sNeuron,
                      DecisionTree wNeuron, DecisionTree eNeuron) {
        this.nNeuron = nNeuron;
        this.sNeuron = sNeuron;
        this.wNeuron = wNeuron;
        this.eNeuron = eNeuron;
    }

    public DecisionTree nNeuron() { return nNeuron; }
    public DecisionTree sNeuron() { return sNeuron; }
    public DecisionTree wNeuron() { return wNeuron; }
    public DecisionTree eNeuron() { return eNeuron; }

    /**
     * Evaluates all 4 neurons on the sensor bits and returns the chosen direction.
     */
    public Direction act(long sensorBits) {
        BitSet input = toBitSet(sensorBits);
        if (nNeuron.evaluate(input)) return Direction.N;
        if (sNeuron.evaluate(input)) return Direction.S;
        if (wNeuron.evaluate(input)) return Direction.W;
        if (eNeuron.evaluate(input)) return Direction.E;
        return Direction.STAY;
    }

    private static BitSet toBitSet(long bits) {
        BitSet bs = new BitSet(64);
        for (int i = 0; i < 64; i++) {
            if ((bits & (1L << i)) != 0) {
                bs.set(i);
            }
        }
        return bs;
    }
}
