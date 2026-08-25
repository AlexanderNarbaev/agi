package io.matrix.minecraft;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.TruthTable;
import io.matrix.bir.TtForm;
import io.matrix.bir.DecisionTreeAdapter;

import java.util.BitSet;
import java.util.Random;

/**
 * MATRIX neural brain for controlling a Minecraft-like agent.
 *
 * <p>6 DecisionTrees encode 6 action types (Move, Mine, Craft, Eat, NOP, ToolUp).
 * Sensor bits (35) are evaluated by each tree; the tree with the best
 * combined output selects the action.
 */
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

    /** Per-tree BIR form cache (DESIGN-14 wave A-2): trees are immutable. */
    private static final java.util.concurrent.ConcurrentHashMap<DecisionTree, TtForm> FORM_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** BIR-path evaluation, semantically identical to {@code tree.evaluate(BitSet)}. */
    private static boolean evaluateViaBir(DecisionTree tree, BitSet input) {
        TtForm form = FORM_CACHE.computeIfAbsent(tree, dt -> DecisionTreeAdapter.toBir(dt, dt.inputCount()));
        long packed = 0L;
        for (int b = input.nextSetBit(0); b >= 0 && b < form.k(); b = input.nextSetBit(b + 1)) {
            packed |= 1L << b;
        }
        long[] out = new long[1];
        form.eval(new long[]{packed}, out);
        return out[0] != 0L;
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

        if (evaluateViaBir(eatTree, input) && hungerUrgent(sensorBits)) {
            return new BlockAgent.Action.Eat();
        }
        if (evaluateViaBir(craftTree, input)) {
            return new BlockAgent.Action.Craft();
        }
        if (evaluateViaBir(toolUpTree, input)) {
            return new BlockAgent.Action.Craft();
        }
        if (evaluateViaBir(mineTree, input)) {
            if (evaluateViaBir(moveTree, input)) {
                return pickDirection(input, sensorBits);
            }
            return new BlockAgent.Action.Mine();
        }

        return pickDirection(input, sensorBits);
    }

    private BlockAgent.Action.Move pickDirection(BitSet input, long sensorBits) {
        boolean n = evaluateViaBir(moveTree, input);
        boolean s = evaluateViaBir(moveTree, shiftInput(input, 1));
        boolean w = evaluateViaBir(moveTree, shiftInput(input, 2));
        boolean e = evaluateViaBir(moveTree, shiftInput(input, 3));

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
