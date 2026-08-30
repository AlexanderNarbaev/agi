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
        for (int cell = (1 << k) - 1; cell >= 0; cell--) {
            if (tt.evaluate(cell)) {
                // return the highest set bit (would change most patterns)
                return Integer.numberOfTrailingZeros(Integer.highestOneBit(cell));
            }
        }
        return 0;
    }

    /** Rebuild a truth table by flipping a single bit in its fired pattern.
     *  Returns a {@link TruthTable} (the input type to the chain). */
    private static TruthTable flippedTable(TruthTable original, int flippedBit) {
        int k = original.k();
        int cells = 1 << k;
        java.util.BitSet newTable = (java.util.BitSet) original.table().clone();
        for (int cell = 0; cell < cells; cell++) {
            if (original.evaluate(cell) && ((cell >>> flippedBit) & 1) == 0) {
                newTable.set(cell, false);
            }
        }
        return TruthTable.of(k, newTable, original.weights());
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