package io.matrix.brain.runtime.graph;

import io.matrix.tsetlin.TsetlinTrainer;
import java.util.BitSet;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * S5.1 / S5.3 (RECON-W38.3) — the seeded Gibbs sampler, and its parity claim against the
 * hill-climb baseline at parity k &gt;= 7.
 *
 * <p>S5.3 in the backlog asks for a "parity k&gt;=7 convergence test vs hill-climb baseline".
 * That is read here literally and measured: the same under-provisioned trainer, the same
 * held-out set, the same starting truth table, and the two search procedures run against each
 * other. Parity means the sampler reaches at least the hill climb's held-out accuracy. It does
 * not mean the sampler is better, and these tests do not claim it is.</p>
 *
 * <p><b>Measured limitation of the whole benchmark.</b> Over 11 seeds and widths k=4..7 the hill
 * climb solves parity as reliably as the sampler does, 44 of 44 perfect runs for both, and both
 * are already perfect at a matched budget of 32 candidate visits at k=7. The parity criterion is
 * therefore met but cannot rank the two methods. The cause is structural: optimising the truth
 * table gives a separable loss, so coordinate ascent is optimal in one pass and no local optimum
 * exists for a sampler to escape. See
 * {@link #parityHoldsAcrossSeedsAndWidths()} for the full statement. Demonstrating an actual MCMC
 * advantage requires sampling over interacting clause automata, which is NOT implemented.</p>
 */
class ClauseGibbsSamplerTest {

    /** Parity over k bits: the target whose clause space the default trainer cannot learn. */
    private static ClauseExplorer.Example[] parity(int k) {
        int n = 1 << k;
        ClauseExplorer.Example[] ex = new ClauseExplorer.Example[n];
        for (int i = 0; i < n; i++) {
            ex[i] = new ClauseExplorer.Example(i, (Integer.bitCount(i) & 1) == 1);
        }
        return ex;
    }

    /** The default configuration, deliberately under-provisioned, so repair is measurable. */
    private static TsetlinTrainer underProvisioned(int k, double s) {
        ClauseExplorer.Example[] ex = parity(k);
        long[][] inputs = new long[ex.length][];
        boolean[] labels = new boolean[ex.length];
        for (int i = 0; i < ex.length; i++) {
            inputs[i] = new long[] {i};
            labels[i] = ex[i].label();
        }
        var t = new TsetlinTrainer(k, 64, 200, new Random(42L),
                TsetlinTrainer.InitStrategy.RANDOM, s);
        t.trainBatch(inputs, labels, 200);
        return t;
    }

    @Test
    @DisplayName("the same seed reproduces the same trajectory exactly")
    void sameSeedReproducesTrajectory() {
        ClauseExplorer.Example[] holdout = parity(4);
        BitSet start = ClauseExplorer.truthTableOf(underProvisioned(4, 4.0), 4);

        ClauseGibbsSampler a = new ClauseGibbsSampler(4, holdout, 7L);
        ClauseGibbsSampler b = new ClauseGibbsSampler(4, holdout, 7L);
        ClauseGibbsSampler.Result ra = a.sample(start, 6, 4.0, 0.5);
        ClauseGibbsSampler.Result rb = b.sample(start, 6, 4.0, 0.5);

        // Compare the TABLES THE CHAIN PRODUCED. Comparing the arguments would pass trivially:
        // sample() never writes through its argument, so the input copies stay equal no matter
        // what the sampler does. That mistake is what made this test vacuous the first time.
        assertThat(ra.entriesChanged())
                .as("a precondition worth stating: on parity-4 at these temperatures the chain "
                        + "must actually move, or the equality below would be vacuous again")
                .isPositive();
        assertThat(rb.table()).as("same seed must give the same table, entry for entry")
                .isEqualTo(ra.table());
        assertThat(rb.entriesChanged()).isEqualTo(ra.entriesChanged());
        assertThat(rb.accuracyAfter()).isEqualTo(ra.accuracyAfter());
    }

    /**
     * A holdout carrying one TRUE-labelled and one FALSE-labelled example at every input.
     *
     * <p>Then both energy levels are equal at every entry, so every Gibbs conditional is exactly
     * one half at any temperature and the chain is a pure coin flip. That is what makes the seed
     * observable: on a solvable parity target both seeds simply converge to the same perfect
     * table, which would hide the seed behind the answer.</p>
     */
    private static ClauseExplorer.Example[] balanced(int k) {
        int n = 1 << k;
        ClauseExplorer.Example[] ex = new ClauseExplorer.Example[n * 2];
        for (int i = 0; i < n; i++) {
            ex[2 * i] = new ClauseExplorer.Example(i, true);
            ex[2 * i + 1] = new ClauseExplorer.Example(i, false);
        }
        return ex;
    }

    @Test
    @DisplayName("a different seed produces a different trajectory")
    void differentSeedDiffers() {
        ClauseExplorer.Example[] holdout = balanced(4);
        BitSet empty = new BitSet(16);

        BitSet a = new ClauseGibbsSampler(4, holdout, 1L).sample(empty, 6, 4.0, 0.5).table();
        BitSet b = new ClauseGibbsSampler(4, holdout, 999L).sample(empty, 6, 4.0, 0.5).table();

        assertThat(b)
                .as("with every conditional at exactly one half the chain is a coin flip, so "
                        + "distinct seeds must diverge; if the seed were ignored they would match")
                .isNotEqualTo(a);
    }

    @Test
    @DisplayName("a balanced holdout has no energy signal to follow, so it pins at chance")
    void balancedHoldoutPinsAtChance() {
        // This is the honest cost of the design: with one example each way, P(true) is exactly 0.5
        // and the sampler is pure noise. Recorded so the seed test above cannot be mistaken for a
        // claim that the sampler finds structure by itself.
        ClauseExplorer.Example[] holdout = balanced(4);
        ClauseGibbsSampler sampler = new ClauseGibbsSampler(4, holdout, 3L);
        BitSet empty = new BitSet(16);
        BitSet a = sampler.sample(empty, 20, 4.0, 0.05).table();
        BitSet b = sampler.sample(empty, 20, 4.0, 0.05).table();
        assertThat(sampler.accuracyOf(a))
                .as("no table can satisfy a contradictory holdout")
                .isEqualTo(0.5);
        assertThat(b).as("same seed still reproduces exactly, noise and all").isEqualTo(a);
    }

    @Test
    @DisplayName("the Gibbs conditional is the logistic of the entry's own error difference")
    void conditionalMatchesTheClosedForm() {
        // no examples: both costs zero, so the conditional is exactly one half at any temperature.
        assertThat(ClauseGibbsSampler.probabilityTrue(0, 0, 1.0)).isEqualTo(0.5);
        assertThat(ClauseGibbsSampler.probabilityTrue(0, 0, 0.01)).isEqualTo(0.5);
        // one TRUE-labelled and zero FALSE-labelled example at this entry.
        // Setting the entry TRUE costs E=0; setting it FALSE costs E=1. So P(true) = 1/(1+exp(-1/T)),
        // which is a strong preference for TRUE but NOT certainty at a finite temperature.
        assertThat(ClauseGibbsSampler.probabilityTrue(1, 0, 1.0))
                .isEqualTo(1.0 / (1.0 + Math.exp(-1.0)));
        assertThat(ClauseGibbsSampler.probabilityTrue(1, 0, 1.0))
                .as("the cheaper value is preferred, but a finite temperature never reaches certainty")
                .isGreaterThan(0.7);
        assertThat(ClauseGibbsSampler.probabilityTrue(1, 0, 0.01))
                .as("cold chain still prefers the zero-cost value")
                .isGreaterThan(0.999);
        // the mirror image: one FALSE-labelled and zero TRUE-labelled, so P(true) is the complement
        assertThat(ClauseGibbsSampler.probabilityTrue(0, 1, 1.0))
                .isEqualTo(1.0 / (1.0 + Math.exp(1.0)));
        // and the large-cost case must not overflow into NaN
        assertThat(ClauseGibbsSampler.probabilityTrue(0, 1000, 1.0)).isEqualTo(0.0);
        assertThat(ClauseGibbsSampler.probabilityTrue(1000, 0, 1.0)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a colder chain concentrates harder on the entry's lower-energy value")
    void coldConcentratesOnTheCheaperValue() {
        // One TRUE-labelled and TWO FALSE-labelled examples at this entry.
        // Setting the entry TRUE costs E=2; setting it FALSE costs E=1. FALSE is cheaper, so P(true)
        // is below one half, and cooling pushes it FURTHER down, not up.
        double hot = ClauseGibbsSampler.probabilityTrue(1, 2, 100.0);
        double cold = ClauseGibbsSampler.probabilityTrue(1, 2, 0.1);
        assertThat(hot).as("at high temperature the chain is nearly indifferent").isCloseTo(0.5, within(0.02));
        assertThat(hot).as("below one half because FALSE is the cheaper value").isLessThan(0.5);
        assertThat(cold).as("at low temperature the cheaper value dominates").isLessThan(hot);
        // and the mirror configuration must sit above one half and rise as it cools
        assertThat(ClauseGibbsSampler.probabilityTrue(2, 1, 0.1))
                .as("two TRUE-labelled against one FALSE-labelled, so TRUE is cheaper here")
                .isGreaterThan(ClauseGibbsSampler.probabilityTrue(2, 1, 100.0));
    }

    @Test
    @DisplayName("a cold chain driving parity-7 solves it, at parity with the hill climb")
    void coldChainSolvesParity7AtParityWithHillClimb() {
        int k = 7;
        ClauseExplorer.Example[] holdout = parity(k);
        BitSet start = ClauseExplorer.truthTableOf(underProvisioned(k, 4.0), k);
        double accuracyBefore = new ClauseGibbsSampler(k, holdout, 1L).accuracyOf(start);

        ClauseGibbsSampler sampler = new ClauseGibbsSampler(k, holdout, 20260119L);
        ClauseGibbsSampler.Result gibbs = sampler.sample(start, 40, 2.0, 0.05);
        BitSet gibbsTable = gibbs.table();

        ClauseExplorer.Result climb =
                new ClauseExplorer(k, holdout, 20260119L).explore((BitSet) start.clone(), 20);

        System.out.printf(
                "  parity-%d: start %.1f%% | gibbs %.1f%% (%d entries changed) | hill-climb %.1f%%%n",
                k, accuracyBefore * 100.0, gibbs.accuracyAfter() * 100.0,
                gibbs.entriesChanged(), climb.accuracyAfter() * 100.0);

        assertThat(gibbs.accuracyAfter())
                .as("S5.3 parity claim: the sampler must reach at least the hill climb's "
                        + "held-out accuracy at k>=7")
                .isGreaterThanOrEqualTo(climb.accuracyAfter());
        assertThat(gibbs.accuracyAfter())
                .as("the target is satisfiable, so a cold chain must find it")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("the sampler never returns a worse table than it was given")
    void neverDegradesTheStartingTable() {
        // A stochastic sampler CAN move uphill, so this is a real risk, not a tautology.
        // The contract is that the caller keeps the best state, not that the chain is monotone.
        int k = 5;
        ClauseExplorer.Example[] holdout = parity(k);
        BitSet start = ClauseExplorer.truthTableOf(underProvisioned(k, 4.0), k);
        ClauseGibbsSampler sampler = new ClauseGibbsSampler(k, holdout, 5L);
        ClauseGibbsSampler.Result r = sampler.sample(start, 15, 5.0, 0.2);

        assertThat(r.accuracyAfter())
                .as("after annealing from hot to cold the chain should have settled at or "
                        + "above where it started")
                .isGreaterThanOrEqualTo(r.accuracyBefore());
    }

    @Test
    @DisplayName("a saturated table is reached by the chain and stays there")
    void chainReachesAndHoldsThePerfectTable() {
        int k = 3;
        ClauseExplorer.Example[] holdout = parity(k);
        ClauseGibbsSampler sampler = new ClauseGibbsSampler(k, holdout, 11L);
        BitSet empty = new BitSet(1 << k);
        ClauseGibbsSampler.Result r = sampler.sample(empty, 40, 3.0, 0.05);

        assertThat(r.accuracyAfter()).isEqualTo(1.0);
        assertThat(r.converged())
                .as("a cold chain on a satisfiable target stops changing entries")
                .isTrue();
    }

    @Test
    @DisplayName("illegal schedules and missing inputs are rejected, not silently repaired")
    void rejectsIllegalArguments() {
        ClauseExplorer.Example[] holdout = parity(3);
        ClauseGibbsSampler sampler = new ClauseGibbsSampler(3, holdout, 1L);
        BitSet table = new BitSet(8);

        assertThatThrownBy(() -> new ClauseGibbsSampler(0, holdout, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClauseGibbsSampler(3, new ClauseExplorer.Example[0], 1L))
                .as("a sampler with no holdout has nothing to minimise")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClauseGibbsSampler(3, null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sampler.sample(null, 3, 1.0, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sampler.sample(table, 0, 1.0, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sampler.sample(table, 3, 0.0, 0.5))
                .as("a zero temperature would divide by zero in the conditional")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sampler.sample(table, 3, 0.5, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sampler.sample(table, 3, 0.5, 2.0))
                .as("cooling must not raise the temperature")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an example outside the table width is rejected at construction")
    void rejectsOutOfRangeExamples() {
        ClauseExplorer.Example[] bad = {
            new ClauseExplorer.Example(99L, true),
        };
        assertThatThrownBy(() -> new ClauseGibbsSampler(3, bad, 1L))
                .as("a truth table for 3 bits has 8 entries; index 99 cannot be represented")
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The S5.3 claim across many seeds and every width from 4 to 7, not one lucky seed.
     *
     * <p><b>This test also records a negative result, and it is the important one.</b> Measured
     * over 11 seeds x 4 widths = 44 comparisons, the Gibbs chain and the hill climb both reach
     * held-out accuracy 1.00 every single time. A budget-limited re-measurement found the same: at
     * a matched budget as low as a quarter of the truth table, 32 candidate visits at k=7, both
     * methods are already perfect. The parity criterion is therefore satisfied but
     * <b>non-discriminative</b>: this benchmark cannot show that Gibbs sampling is better than the
     * hill climb, because the hill climb never loses.</p>
     *
     * <p>The reason is structural, not statistical. The sampler optimises the Tsetlin machine's
     * <em>truth table</em>, and the held-out loss depends on table entry i only through the
     * examples presented at input i. The objective is therefore <b>separable</b>: every entry's
     * optimal value can be set without disturbing any other. Coordinate ascent, which is exactly
     * what {@link ClauseExplorer#explore} does, is already optimal in one full pass, so there is
     * no local optimum for a sampler to escape. The point of Gibbs sampling is escaping
     * correlated local optima, and that benefit cannot manifest on a separable objective.</p>
     *
     * <p>So the honest reading of S5.3: the sampler meets the stated acceptance criterion, and the
     * criterion is too weak to demonstrate the property it was written to demonstrate. A
     * benchmark that could discriminate would need interacting entries, that is a representation
     * with clause automata rather than the truth table they induce. That is explicitly out of
     * scope here and is recorded as unimplemented, not as a limitation of the sampler.</p>
     */
    @Test
    @DisplayName("parity holds across 11 seeds and every width from 4 to 7, and so does the hill climb")
    void parityHoldsAcrossSeedsAndWidths() {
        long[] seeds = {1L, 7L, 13L, 42L, 101L, 999L, 20260119L, 555555L, 3L, 64L, 8675309L};

        for (int k : new int[] {4, 5, 6, 7}) {
            ClauseExplorer.Example[] holdout = parity(k);
            BitSet start = ClauseExplorer.truthTableOf(underProvisioned(k, 4.0), k);

            for (long seed : seeds) {
                ClauseGibbsSampler.Result gibbs =
                        new ClauseGibbsSampler(k, holdout, seed)
                                .sample((BitSet) start.clone(), 40, 2.0, 0.05);
                double climb = new ClauseExplorer(k, holdout, seed)
                        .explore((BitSet) start.clone(), 20).accuracyAfter();

                assertThat(gibbs.accuracyAfter())
                        .as("k=" + k + " seed=" + seed + ": the target is satisfiable, so a cold "
                                + "chain must solve it from the under-provisioned start")
                        .isEqualTo(1.0);
                assertThat(gibbs.accuracyAfter())
                        .as("k=" + k + " seed=" + seed + ": the S5.3 parity claim")
                        .isGreaterThanOrEqualTo(climb);
                assertThat(climb)
                        .as("k=" + k + " seed=" + seed + ": measured, the hill climb is ALSO perfect "
                                + "here, which is exactly why this benchmark cannot rank the two "
                                + "methods; if this assertion ever fails the benchmark has become "
                                + "discriminative and the claim above must be revisited")
                        .isEqualTo(1.0);
            }
        }
    }
}
