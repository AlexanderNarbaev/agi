package io.matrix.minecraft;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.TruthTable;

import java.util.BitSet;
import java.util.Random;

/**
 * MATRIX neural brain for controlling a Minecraft-like agent.
 *
 * <p>6 DecisionTrees encode 6 action types (Move, Mine, Craft, Eat, NOP, ToolUp).
 * Sensor bits (35) are evaluated by each tree; the tree with the best
 * combined output selects the action.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
@SuppressWarnings("removal")
public class NeuralBrain {

    private final DecisionTree moveTree;
    private final DecisionTree mineTree;
    private final DecisionTree craftTree;
    private final DecisionTree eatTree;
    private final DecisionTree toolUpTree;

    public NeuralBrain(Random rng) {
        this.moveTree = DecisionTree.random(20, 10, rng);
        this.mineTree = DecisionTree.random(20, 8, rng);
        this.craftTree = DecisionTree.random(20, 8, rng);
        this.eatTree = DecisionTree.random(20, 6, rng);
        this.toolUpTree = DecisionTree.random(20, 6, rng);
    }

    public NeuralBrain(DecisionTree move, DecisionTree mine, DecisionTree craft,
                        DecisionTree eat, DecisionTree toolUp) {
        this.moveTree = move;
        this.mineTree = mine;
        this.craftTree = craft;
        this.eatTree = eat;
        this.toolUpTree = toolUp;
    }

    /**
     * Evaluates sensor input and returns the chosen action.
     */
    public BlockAgent.Action act(long sensorBits) {
        BitSet input = toBitSet(sensorBits);

        if (eatTree.evaluate(input) && hungerUrgent(sensorBits)) {
            return new BlockAgent.Action.Eat();
        }
        if (craftTree.evaluate(input)) {
            return new BlockAgent.Action.Craft();
        }
        if (toolUpTree.evaluate(input)) {
            return new BlockAgent.Action.Craft();
        }
        if (mineTree.evaluate(input)) {
            if (moveTree.evaluate(input)) {
                return pickDirection(input, sensorBits);
            }
            return new BlockAgent.Action.Mine();
        }

        return pickDirection(input, sensorBits);
    }

    private BlockAgent.Action.Move pickDirection(BitSet input, long sensorBits) {
        boolean n = moveTree.evaluate(input);
        boolean s = moveTree.evaluate(shiftInput(input, 1));
        boolean w = moveTree.evaluate(shiftInput(input, 2));
        boolean e = moveTree.evaluate(shiftInput(input, 3));

        if (n) return new BlockAgent.Action.Move(BlockAgent.Direction.N);
        if (s) return new BlockAgent.Action.Move(BlockAgent.Direction.S);
        if (w) return new BlockAgent.Action.Move(BlockAgent.Direction.W);
        if (e) return new BlockAgent.Action.Move(BlockAgent.Direction.E);
        return new BlockAgent.Action.Move(BlockAgent.Direction.STAY);
    }

    private boolean hungerUrgent(long sensorBits) {
        return ((sensorBits >> 13) & 0x7) <= 1;
    }

    private BitSet toBitSet(long bits) {
        BitSet bs = new BitSet(64);
        for (int i = 0; i < 64; i++) {
            if ((bits & (1L << i)) != 0) bs.set(i);
        }
        return bs;
    }

    private BitSet shiftInput(BitSet input, int mod) {
        BitSet shifted = (BitSet) input.clone();
        shifted.set(0, mod);
        return shifted;
    }

    public DecisionTree moveTree() { return moveTree; }
    public DecisionTree mineTree() { return mineTree; }
    public DecisionTree craftTree() { return craftTree; }
    public DecisionTree eatTree() { return eatTree; }
}
