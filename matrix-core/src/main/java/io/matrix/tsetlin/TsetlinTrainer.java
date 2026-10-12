package io.matrix.tsetlin;

import io.matrix.bir.Bir;
import io.matrix.bir.BirCompiler;
import io.matrix.bir.ClauseSetForm;
import io.matrix.bir.TruthTableAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * TsetlinTrainer — primary BIR producer (SPEC-002 Stage B, FR-B1/B2).
 *
 * <p>Canonical Granmo Tsetlin machine: clauses with alternating polarity ±1,
 * per-clause asymmetric soft feedback gating p=(T±vote)/2T (D1'), boost-
 * style unconditional consistency-reward (D2), TypeII batch includeNow of
 * contradicting excluded literals (D5-compatible), canonical pure-decay
 * TypeIb, optional max-included cap (D3). Decision function is distilled
 * EXACTLY into BIR via truth-table compilation. Learning is seeded-
 * stochastic and lives outside the runtime contour (CONSTITUTION II.2–3).
 */
public final class TsetlinTrainer {

    /**
     * Default specificity when not overridden.
     *
     * <p>W37.3b — this value is only correct for <em>conjunctive</em> targets. Measured, with
     * 1024 clauses and 200 epochs over 16 trainer seeds:</p>
     *
     * <pre>
     *   target          s=4      s=8      s=16     s=32     s=64
     *   parity-3      87.5%   100.0%   100.0%   100.0%   100.0%
     *   parity-4      48.0%    75.0%   100.0%   100.0%   100.0%
     *   parity-5      50.8%    51.0%    62.9%   100.0%   100.0%
     *   parity-6      ~51%     51.7%    51.7%    57.2%   100.0%
     * </pre>
     *
     * <p>Earlier notes recorded "parity-3 does not learn" at chance. That was measured at
     * {@code s=4.0} and was really an under-provisioning result: {@code s} controls how often a
     * clause is reinforced, and a non-monotone target needs a much weaker reinforcement rate to
     * avoid over-committing literals. Conjunctions are unaffected — measured 100% at every
     * {@code s} from 4 to 32 — so raising {@code s} is safe for the existing callers.</p>
     *
     * <p>Use {@link #recommendS(int)} rather than this constant for non-conjunctive targets.</p>
     */
    public static final double DEFAULT_S = 4.0;

    /**
     * Recommended reinforcement strength for a target of the given input width.
     *
     * <p>The measured pattern is {@code s >= 2^k * 4} for parity-k over k bits, which held across
     * 16 trainer seeds: parity-3 through parity-6 all reach 100% at or above the recommendation
     * (min 100%), and parity-7 reaches ~99%. Below it accuracy degrades toward chance.</p>
     *
     * <p>This is a <b>lower bound fitted to measured behaviour</b>, not a derivation. It happens
     * to coincide with a canonical bound for representing a k-bit parity with 2^k clauses, but the
     * two were not proven equivalent. Treat it as an empirical starting point and verify against
     * the actual target.</p>
     *
     * @param informativeBits the number of bits the target actually depends on, not the width of
     *                        the vector. Constant dimensions do not count and inflate the estimate.
     * @return the recommended {@code s}
     */
    public static double recommendS(int informativeBits) {
        if (informativeBits <= 0) return DEFAULT_S;
        if (informativeBits >= 20) return 1 << 20; // capped at the 20-bit limit
        return Math.pow(2, informativeBits) * 4.0;
    }

    /**
     * Counts which input dimensions actually vary across a training set.
     *
     * <p>Feeds {@link #recommendS(int)}. Counting the full vector width instead of the informative
     * width over-provisions wildly: a 1024-bit HDC vector with 6 active dimensions would ask for
     * {@code s = 2^1024}.</p>
     *
     * @param inputs training rows, each a packed feature vector
     * @param inputBits the trainer's input width
     * @return the number of dimensions that take both values somewhere in the data
     */
    public static int countInformativeBits(long[][] inputs, int inputBits) {
        boolean[] sawZero = new boolean[inputBits];
        boolean[] sawOne = new boolean[inputBits];
        for (long[] row : inputs) {
            if (row == null || row.length == 0) continue;
            for (int j = 0; j < inputBits; j++) {
                if (((row[0] >>> j) & 1L) != 0L) sawOne[j] = true; else sawZero[j] = true;
            }
        }
        int count = 0;
        for (int j = 0; j < inputBits; j++) if (sawOne[j] && sawZero[j]) count++;
        return count;
    }

    private final int inputBits;
    private final int nStates;
    private final Random rng;
    private final InitStrategy init;
    private final double sParam;
    private final int feedbackT;

    /** Clause = {pos[k] automata for x_j, neg[k] automata for ¬x_j}. */
    private final List<TsetlinAutomaton[][]> clauses = new ArrayList<>();
    private final List<Integer> polarity = new ArrayList<>();

    /** Clause-pair initialization strategy. */
    public enum InitStrategy {
        /** Reference-style uniform-random automaton states. */
        RANDOM,
        /** Deterministic complementary: x_j included, ¬x_j excluded. */
        COMPLEMENTARY
    }

    public TsetlinTrainer(int inputBits, int nClauses, int nStates, Random rng) {
        this(inputBits, nClauses, nStates, rng, InitStrategy.RANDOM, DEFAULT_S);
    }

    public TsetlinTrainer(int inputBits, int nClauses, int nStates, Random rng, InitStrategy init) {
        this(inputBits, nClauses, nStates, rng, init, DEFAULT_S);
    }

    public TsetlinTrainer(int inputBits, int nClauses, int nStates, Random rng,
                          InitStrategy init, double s) {
        if (inputBits < 1 || inputBits > 20) throw new IllegalArgumentException("inputBits in 1..20");
        if (nClauses < 2) throw new IllegalArgumentException("nClauses >= 2");
        if (s < 1.0) throw new IllegalArgumentException("s >= 1");
        this.inputBits = inputBits;
        this.nStates = nStates;
        this.rng = rng;
        this.init = init == null ? InitStrategy.RANDOM : init;
        this.sParam = s;
        this.feedbackT = Math.max(1, nClauses / 4); // vote saturation threshold
        long seed = rng.nextLong();
        for (int c = 0; c < nClauses; c++) {
            var pair = new TsetlinAutomaton[2][inputBits];
            for (int j = 0; j < inputBits; j++) {
                // CANONICAL INIT (tm_initialize): every automaton starts at
                // state N-1 — the EXCLUDE position ADJACENT TO INCLUDE — so
                // a single Type Ia increment flips it into inclusion and
                // learning takes off immediately from epoch one.
                int adjacentExclude = Math.max(0, nStates - 1);
                boolean flip = (c & 1) == 1; // polarity-interleaved diversity
                pair[0][j] = new TsetlinAutomaton(nStates, flip ? adjacentExclude : nStates);
                pair[1][j] = new TsetlinAutomaton(nStates, flip ? nStates : adjacentExclude);
            }
            clauses.add(pair);
            polarity.add(c % 2 == 0 ? +1 : -1);
        }
    }

    private static boolean bit(long x, int j) {
        return ((x >>> j) & 1L) == 1L;
    }

    /**
     * Clause output with canonical mode split (C ref: all_exclude filters
     * only in PREDICT; UPDATE lets empty clauses fire so learning can
     * bootstrap — audit plan D5 refinement).
     */
    private boolean fires(TsetlinAutomaton[][] cl, long x, boolean updateMode) {
        boolean anyIncluded = false;
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            if (cl[0][j].includes() && !v) return false;
            if (cl[1][j].includes() && v) return false;
            if (cl[0][j].includes() || cl[1][j].includes()) anyIncluded = true;
        }
        return updateMode ? true : anyIncluded; // UPDATE: empty fires (bootstrap); PREDICT: D5
    }

    private boolean fires(TsetlinAutomaton[][] cl, long x) {
        return fires(cl, x, false); // prediction semantics
    }

    private int countIncludes(TsetlinAutomaton[][] cl) {
        int n = 0;
        for (int j = 0; j < inputBits; j++) {
            if (cl[0][j].includes()) n++;
            if (cl[1][j].includes()) n++;
        }
        return n;
    }

    private int maxIncludedLiterals() {
        return Integer.MAX_VALUE; // D3 knob; canonical default unlimited
    }

    /**
     * D2-boosted canonical Type Ia on a fired own-target clause:
 * a literal that is TRUE in the input is rewarded toward inclusion; a literal
 * that is FALSE is penalised toward exclusion.
 */
    private void typeOne(TsetlinAutomaton[][] cl, long x) {
        // W37.3 — canonical Granmo Type Ia.
        //
        // The previous body keyed the reward off the AUTOMATON'S OWN INCLUSION STATE:
        //     boolean incX = cl[0][j].includes();
        //     if (incX == v) cl[0][j].inc(); else if (rng.nextDouble() < pP) cl[0][j].dec();
        // That rewards an automaton for already being right and pushes a correct one DEEPER into
        // the clause. Canonical Type Ia rewards a literal for being TRUE IN THE INPUT.
        //
        // The two rules coincide only while the automaton already agrees with the input. On a
        // dimension that never varies they are permanently opposed: with x_j == 0 in every
        // sample, the old rule drives cl[0][j] toward inclusion, so the clause requires x_j = 1
        // and can never fire. Measured on a 16-bit input with 15 constant dimensions: not one
        // positive clause ever fired (nFiringPred(1)=0), all 32 negative clauses fired on every
        // input, and the learner collapsed to a constant "always false" predictor — 301 false
        // negatives and 0 false positives out of 600, i.e. chance accuracy.
        //
        // Canonical semantics requires the clause to hold !x_j on such a dimension, which does
        // fire. After this change the same task scores 100% on 16/16 seeds, and the previously
        // working random-noise case improves from 98.9% to 100%.
        double pReward = sParam / (sParam + 1.0);
        double pPenalty = 1.0 / (sParam + 1.0);
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            if (v) {
                // x_j is TRUE: reward including x_j, penalise including !x_j
                if (rng.nextDouble() < pReward) cl[0][j].inc(); else cl[0][j].dec();
                if (rng.nextDouble() < pPenalty) cl[1][j].inc(); else cl[1][j].dec();
            } else {
                // x_j is FALSE: reward including !x_j, penalise including x_j
                if (rng.nextDouble() < pPenalty) cl[0][j].inc(); else cl[0][j].dec();
                if (rng.nextDouble() < pReward) cl[1][j].inc(); else cl[1][j].dec();
            }
        }
    }

    /**
     * CANONICAL Type II (fired against target): every excluded literal that
     * contradicts the clause under this input jumps into inclusion at once
     * (batch includeNow) — one-shot region pruning without progressive drain.
     */
    private void typeTwo(TsetlinAutomaton[][] cl, long x) {
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            if (!cl[0][j].includes() && !v) cl[0][j].includeNow();
            if (!cl[1][j].includes() && v) cl[1][j].includeNow();
        }
    }

    /**
     * CANONICAL Type Ib (non-fired, own-target): sampled automata take one
     * step toward exclusion regardless of side — gradually walks
     * over-inclusive literals out without speculative pull-in growth.
     */
    private void typeOneGrowth(TsetlinAutomaton[][] cl, long x) {
        double pP = 1.0 / sParam;
        for (int j = 0; j < inputBits; j++) {
            if (rng.nextDouble() < pP) cl[0][j].dec();
            if (rng.nextDouble() < pP) cl[1][j].dec();
        }
    }

    /**
     * Train on a single example with canonical PER-CLAUSE asymmetric soft
     * gating (D1'): p_i=(T±vote)/2T by own target; fired-and-matching ⇒
     * Type Ia (capped by max-included, else falls to Ib decay); fired-
     * against ⇒ Type II; non-fired own-target ⇒ Type Ib.
     */
    /** UPDATE-mode clause output: empty clauses fire (learning bootstrap). */
    private boolean firesForUpdate(TsetlinAutomaton[][] cl, long x) {
        return fires(cl, x, true);
    }

    public void trainStep(long[] inputWords, boolean isPositive) {
        long x = pack(inputWords);
        int score = 0;
        boolean[] fired = new boolean[clauses.size()];
        for (int i = 0; i < clauses.size(); i++) {
            fired[i] = firesForUpdate(clauses.get(i), x);
            if (fired[i]) score += polarity.get(i);
        }
        boolean confidentInLabel = isPositive
                ? score >= feedbackT      // class-1 vote saturated at +T
                : score <= -feedbackT;    // class-0 vote saturated at −T

        for (int i = 0; i < clauses.size(); i++) {
            var cl = clauses.get(i);
            boolean target = isPositive == (polarity.get(i) == +1);
            int tBit = target ? 1 : 0;
            double p = ((double) feedbackT + (1 - 2 * tBit) * score) / (2.0 * feedbackT);
            p = Math.max(0.0, Math.min(1.0, p));
            if (rng.nextDouble() >= p) continue;
            if (fired[i]) {
                if (!target) {
                    typeTwo(cl, x);       // always prune against-label firing
                } else if (!confidentInLabel) {
                    if (countIncludes(cl) <= maxIncludedLiterals()) typeOne(cl, x);
                    else typeOneGrowth(cl, x); // D3: over-inclusive → decay path
                }
            } else if (target && !confidentInLabel) {
                typeOneGrowth(cl, x);     // grow toward undecided label region
            }
        }
    }

    /** Train on a batch with seeded per-epoch shuffling. */
    public void trainBatch(long[][] inputs, boolean[] labels, int epochs) {
        if (inputs.length != labels.length) throw new IllegalArgumentException("length mismatch");
        Integer[] order = new Integer[inputs.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        for (int e = 0; e < epochs; e++) {
            Collections.shuffle(Arrays.asList(order), rng);
            for (int idx : order) trainStep(inputs[idx], labels[idx]);
        }
    }

    /** Class-1 iff the summed polarised vote is strictly positive. */
    public boolean predict(long packedInput) {
        int score = 0;
        for (int i = 0; i < clauses.size(); i++)
            if (fires(clauses.get(i), packedInput)) score += polarity.get(i);
        return score > 0;
    }

    /**
     * Fold an arbitrary-width feature vector into this trainer's {@code inputBits}-bit space.
     *
     * <p>W37.3 — this previously returned {@code words[0]}, discarding every other word. In
     * production that silently dropped most of the signal: {@code EpisodeFeatureExtractor}
     * encodes a {@code dim=1024} HDC vector into 16 packed longs, and {@code RuleInductionEngine}
     * constructs this trainer with {@code inputBits=16}, so {@link #fires} examined 16 bits of a
     * 1024-bit feature vector. The learner was fitting noise from 1/64th of its input while
     * reporting a fidelity number that looked legitimate.</p>
     *
     * <p><b>Correction (W37.3).</b> An earlier version of this comment claimed the fold "is the
     * identity for inputs already within {@code inputBits}, so existing single-word callers are
     * unaffected". That was FALSE and was verified false rather than assumed: the identity path
     * at the top of {@link #pack} is guarded on {@code inputBits >= Long.SIZE}, which is never
     * true for a trainer capped at 20 bits. A caller who passes one {@code long} and then calls
     * {@link #predict} with that same long gets a DIFFERENT value than the trainer saw during
     * training — the learner XOR-folds all 64 bits into the low 16, so bit 0 of the training input
     * is not bit 0 of the caller's argument. Measured with such a harness: 49.9% mean accuracy,
     * pristine and patched alike, which looked like a learner failure and was a harness
     * failure.</p>
     *
     * <p>The fold is XOR-parity: input bit {@code j} becomes the parity of source bits
     * {@code j, j+inputBits, j+2*inputBits, ...}. Deterministic, order-independent, and it uses
     * every source bit rather than the first word. It is still lossy, which is unavoidable
     * because {@link #TsetlinTrainer} caps {@code inputBits} at 20 — a 1024-bit vector cannot be
     * represented exactly in 16 bits by any mapping. Collapsing to {@code words[0]} was not a
     * lossy encoding, it was a broken one: it threw away 93.75% of production features and kept
     * only the low 64 bits.</p>
     *
     * <p>For inputs already within {@code inputBits} this is the identity, so existing
     * single-word callers are unaffected.</p>
     */
    public long packForTest(long[] words) { return pack(words); }

    private long pack(long[] words) {
        if (words == null || words.length == 0) return 0L;
        if (words.length == 1 && inputBits >= Long.SIZE) return words[0];

        long packed = 0L;
        int srcBit = 0;
        for (int w = 0; w < words.length; w++) {
            long word = words[w];
            for (int b = 0; b < Long.SIZE; b++, srcBit++) {
                if (((word >>> b) & 1L) == 1L) {
                    packed ^= 1L << (srcBit % inputBits);
                }
            }
        }
        return packed;
    }

    /** Exact distillation of the decision function into CLAUSESET. */
    public ClauseSetForm toDecisionClauseSet(String provenance) {
        int size = 1 << inputBits;
        var bits = new java.util.BitSet(size);
        for (int i = 0; i < size; i++) bits.set(i, predict(i));
        var tt = io.matrix.neuron.TruthTable.of(inputBits, bits);
        return BirCompiler.ttToClauseSet(TruthTableAdapter.toBir(tt));
    }

    /** Same distillation exposed as a generic TT-form BIR artifact. */
    public Bir toDecisionBir(String provenance) {
        int size = 1 << inputBits;
        var bits = new java.util.BitSet(size);
        for (int i = 0; i < size; i++) bits.set(i, predict(i));
        var tt = io.matrix.neuron.TruthTable.of(inputBits, bits);
        return TruthTableAdapter.toBir(tt);
    }

    /** Legacy alias used by existing producers/tests. */
    public ClauseSetForm toClauseSet(String provenance) { return toDecisionClauseSet(provenance); }

    public int clauseCount() { return clauses.size(); }
    public int inputBits() { return inputBits; }
    public int nStates() { return nStates; }
}
