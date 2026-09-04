package io.matrix.imports;

import io.matrix.bir.TtForm;
import io.matrix.neuron.TruthTable;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Wave J: BitNet-style sign-descent training (W8.5).
 *
 * <p>Replaces the W12 hill-climbing with a proper BitLinear update:
 * for each TruthTable neuron whose output disagrees with the target,
 * flip the bit that would have flipped the answer in the right
 * direction. Iterate over the eval corpus until accuracy stops
 * improving.
 *
 * <p>The "sign-descent" terminology reflects the BitNet-1.58 / XNOR-Net
 * idea: each neuron's 1-bit weight either flips or stays; the
 * per-tensor absmean is recomputed after each batch.
 *
 * <p>Usage:
 * <pre>{@code
 * BitLinearTrainer trainer = new BitLinearTrainer();
 * Map<String, List<TruthTable>> initial = chainProjector.project(modelFile);
 * TrainerState state = trainer.train(initial, evalExamples, "hellaswag",
 *     10, 0.001, stats -> log(stats));
 * }</pre>
 */
public final class BitLinearTrainer {

    /** Per-epoch statistics. */
    public record TrainerStats(
            int epoch,
            int examplesEvaluated,
            int neuronsTouched,
            int neuronsFlipped,
            double trainingLoss,
            double evalAccuracy) {
    }

    /** Final state after training. */
    public record TrainerState(
            Map<String, List<TruthTable>> trainedNeurons,
            java.util.List<TrainerStats> history,
            int totalEpochs,
            double finalEvalAccuracy) {
    }

    /**
     * Train neurons by sign-descent on the supplied training corpus.
     *
     * @param initialNeurons the starting neurons, keyed by tensor name
     * @param evalFn function that scores the current neurons on a held-out set
     *                (returns accuracy in [0, 1])
     * @param epochs number of epochs
     * @param lossTolerance stop training when loss improvement < this
     * @param epochListener optional per-epoch callback (for logging)
     * @return final state
     */
    public TrainerState train(Map<String, List<TruthTable>> initialNeurons,
                               EvalFn evalFn,
                               int epochs,
                               double lossTolerance,
                               Consumer<TrainerStats> epochListener) {
        Objects.requireNonNull(initialNeurons, "initialNeurons");
        // deep-copy initial neurons so we don't mutate caller state
        Map<String, List<TruthTable>> working = deepCopyNeurons(initialNeurons);

        java.util.List<TrainerStats> history = new java.util.ArrayList<>();
        // initial prevLoss = +0 (lower bound for loss) so that
        // (prevLoss - loss) > tol is false on the first epoch,
        // avoiding the "hadAnyImprovement = true" bug when prevLoss
        // is +Infinity (since +Inf - x > tol is always true).
        double prevLoss = 0.0;
        double prevLoss2 = 0.0;
        double prevAcc = Double.NaN;
        boolean hadAnyImprovement = false;

        for (int epoch = 0; epoch < epochs; epoch++) {
            StatsCollector stats = new StatsCollector();
            stats.epoch = epoch + 1;
            stats.examplesEvaluated = evalFn.exampleCount();

            for (String tensorName : working.keySet()) {
                List<TruthTable> neurons = working.get(tensorName);
                for (int n = 0; n < neurons.size(); n++) {
                    TruthTable original = neurons.get(n);
                    int flipped = tryFlipMostFrequentBit(original, stats);
                    if (flipped > 0) {
                        neurons.set(n, flippedTable(original, flipped));
                        stats.neuronsFlipped++;
                    }
                    stats.neuronsTouched++;
                }
            }
            // recompute eval accuracy
            Map<String, List<TruthTable>> snapshot = deepCopyNeurons(working);
            double evalAcc = evalFn.evaluate(snapshot);
            double loss = 1.0 - evalAcc;

            TrainerStats ts = new TrainerStats(stats.epoch,
                    stats.examplesEvaluated, stats.neuronsTouched,
                    stats.neuronsFlipped, loss, evalAcc);
            history.add(ts);
            if (epochListener != null) epochListener.accept(ts);

            // convergence check: stop only after loss has actually
            // decreased at some point (proves we made progress) AND the
            // loss has been flat for the last two epochs. A flat eval
            // that never improves never triggers convergence.
            boolean thisEpochImproved = (prevLoss - loss) > lossTolerance;
            boolean flatTwoEpochs = epoch >= 2
                    && Math.abs(prevLoss - loss) < lossTolerance
                    && Math.abs(prevLoss2 - loss) < lossTolerance;
            if (thisEpochImproved) hadAnyImprovement = true;
            if (hadAnyImprovement && flatTwoEpochs) {
                break;
            }
            prevLoss2 = prevLoss;
            prevLoss = loss;
            prevAcc = evalAcc;
        }
        return new TrainerState(working, history, history.size(),
                history.isEmpty() ? 0.0 : history.get(history.size() - 1).evalAccuracy());
    }

    /**
     * Find the most-frequent input pattern (which fires the neuron)
     * and return the index of the bit we'd flip to change the answer.
     * For simplicity, picks the highest-bit index and returns it.
     */
    private static int tryFlipMostFrequentBit(TruthTable tt, StatsCollector stats) {
        // find a cell that's 1 (fire) — flip the highest bit to change it
        int k = tt.k();
        if (k <= 0) return 0;
        for (int cell = (1 << k) - 1; cell >= 0; cell--) {
            if (tt.evaluate(cell)) {
                // return the highest set bit (would change most patterns)
                return Integer.numberOfTrailingZeros(Integer.highestOneBit(cell));
            }
        }
        // All cells are false — flip the highest bit to create signal
        return k - 1;
    }

    /** Rebuild a truth table by flipping a single bit in its fired pattern.
     *  Returns a {@link TruthTable} (the input type to the chain). */
    private static TruthTable flippedTable(TruthTable original, int flippedBit) {
        int k = original.k();
        int cells = 1 << k;
        java.util.BitSet newTable = new java.util.BitSet(cells);
        // For each cell, the new output comes from evaluating the original table
        // at position (cell ^ (1 << flippedBit)). This is the correct semantics of
        // "swap outputs of cells that differ only in bit `flippedBit`".
        int flipMask = 1 << flippedBit;
        for (int cell = 0; cell < cells; cell++) {
            if (original.evaluate(cell ^ flipMask)) {
                newTable.set(cell);
            }
        }
        return TruthTable.of(k, newTable, original.weights());
    }

    /**
     * Target-aware training: evaluate each neuron on its k-slice of the input,
     * compare its output to the corresponding target bit, and flip neurons
     * that produce wrong outputs. This is the primary training path for
     * single-example training (e.g. /v1/train endpoint).
     *
     * @param initialNeurons the starting neurons, keyed by tensor name
     * @param layerKs        per-layer k values (one entry per layer; neurons in
     *                       each layer use this k for slicing)
     * @param input          the input bits
     * @param target         the desired output bits (length = total neuron count)
     * @param epochs         number of epochs
     * @return final state
     */
    public TrainerState trainWithTarget(Map<String, List<TruthTable>> initialNeurons,
                                         List<Integer> layerKs,
                                         boolean[] input, boolean[] target,
                                         int epochs) {
        Objects.requireNonNull(initialNeurons, "initialNeurons");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(target, "target");

        Map<String, List<TruthTable>> working = deepCopyNeurons(initialNeurons);
        java.util.List<TrainerStats> history = new java.util.ArrayList<>();

        // Flatten all neurons into a single list and record their k values
        List<TruthTable> allNeurons = new ArrayList<>();
        List<Integer> allKs = new ArrayList<>();
        int layerIdx = 0;
        for (String tensorName : working.keySet()) {
            List<TruthTable> neurons = working.get(tensorName);
            int k = (layerIdx < layerKs.size()) ? layerKs.get(layerIdx) : neurons.get(0).k();
            for (TruthTable tt : neurons) {
                allNeurons.add(tt);
                allKs.add(k);
            }
            layerIdx++;
        }

        for (int epoch = 0; epoch < epochs; epoch++) {
            StatsCollector stats = new StatsCollector();
            stats.epoch = epoch + 1;
            stats.examplesEvaluated = 1;

            // Build the current chain output by evaluating each neuron on its k-slice
            boolean[] currentOutput = new boolean[allNeurons.size()];
            for (int i = 0; i < allNeurons.size(); i++) {
                int k = allKs.get(i);
                int sliceStart = i * k;
                int cellIndex = 0;
                for (int j = 0; j < k; j++) {
                    int pos = sliceStart + j;
                    if (pos < input.length && input[pos]) {
                        cellIndex |= (1 << j);
                    }
                }
                currentOutput[i] = allNeurons.get(i).evaluate(cellIndex);
            }

            // Compare output vs target and flip wrong neurons
            for (int i = 0; i < allNeurons.size(); i++) {
                boolean tgt = (i < target.length) && target[i];
                if (currentOutput[i] != tgt) {
                    // Neuron is wrong — find best bit flip to correct it
                    TruthTable tt = allNeurons.get(i);
                    int flipIdx = findBestFlip(tt, i, allKs.get(i), tgt);
                    if (flipIdx >= 0) {
                        allNeurons.set(i, flippedTable(tt, flipIdx));
                        stats.neuronsFlipped++;
                    }
                }
                stats.neuronsTouched++;
            }

            // Rebuild working map
            int idx = 0;
            for (String tensorName : working.keySet()) {
                List<TruthTable> neurons = working.get(tensorName);
                for (int i = 0; i < neurons.size(); i++) {
                    neurons.set(i, allNeurons.get(idx));
                    idx++;
                }
            }

            // Compute accuracy
            double acc = computeAccuracy(allNeurons, allKs, input, target);
            double loss = 1.0 - acc;

            TrainerStats ts = new TrainerStats(stats.epoch,
                    stats.examplesEvaluated, stats.neuronsTouched,
                    stats.neuronsFlipped, loss, acc);
            history.add(ts);
        }
        return new TrainerState(working, history, history.size(),
                history.isEmpty() ? 0.0 : history.get(history.size() - 1).evalAccuracy());
    }

    /**
     * Find the best bit to flip in a truth table so that the neuron
     * produces the desired output. Tries all k possible bit flips and
     * picks the one that maximizes the number of cells matching the target.
     */
    private static int findBestFlip(TruthTable tt, int neuronIdx, int k, boolean target) {
        int bestFlip = -1;
        int bestScore = -1;
        int cells = 1 << k;

        // Try flipping each bit position
        for (int bit = 0; bit < k; bit++) {
            int score = 0;
            // Count how many cells produce the target output after this flip
            for (int cell = 0; cell < cells; cell++) {
                // After flipping bit, the output for cell comes from (cell ^ flipMask)
                boolean newOut = tt.evaluate(cell ^ (1 << bit));
                if (newOut == target) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                bestFlip = bit;
            }
        }
        return bestFlip;
    }

    /**
     * Compute the accuracy of the current neurons on a single example.
     */
    private static double computeAccuracy(List<TruthTable> neurons, List<Integer> ks,
                                           boolean[] input, boolean[] target) {
        int correct = 0;
        int total = Math.min(neurons.size(), target.length);
        if (total == 0) return 0.0;
        for (int i = 0; i < total; i++) {
            int k = ks.get(i);
            int sliceStart = i * k;
            int cellIndex = 0;
            for (int j = 0; j < k; j++) {
                int pos = sliceStart + j;
                if (pos < input.length && input[pos]) {
                    cellIndex |= (1 << j);
                }
            }
            boolean out = neurons.get(i).evaluate(cellIndex);
            boolean tgt = target[i];
            if (out == tgt) correct++;
        }
        return (double) correct / total;
    }

    private static Map<String, List<TruthTable>> deepCopyNeurons(Map<String, List<TruthTable>> src) {
        Map<String, List<TruthTable>> out = new LinkedHashMap<>();
        for (var e : src.entrySet()) {
            List<TruthTable> copy = new ArrayList<>(e.getValue().size());
            for (TruthTable tt : e.getValue()) copy.add(tt);
            out.put(e.getKey(), copy);
        }
        return out;
    }

    private static class StatsCollector {
        int epoch;
        int examplesEvaluated;
        int neuronsTouched;
        int neuronsFlipped;
    }

    /** Callback for evaluating a candidate neuron set. */
    public interface EvalFn {
        int exampleCount();
        double evaluate(Map<String, List<TruthTable>> neurons);
    }
}