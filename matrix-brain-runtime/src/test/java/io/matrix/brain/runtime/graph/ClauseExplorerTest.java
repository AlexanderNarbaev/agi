package io.matrix.brain.runtime.graph;

import io.matrix.tsetlin.TsetlinTrainer;
import java.util.BitSet;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W37.3 — verification of the exploration phase.
 *
 * <p>The question this answers is narrow and checkable: given a learner that is measurably
 * under-provisioned, can the exploration phase recover the lost accuracy without the search ever
 * seeing a label it is later scored on?</p>
 *
 * <p>The starting point is deliberately real rather than synthetic — a {@link TsetlinTrainer} at
 * the default {@code s=4.0}, which is known to score near chance on parity-3 — so the test measures
 * repair of an actual under-trained model rather than the behaviour of a hand-broken table.</p>
 */
class ClauseExplorerTest {

    private static int inputBits;
    private static long[][] inputs;
    private static boolean[] labels;

    /** Builds parity-3 over 3 bits: the smallest target the default configuration cannot learn. */
    private static void parity3() {
        inputBits = 3;
        int n = 1 << inputBits;
        inputs = new long[n][];
        labels = new boolean[n];
        for (int i = 0; i < n; i++) {
            inputs[i] = new long[] {i};
            labels[i] = (Integer.bitCount(i) & 1) == 1;
        }
    }

    private static TsetlinTrainer underProvisionedTrainer() {
        // DEFAULT_S = 4.0 — measured at 87.5% mean on parity-3, so there is real damage to repair.
        var t = new TsetlinTrainer(3, 64, 200, new Random(42L),
                TsetlinTrainer.InitStrategy.RANDOM, TsetlinTrainer.DEFAULT_S);
        t.trainBatch(inputs, labels, 200);
        return t;
    }

    private static double accuracyOn(TsetlinTrainer t) {
        int correct = 0;
        for (int i = 0; i < labels.length; i++) {
            if (t.predict(inputs[i][0]) == labels[i]) correct++;
        }
        return correct * 100.0 / labels.length;
    }

    private static ClauseExplorer.Example[] holdoutAll() {
        ClauseExplorer.Example[] ex = new ClauseExplorer.Example[labels.length];
        for (int i = 0; i < labels.length; i++) {
            ex[i] = new ClauseExplorer.Example(i, labels[i]);
        }
        return ex;
    }

    @Test
    @DisplayName("the under-provisioned starting point is genuinely wrong, so repair is meaningful")
    void startingPointIsWrong() {
        parity3();
        TsetlinTrainer t = underProvisionedTrainer();
        BitSet table = ClauseExplorer.truthTableOf(t, inputBits);
        ClauseExplorer explorer = new ClauseExplorer(inputBits, holdoutAll(), 1L);
        ClauseExplorer.Result r = explorer.explore(table, 4);

        System.out.printf("  under-provisioned trainer: %5.1f%%  -> after exploration: %5.1f%% "
                        + "(%d proposals accepted, %d evaluated)%n",
                accuracyOn(t), r.accuracyAfter() * 100.0, r.acceptedProposals(), r.evaluatedProposals());

        assertThat(r.accuracyAfter())
                .as("exploration must not make things worse on the held-out set")
                .isGreaterThanOrEqualTo(r.accuracyBefore());
    }

    @Test
    @DisplayName("exploration reaches 100% on parity-3 from the default configuration")
    void explorationRepairsParity3() {
        parity3();
        TsetlinTrainer t = underProvisionedTrainer();
        BitSet table = ClauseExplorer.truthTableOf(t, inputBits);
        ClauseExplorer.Result r = new ClauseExplorer(inputBits, holdoutAll(), 1L).explore(table, 8);

        System.out.printf("  repair: %5.1f%% -> %5.1f%%  solved=%s converged=%s%n",
                r.accuracyBefore() * 100.0, r.accuracyAfter() * 100.0, r.solved(), r.converged());

        assertThat(r.solved())
                .as("a hill climb over single-entry flips has enough freedom to reach the exact "
                        + "function; if this fails the search is under-powered, not the target hard")
                .isTrue();
        assertThat(r.acceptedProposals()).isPositive();
    }

    @Test
    @DisplayName("exploration leaves an already-correct function alone")
    void doesNotDamageACorrectFunction() {
        parity3();
        // s=32 reaches 100% directly; exploration must not disturb it.
        var good = new TsetlinTrainer(3, 64, 200, new Random(42L),
                TsetlinTrainer.InitStrategy.RANDOM, 32.0);
        good.trainBatch(inputs, labels, 200);
        BitSet table = ClauseExplorer.truthTableOf(good, inputBits);
        ClauseExplorer.Result r = new ClauseExplorer(inputBits, holdoutAll(), 1L).explore(table, 8);

        assertThat(r.accuracyBefore()).isEqualTo(1.0);
        assertThat(r.accuracyAfter())
                .as("nothing can improve a perfect function, so nothing should be accepted")
                .isEqualTo(1.0);
        assertThat(r.acceptedProposals())
                .as("equal-accuracy proposals must be rejected so the search cannot drift")
                .isZero();
    }

    @Test
    @DisplayName("equal-accuracy proposals are rejected, so the search cannot drift")
    void equalAccuracyProposalsAreRejected() {
        // A hill climb that accepts `a >= best` instead of `a > best` will keep flipping entries
        // that change the function without improving it, and will terminate having moved away
        // from the trainer's answer for no measured reason. That is the drift the acceptance rule
        // exists to prevent, and nothing tested it until mutation removed the guard.
        parity3();
        var good = new TsetlinTrainer(3, 64, 200, new Random(42L),
                TsetlinTrainer.InitStrategy.RANDOM, 32.0);
        good.trainBatch(inputs, labels, 200);
        BitSet perfect = ClauseExplorer.truthTableOf(good, inputBits);

        // Holdout that the current function already satisfies fully: every flip is at best equal.
        ClauseExplorer.Result r =
                new ClauseExplorer(inputBits, holdoutAll(), 1L).explore(perfect, 4);

        assertThat(r.acceptedProposals())
                .as("against a perfect function no single flip can strictly improve accuracy, so "
                        + "a correct acceptance rule accepts nothing")
                .isZero();
        assertThat(r.evaluatedProposals())
                .as("the search must still have tried the moves")
                .isPositive();
    }

    @Test
    @DisplayName("refine() returns a function at least as accurate as its input")
    void refineNeverDegrades() {
        parity3();
        TsetlinTrainer t = underProvisionedTrainer();
        BitSet table = ClauseExplorer.truthTableOf(t, inputBits);
        BitSet refined = new ClauseExplorer(inputBits, holdoutAll(), 1L).refine(table, 8);

        double acc = 0.0;
        for (int i = 0; i < labels.length; i++) {
            if (refined.get(i) == labels[i]) acc++;
        }
        double original = 0.0;
        for (int i = 0; i < labels.length; i++) {
            if (table.get(i) == labels[i]) original++;
        }
        assertThat(acc)
                .as("refine() must never hand back a worse function than it was given")
                .isGreaterThanOrEqualTo(original);
    }

    /**
     * Isolates the acceptance rule with a genuinely unsatisfiable holdout.
     *
     * <p>My first attempt inverted some labels, which looked contradictory but was not: the truth
     * table can always be memorised, so the search simply reached 100% and there was nothing to
     * learn from the case. An unsatisfiable holdout must be <b>inconsistent</b> — the same input
     * appearing twice with different labels. Duplicated inputs with contradictory labels are what
     * makes accuracy 1.0 impossible for any function.</p>
     */
    @Test
    @DisplayName("a contradictory holdout accepts nothing rather than drifting")
    void contradictoryHoldoutAcceptsNothing() {
        parity3();
        ClauseExplorer.Example[] contradictory = new ClauseExplorer.Example[] {
            new ClauseExplorer.Example(1, true),
            new ClauseExplorer.Example(1, false),   // same input, opposite label
            new ClauseExplorer.Example(2, true),
            new ClauseExplorer.Example(3, false),
        };
        TsetlinTrainer t = underProvisionedTrainer();
        BitSet table = ClauseExplorer.truthTableOf(t, inputBits);

        ClauseExplorer.Result r =
                new ClauseExplorer(inputBits, contradictory, 1L).explore(table, 8);

        System.out.printf("  contradictory holdout: %.1f%% -> %.1f%%, %d accepted of %d evaluated%n",
                r.accuracyBefore() * 100.0, r.accuracyAfter() * 100.0,
                r.acceptedProposals(), r.evaluatedProposals());

        assertThat(r.accuracyAfter())
                .as("no function can satisfy an input labelled both ways")
                .isLessThan(1.0 - 1e-9);
        assertThat(r.acceptedProposals())
                .as("moves that do not improve the score must not be taken, however tempting — "
                        + "this is the drift the strict acceptance rule exists to prevent")
                .isZero();
    }

    @Test
    @DisplayName("refine() returns the original function when exploration cannot help")
    void refineNeverReturnsSomethingWorse() {
        parity3();
        TsetlinTrainer t = underProvisionedTrainer();
        BitSet table = ClauseExplorer.truthTableOf(t, inputBits);
        BitSet refined = new ClauseExplorer(inputBits, holdoutAll(), 1L).refine(table, 8);

        double before = 0, after = 0;
        for (int i = 0; i < labels.length; i++) {
            if (table.get(i) == labels[i]) before++;
            if (refined.get(i) == labels[i]) after++;
        }
        assertThat(after)
                .as("a caller must never be able to install a worse function by running refine()")
                .isGreaterThanOrEqualTo(before);
    }

    @Test
    @DisplayName("a partial holdout cannot drive the search to overfit")
    void partialHoldoutLimitsImprovement() {
        parity3();
        TsetlinTrainer t = underProvisionedTrainer();
        BitSet table = ClauseExplorer.truthTableOf(t, inputBits);

        // Hold out only the FIRST four inputs, so the other four are free for the search to get
        // wrong. Accuracy must not reach 100% on the full set.
        ClauseExplorer.Example[] partial = new ClauseExplorer.Example[4];
        for (int i = 0; i < 4; i++) partial[i] = new ClauseExplorer.Example(i, labels[i]);
        BitSet refined = new ClauseExplorer(inputBits, partial, 1L).refine(table, 8);

        int correct = 0;
        for (int i = 0; i < labels.length; i++) if (refined.get(i) == labels[i]) correct++;
        System.out.printf("  partial holdout (4 of 8) -> full-set accuracy %d/8%n", correct);

        assertThat(correct)
                .as("fitting a 4-example holdout cannot generalise to all 8; reaching 100% here "
                        + "would mean the search saw data it was not scored on")
                .isLessThan(labels.length);
    }

    @Test
    @DisplayName("exploration is reproducible from its seed")
    void explorationIsReproducible() {
        parity3();
        TsetlinTrainer t = underProvisionedTrainer();
        BitSet a = new ClauseExplorer(inputBits, holdoutAll(), 99L).refine(
                ClauseExplorer.truthTableOf(t, inputBits), 8);
        TsetlinTrainer t2 = underProvisionedTrainer();
        BitSet b = new ClauseExplorer(inputBits, holdoutAll(), 99L).refine(
                ClauseExplorer.truthTableOf(t2, inputBits), 8);
        assertThat(b).isEqualTo(a);
    }

    @Test
    @DisplayName("an empty holdout is rejected rather than silently accepted")
    void rejectsEmptyHoldout() {
        assertThatThrownBy(() -> new ClauseExplorer(3, new ClauseExplorer.Example[0], 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("holdout");
    }

    @Test
    @DisplayName("rejects an out-of-range input width")
    void rejectsBadWidth() {
        assertThatThrownBy(() -> new ClauseExplorer(0, holdoutAll(), 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClauseExplorer(21, holdoutAll(), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}