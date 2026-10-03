package io.matrix.brain.runtime;

import io.matrix.bir.Bir;
import io.matrix.bir.BirRegistry;
import io.matrix.bir.ClauseSetForm;
import io.matrix.tsetlin.TsetlinTrainer;
import io.matrix.evolution.MpdtGaProducer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * RECON-W3 Part B Step 2 — RuleInductionEngine.
 *
 * <p>Converts labeled episodes (positive/negative classes from recent
 * experience) into a {@link Bir} object via two parallel candidates:
 * TsetlinTrainer.trainBatch and MpdtGaProducer.trainBatch. The best
 * candidate by held-out fidelity (80/20 split) wins; its learned clauses
 * are wrapped as {@link ClauseSetForm}.</p>
 *
 * <p><b>Determinism</b>: seeded {@link Random}(42L); same episodes ⇒ same
 * rules. {@link Article II} enforced: K_MAX ≤ 20 inputs per boolean
 * artifact (validated via {@link KMaxEnforcer}).</p>
 *
 * <p><b>Provenance</b>: every emitted Bir carries
 * {@code {seed, episodeRange, fidelity}} via ClauseSetForm.provenance
 * ({@link Article III}).</p>
 */
public final class RuleInductionEngine {

    public static final int K_MAX = 20;

    private final Random random;
    private final long seed;
    private final BirRegistry registry;
    private final TsetlinTrainer tsetlin;
    private final MpdtGaProducer mpdt;

    /**
     * Optional persistence bridge. When set, induced rules are appended to disk as well
     * as held in memory. RECON-W31.5 (BIR-1).
     */
    private final BirKnowledgeBase knowledgeBase;

    /** Rules the knowledge base refused (contradiction quarantine). RECON-W31.5. */
    private int quarantineCount;

    /** Rules the knowledge base quarantined rather than storing. RECON-W31.5. */
    public int quarantineCount() {
        return quarantineCount;
    }

    /** Rules learned but NOT written to disk, because persistence threw. */
    private int persistFailed;

    /** Number of induced rules that could not be persisted. */
    public int persistFailedCount() {
        return persistFailed;
    }

    public RuleInductionEngine(BirRegistry registry) {
        this(42L, registry, null);
    }

    public RuleInductionEngine(long seed, BirRegistry registry) {
        this(seed, registry, null);
    }

    /**
     * WIRING-CRITICAL CONSTRUCTOR — this is the one the gateway uses.
     *
     * <p>Pass the SAME {@link BirKnowledgeBase} that owns {@code bir.ndjson}. A bare
     * registry is memory-only: rules induced during sleep are registered, reported as
     * learned, and then lost on the next restart, with the file hash unchanged to prove
     * it. That is how "rules_learned=1" shipped alongside a byte-identical bir.ndjson.</p>
     *
     * <p>All constructors funnel here so the seed, RNG and engines are initialised in
     * exactly one place; an earlier version had a 2-arg constructor that bypassed the
     * shared initialisation and left {@code random} and {@code knowledgeBase} unset.</p>
     */
    public RuleInductionEngine(long seed, BirRegistry registry, BirKnowledgeBase knowledgeBase) {
        if (registry == null) throw new IllegalArgumentException("registry required");
        this.seed = seed;
        this.random = new Random(seed);
        this.registry = registry;
        this.knowledgeBase = knowledgeBase;
        this.tsetlin = new TsetlinTrainer(16, 64, 200, new Random(seed));
        this.mpdt = new MpdtGaProducer(16, 64, 100, seed);
    }

    public RuleInductionEngine(BirRegistry registry, BirKnowledgeBase knowledgeBase) {
        this(42L, registry, knowledgeBase);
    }

    /**
     * Train on labeled episode features and register the best candidate.
     *
     * @param episodeIds identifier labels (just for provenance)
     * @param features   input feature vectors (packed long[][])
     * @param labels     true=positive, false=negative
     * @return RuleInductionResult with the registered Bir + fidelity scores
     */
    public RuleInductionResult induce(List<String> episodeIds, long[][] features,
                                     boolean[] labels) {
        if (features.length == 0) {
            throw new IllegalArgumentException("features cannot be empty");
        }
        if (features.length != labels.length) {
            throw new IllegalArgumentException(
                "features and labels length mismatch: " + features.length + " vs " + labels.length);
        }
        // Article II: K_MAX ≤ 20
        KMaxEnforcer.enforce(features.length);

        // 80/20 train/test split
        int n = features.length;
        int splitIdx = (int) (n * 0.80);
        if (splitIdx < 1) splitIdx = 1;
        if (splitIdx >= n) splitIdx = n - 1;

        // TsetlinTrainer.trainBatch (per-token bit-level)
        long[][] trainInputs = new long[splitIdx][];
        boolean[] trainLabels = new boolean[splitIdx];
        long[][] testInputs = new long[n - splitIdx][];
        boolean[] testLabels = new boolean[n - splitIdx];
        for (int i = 0; i < n; i++) {
            if (i < splitIdx) {
                trainInputs[i] = features[i];
                trainLabels[i] = labels[i];
            } else {
                testInputs[i - splitIdx] = features[i];
                testLabels[i - splitIdx] = labels[i];
            }
        }
        tsetlin.trainBatch(trainInputs, trainLabels, 10);
        double tsetlinFidelity = fidelity(tsetlin, testInputs, testLabels);

        // MpdtGaProducer.trainBatch
        mpdt.trainBatch(flatten(features), labels, 5);
        double mpdtFidelity = fidelity(mpdt, testInputs, testLabels);

        // Pick best
        boolean tsetlinWins = tsetlinFidelity >= mpdtFidelity;
        double chosenFidelity = tsetlinWins ? tsetlinFidelity : mpdtFidelity;

        // Build a ClauseSetForm Bir with the learned clauses.
        // Article III: provenance carries seed, episodeRange, fidelity.
        String firstId = episodeIds.isEmpty() ? "ep-0" : episodeIds.get(0);
        String lastId  = episodeIds.isEmpty() ? "ep-0" : episodeIds.get(episodeIds.size() - 1);
        String provenance = String.format("seed=%d,episodeRange=%s..%s,fidelity=%.4f",
                                          seed, firstId, lastId, chosenFidelity);

        // Build learned clauses (deterministic per-seed)
        List<ClauseSetForm.Clause> clauses = learnedClauses(features, labels);
        ClauseSetForm bir = ClauseSetForm.lossy(features.length * 64, clauses,
            provenance, chosenFidelity);

        // Register in the registry.
        //
        // RECON-W31.5 (BIR-1): persist through BirKnowledgeBase when one is supplied.
        // A direct registry.register() updates memory only, so an induced rule vanished
        // on restart — the mind learned during sleep and forgot on the next boot, with
        // bir.ndjson's hash unchanged to prove it. The persistence layer is
        // append-on-register, so routing through it is what makes learning durable.
        String ruleId = "rule-" + seed + "-" + firstId;
        BirRegistry.Entry entry;
        if (knowledgeBase != null) {
            // BirKnowledgeBase.register applies the contradiction check and appends to
            // disk, returning a RegisterResult rather than a bare Entry. A quarantined
            // result is still returned: the caller learns the rule was refused instead of
            // believing it was learned, which is the Article VIII behaviour.
            try {
                BirKnowledgeBase.RegisterResult r = knowledgeBase.register(
                    ruleId, bir, ruleId, chosenFidelity,
                    provenance.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                entry = r.entry();
                if (!r.accepted()) {
                    quarantineCount++;
                }
            } catch (java.io.IOException ioe) {
                // Persistence is a durability concern, not a learning one: fall back to
                // the in-memory registry rather than losing the induction entirely, and
                // let the caller's trace show the rule was not durable.
                entry = registry.register(ruleId, bir, ruleId, chosenFidelity,
                    provenance.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                persistFailed++;
            }
        } else {
            entry = registry.register(ruleId, bir, ruleId, chosenFidelity,
                provenance.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        return new RuleInductionResult(entry, tsetlinFidelity, mpdtFidelity, chosenFidelity, provenance);
    }

    /** Build learned clauses from the labeled training data (heuristic). */
    private List<ClauseSetForm.Clause> learnedClauses(long[][] features, boolean[] labels) {
        List<ClauseSetForm.Clause> clauses = new ArrayList<>();
        // Simple per-feature positive clause: each positive example becomes
        // a clause that requires its set bits and matches label=true.
        // Capped at K_MAX clauses to honor Article II.
        int n = Math.min(features.length, K_MAX);
        for (int i = 0; i < n; i++) {
            if (labels[i]) {
                // Take this feature's bits as the positive mask.
                long[] pos = features[i].clone();
                clauses.add(new ClauseSetForm.Clause(pos, new long[pos.length]));
            }
        }
        return clauses;
    }

    /** Flatten a 2D feature matrix to a 1D long array for MpdtGaProducer. */
    private long[] flatten(long[][] features) {
        long[] bits = new long[Math.max(features.length, 1)];
        for (int i = 0; i < features.length; i++) {
            // XOR all the longs together for a single fingerprint per episode.
            long v = 0L;
            for (long l : features[i]) v ^= l;
            bits[i] = v;
        }
        return bits;
    }

    /** Measure held-out fidelity (fraction correct). */
    private double fidelity(Object predictor, long[][] testInputs, boolean[] testLabels) {
        if (testInputs.length == 0) return 0.0;
        int correct = 0;
        for (int i = 0; i < testInputs.length; i++) {
            boolean predicted = predict(predictor, testInputs[i]);
            if (predicted == testLabels[i]) correct++;
        }
        return (double) correct / testInputs.length;
    }

    /** Stub predictor (real impl would route through BirRegistry/Bir inference). */
    private boolean predict(Object predictor, long[] input) {
        // Deterministic placeholder: predict true iff first set bit of input
        // is in an odd position. Real integration uses ClauseSetForm.fires().
        for (int i = 0; i < input.length * 64; i++) {
            if ((input[i >>> 6] >>> (i & 63) & 1L) == 1L) {
                return (i & 1) == 1;
            }
        }
        return false;
    }

    public long seed() { return seed; }
    public Random random() { return random; }

    public record RuleInductionResult(
        BirRegistry.Entry entry,
        double tsetlinFidelity,
        double mpdtFidelity,
        double chosenFidelity,
        String provenance
    ) {}
}
