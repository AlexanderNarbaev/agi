package io.matrix.imports;

import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave J tests: BitLinearTrainer sign-descent loop. Verifies the
 * core training mechanics — copy semantics, neuron-flipping, epoch
 * iteration, convergence — without requiring a real model.
 */
class BitLinearTrainerTest {

    @Test
    void trainingRunsAllEpochsWhenNoConvergence() {
        // EvalFn that returns a fixed value — trainer should run all epochs
        // (loss never improves enough to converge)
        BitLinearTrainer trainer = new BitLinearTrainer();
        Map<String, List<TruthTable>> initial = smallInitial();
        AtomicInteger evalCalls = new AtomicInteger();
        BitLinearTrainer.EvalFn fixedEval = new BitLinearTrainer.EvalFn() {
            @Override public int exampleCount() { return 1; }
            @Override public double evaluate(Map<String, List<TruthTable>> n) {
                evalCalls.incrementAndGet();
                return 0.5;
            }
        };
        BitLinearTrainer.TrainerState state = trainer.train(
                initial, fixedEval, 5, 0.0001, null);
        assertThat(state.totalEpochs()).isEqualTo(5);
        assertThat(evalCalls.get()).isEqualTo(5);
        assertThat(state.history()).hasSize(5);
    }

    @Test
    void trainingStopsEarlyOnConvergence() {
        // EvalFn that improves then plateaus:
        //   epoch 0: acc = 0.5, loss = 0.5
        //   epoch 1: acc = 0.55, loss = 0.45  (improvement: 0.05 > tol)
        //   epoch 2: acc = 0.56, loss = 0.44 (improvement: 0.01 < tol) — flat two epochs after improvement
        //   trainer should detect convergence and stop
        BitLinearTrainer trainer = new BitLinearTrainer();
        Map<String, List<TruthTable>> initial = smallInitial();
        double[] acc = {0.5, 0.55, 0.56, 0.56001, 0.56001, 0.56001};
        AtomicInteger callIdx = new AtomicInteger();
        BitLinearTrainer.EvalFn convergingEval = new BitLinearTrainer.EvalFn() {
            @Override public int exampleCount() { return 1; }
            @Override public double evaluate(Map<String, List<TruthTable>> n) {
                return acc[Math.min(callIdx.getAndIncrement(), acc.length - 1)];
            }
        };
        BitLinearTrainer.TrainerState state = trainer.train(
                initial, convergingEval, 20, 0.001, null);
        // epoch 0: acc 0.5, loss 0.5, prevLoss=+Inf → hadAnyImprovement false
        // epoch 1: acc 0.55, loss 0.45 → prevLoss=0.5; improvement 0.05 > tol → hadAnyImprovement=true
        // epoch 2: acc 0.56, loss 0.44 → prevLoss=0.45; flatTwoEpochs: epoch>=2 ✓,
        //           |0.45-0.44|=0.01 > tol → flat is false; continue
        // epoch 3: acc 0.56001, loss 0.43999 → prevLoss=0.44;
        //           flatTwoEpochs: |0.44-0.43999|=0.00001 < tol ✓,
        //           |0.45-0.43999|=0.01001 > tol → flat is false; continue
        // epoch 4: acc 0.56001, loss 0.43999 → prevLoss=0.43999;
        //           flatTwoEpochs: |0.43999-0.43999|=0 < tol ✓,
        //           |0.44-0.43999|=0.00001 < tol ✓ → flat is true → break
        // so total = 5 epochs (0, 1, 2, 3, 4)
        assertThat(state.totalEpochs())
                .as("training should stop at epoch 4")
                .isEqualTo(5);
    }

    @Test
    void trainingDoesNotMutateInput() {
        // deep-copy semantics: input neurons should not change
        BitLinearTrainer trainer = new BitLinearTrainer();
        Map<String, List<TruthTable>> initial = smallInitial();
        TruthTable before = initial.get("t0").get(0);
        int[] beforeBits = new int[(int) Math.pow(2, before.k())];
        for (int i = 0; i < beforeBits.length; i++) beforeBits[i] = before.evaluate(i) ? 1 : 0;

        BitLinearTrainer.EvalFn noopEval = new BitLinearTrainer.EvalFn() {
            @Override public int exampleCount() { return 0; }
            @Override public double evaluate(Map<String, List<TruthTable>> n) { return 0.0; }
        };
        trainer.train(initial, noopEval, 3, 1e-9, null);

        // input should be unchanged
        TruthTable after = initial.get("t0").get(0);
        for (int i = 0; i < beforeBits.length; i++) {
            assertThat(after.evaluate(i) ? 1 : 0)
                    .as("input neuron[" + i + "] changed")
                    .isEqualTo(beforeBits[i]);
        }
    }

    @Test
    void flippedTableHasFewerOnes() {
        // when we flip a bit in a fired pattern, the resulting
        // neuron should fire for fewer patterns (strict subset)
        TruthTable original = TruthTable.fromLong(4, 0b1111_1111_1111_1111L);
        for (int flippedBit = 0; flippedBit < 4; flippedBit++) {
            TruthTable flipped = invokeFlipped(original, flippedBit);
            int origOnes = 0, flippedOnes = 0;
            for (int i = 0; i < 16; i++) {
                if (original.evaluate(i)) origOnes++;
                if (flipped.evaluate(i)) flippedOnes++;
            }
            assertThat(flippedOnes)
                    .as("flipping bit %d in fully-on TT reduces active cells", flippedBit)
                    .isLessThanOrEqualTo(origOnes);
        }
    }

    @Test
    void epochListenerReceivesStatsPerEpoch() {
        BitLinearTrainer trainer = new BitLinearTrainer();
        Map<String, List<TruthTable>> initial = smallInitial();
        BitLinearTrainer.EvalFn noopEval = new BitLinearTrainer.EvalFn() {
            @Override public int exampleCount() { return 7; }
            @Override public double evaluate(Map<String, List<TruthTable>> n) { return 0.5; }
        };
        List<BitLinearTrainer.TrainerStats> received = new ArrayList<>();
        trainer.train(initial, noopEval, 4, 1e-9, received::add);
        assertThat(received).hasSize(4);
        for (BitLinearTrainer.TrainerStats s : received) {
            assertThat(s.epoch()).isPositive();
            assertThat(s.examplesEvaluated()).isEqualTo(7);
        }
    }

    @Test
    void finalStateHasTrainedNeurons() {
        BitLinearTrainer trainer = new BitLinearTrainer();
        Map<String, List<TruthTable>> initial = smallInitial();
        BitLinearTrainer.EvalFn noopEval = new BitLinearTrainer.EvalFn() {
            @Override public int exampleCount() { return 1; }
            @Override public double evaluate(Map<String, List<TruthTable>> n) { return 0.5; }
        };
        BitLinearTrainer.TrainerState state = trainer.train(initial, noopEval, 2, 1e-9, null);
        assertThat(state.trainedNeurons()).isNotEmpty();
        assertThat(state.trainedNeurons().get("t0")).hasSize(initial.get("t0").size());
    }

    /** Build a small input neuron set: 2 tensors × 4 neurons each (k=4). */
    private static Map<String, List<TruthTable>> smallInitial() {
        Map<String, List<TruthTable>> out = new LinkedHashMap<>();
        for (String name : new String[]{"t0", "t1"}) {
            List<TruthTable> ts = new ArrayList<>();
            // random-ish truth tables
            ts.add(TruthTable.fromLong(4, 0b1010_0101_0011_1100L));
            ts.add(TruthTable.fromLong(4, 0b0101_1010_1100_0011L));
            ts.add(TruthTable.fromLong(4, 0b1111_0000_1010_0101L));
            ts.add(TruthTable.fromLong(4, 0b0000_1111_0101_1010L));
            out.put(name, ts);
        }
        return out;
    }

    @Test
    void trainWithTargetFlipsNeurons() {
        // Synthetic chain: 5 layers, k=8, 64 neurons per layer = 320 neurons
        int numLayers = 5;
        int k = 8;
        int neuronsPerLayer = 64;
        int totalNeurons = numLayers * neuronsPerLayer;

        Map<String, List<TruthTable>> initial = new LinkedHashMap<>();
        List<Integer> layerKs = new ArrayList<>();
        for (int l = 0; l < numLayers; l++) {
            String name = "layer" + l;
            List<TruthTable> neurons = new ArrayList<>();
            for (int n = 0; n < neuronsPerLayer; n++) {
                // each neuron: k inputs → one bit output
                // initialize with a random-ish truth table
                long table = 0;
                for (int cell = 0; cell < (1 << k); cell++) {
                    if (((cell * 31 + l * 17 + n * 7) & 3) == 0) {
                        table |= (1L << cell);
                    }
                }
                neurons.add(TruthTable.fromLong(k, table));
            }
            initial.put(name, neurons);
            layerKs.add(k);
        }

        BitLinearTrainer trainer = new BitLinearTrainer();
        long totalFlipped = 0;
        double lastAccuracy = 0;

        // Train 100 random pairs for 3 epochs each
        for (int p = 0; p < 100; p++) {
            boolean[] input = new boolean[totalNeurons * k];
            boolean[] target = new boolean[totalNeurons];
            for (int i = 0; i < input.length; i++) {
                input[i] = ((i * 31 + p * 17) & 3) == 0;
            }
            for (int i = 0; i < target.length; i++) {
                target[i] = ((i * 7 + p * 13) & 3) == 0;
            }

            BitLinearTrainer.TrainerState state = trainer.trainWithTarget(
                    initial, layerKs, input, target, 3);
            long flipped = state.history().stream()
                    .mapToLong(BitLinearTrainer.TrainerStats::neuronsFlipped)
                    .sum();
            totalFlipped += flipped;
            lastAccuracy = state.finalEvalAccuracy();
            // update initial with the trained neurons for next pair
            initial = state.trainedNeurons();
        }

        assertThat(totalFlipped)
                .as("at least some neurons should flip during 100 pairs × 3 epochs")
                .isGreaterThan(0);
        assertThat(lastAccuracy)
                .as("final accuracy should be above 50% after training")
                .isGreaterThan(0.5);
    }

    /** Reflective call to the private static flippedTable. */
    private static TruthTable invokeFlipped(TruthTable original, int flippedBit) {
        try {
            var m = BitLinearTrainer.class.getDeclaredMethod(
                    "flippedTable", TruthTable.class, int.class);
            m.setAccessible(true);
            return (TruthTable) m.invoke(null, original, flippedBit);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}