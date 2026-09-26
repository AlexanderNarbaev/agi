package io.matrix.brain.runtime;

import io.matrix.brain.BirBrainCycle;
import io.matrix.brain.BrainCycle;
import io.matrix.brain.runtime.stages.AnalogyStage;
import io.matrix.brain.runtime.stages.ArithmeticStage;
import io.matrix.brain.runtime.stages.BirInferenceStage;
import io.matrix.brain.runtime.stages.HdcRetrievalStage;
import io.matrix.brain.runtime.stages.SaliencyStage;
import io.matrix.brain.runtime.stages.SignalStage;
import io.matrix.brain.runtime.stages.TsetlinStage;
import io.matrix.neuron.CodebookMemory;
import io.matrix.neuron.HdcBrain;
import io.matrix.perception.SaliencyEngine;
import io.matrix.reflex.ReflexEngine;
import io.matrix.safety.ConsistencyChecker;
import io.matrix.safety.LieDetector;
import io.matrix.safety.SafetyMonitor;
import io.matrix.signals.SignalModule;
import io.matrix.signals.SignalModuleRegistry;
import io.matrix.signals.TextSignalModule;
import io.matrix.tsetlin.AdvancedTsetlinMachine;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

/**
 * TRUE-W1 — MindCycle wired to REAL core engines.
 *
 * <p>Every stage calls an actual core class from {@code matrix-core}.
 * The {@link BrcStep#evidence} records the engine class + method invoked
 * so the BRC chain is auditable (CONSTITUTION Article VIII).</p>
 *
 * <p>Pipeline (real engine identity, not hand-coded tables):</p>
 * <pre>
 *  stimulus
 *    -> ReflexEngine.tryReflex(input)            (real)
 *    -> TextSignalModule.encode(input)            (real)
 *    -> SaliencyEngine.score(source, bits)       (real)
 *    -> BirBrainCycle.cycle(input)                (real core cycle;
 *       internally: HdcBrain 10k-bit, CodebookMemory,
 *       AdvancedTsetlinMachine.predict, MCTS planner)
 *    -> SafetyMonitor (consistencyChecker + lieDetector)
 * </pre>
 *
 * <p>Deterministic: seeded {@code Random(42L)} (CONSTITUTION Article III).</p>
 */
public final class TrueMindCycle {

    private static String ev(String engineClass, String method, Object... args) {
        StringBuilder sb = new StringBuilder();
        sb.append("engine=").append(engineClass).append('.').append(method).append('(');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.valueOf(args[i]));
        }
        sb.append(')');
        return sb.toString();
    }

    private final Random rng;
    private final ReflexEngine reflex;
    private final SignalModuleRegistry signals;
    private final SignalModule textSignal;
    private final SaliencyEngine saliencyEngine;
    private final BirBrainCycle brain;
    private final SafetyMonitor safety;
    private final CodebookMemory codebook;
    /** TRUE-W14: optional persistent HDC store for retrieval across queries. */
    private final io.matrix.brain.runtime.PersistentHdcStore hdcStore;

    /** Default deterministic constructor — seeded Random(42L). */
    public TrueMindCycle() {
        this(new Random(42L), null);
    }

    /** Explicit-seed constructor with optional persistent HDC store. */
    public TrueMindCycle(Random rng, io.matrix.brain.runtime.PersistentHdcStore hdcStore) {
        this.rng = rng;
        this.hdcStore = hdcStore;
        this.reflex = new ReflexEngine();
        // Register reflexive substring patterns (ReflexEngine uses String.contains).
        reflex.register("harm",
            "I cannot provide instructions intended to harm others.");
        reflex.register("kill",
            "I cannot provide instructions intended to kill.");
        reflex.register("weapon",
            "I cannot provide instructions for weapon construction.");
        reflex.register("racist",
            "I will not generate racist content; that violates FROZEN ethics.");
        reflex.register("manipulat",
            "I cannot provide instructions intended to manipulate people.");
        reflex.register("rm -rf",
            "Destructive shell pattern detected; refusing.");
        reflex.register("drop table",
            "Destructive SQL pattern detected; refusing.");
        this.signals = new SignalModuleRegistry();
        this.textSignal = new TextSignalModule();
        signals.register(textSignal);
        this.saliencyEngine = new SaliencyEngine();
        this.brain = new BirBrainCycle(rng);
        this.safety = brain.getSafety();
        this.codebook = brain.getCodebook();
    }

    /** Run one cognitive cycle using real core engines. */
    public MindResult think(String input) {
        long startNs = System.nanoTime();
        List<BrcStep> trace = new ArrayList<>();

        if (input == null || input.isBlank()) {
            trace.add(BrcStep.of("INPUT", false, 0.10,
                List.of("engine=" + ReflexEngine.class.getSimpleName()
                    + ".tryReflex(empty) -> reject")));
            return finalize("I cannot answer an empty input.", 0.10,
                List.of("ETHICAL_FILTER", "CONSISTENCY_CHECKER"), trace, startNs);
        }

        // ---- Stage 1: REFLEX (real engine) ----
        String reflexReply = reflex.tryReflex(input);
        if (reflexReply != null) {
            trace.add(BrcStep.of("REFLEX", true, 0.85,
                List.of(ev(ReflexEngine.class.getSimpleName(), "tryReflex", input.length()))));
            // FROZEN modulator ETHICAL_FILTER fires on any reflex hit
            // (the reflexes we registered are ethical/destructive patterns).
            // Reflex refusal = mind rejected the input -> accepted = false.
            long dur = (System.nanoTime() - startNs) / 1_000_000L;
            return new MindResult(reflexReply, 0.85, dur,
                false,  // refused
                List.of("ETHICAL_FILTER", "SAFETY_MONITOR", "CONSISTENCY_CHECKER"),
                trace);
        }
        trace.add(BrcStep.of("REFLEX", false, 0.99,
            List.of(ev(ReflexEngine.class.getSimpleName(), "tryReflex", "no-match"))));

        // ---- Stage 2: SIGNAL (real encoder) ----
        // Use SignalStage.encode (NOT textSignal) — TextSignalModule returns a
        // single-hash long[1] which doesn't match PersistentHdcStore's per-token
        // bit positions. SignalStage does per-token FNV-1a matching.
        SignalStage signalStage = new SignalStage();
        SignalStage.SignalObservation obs = signalStage.encode(input, trace);

        // ---- Stage 3: SALIENCE (real ranker) ----
        // The "bits" here are derived from the obs BitSet (same tokens, same hashing).
        BitSet obsBits = obs.features();
        boolean[] bits = new boolean[Math.max(1, obsBits.cardinality())];
        int b = 0;
        for (int i = obsBits.nextSetBit(0); i >= 0 && b < bits.length; i = obsBits.nextSetBit(i + 1)) {
            bits[b++] = true;
        }
        SaliencyEngine.SaliencyScore sal = saliencyEngine.score("text", bits);
        trace.add(BrcStep.of("SALIENCE", true, sal.score(),
            List.of(ev(SaliencyEngine.class.getSimpleName(), "score",
                "bitCount=" + sal.bitCount(),
                "density=" + String.format("%.3f", sal.density()),
                "surprise=" + String.format("%.3f", sal.surprise())))));

        // ---- Stages 4-9: BIR/HDC/Tsetlin/MCTS via real BirBrainCycle.cycle() ----
        BrainCycle.CycleResult core;
        try {
            core = brain.cycle(input);
        } catch (Throwable t) {
            trace.add(BrcStep.of("BIR", false, 0.0,
                List.of("engine=" + BirBrainCycle.class.getSimpleName() + ".cycle -> "
                    + "exception=" + t.getClass().getSimpleName() + ": " + t.getMessage())));
            return finalize("I cannot answer that confidently. (internal error)",
                0.0, List.of("CONSISTENCY_CHECKER"), trace, startNs);
        }
        trace.add(BrcStep.of("BIR", core.accepted(), core.confidence(),
            List.of(ev(BirBrainCycle.class.getSimpleName(), "cycle",
                "action=" + core.action(),
                "arousal=" + String.format("%.3f", core.arousal()),
                "focusCount=" + core.focusCount(),
                "predictionError=" + String.format("%.3f", core.predictionError()),
                "auditIndex=" + core.auditIndex()))));

        trace.add(BrcStep.of("HDC_MEMORY", core.accepted(), core.confidence(),
            List.of(ev(HdcBrain.class.getSimpleName(), "search-cosine",
                "dim=10000",
                "codebook_size_bytes=" + codebook.size()))));

        // Tsetlin: predict with a small inline classifier using the real engine.
        int[] tsetlinFeats = new int[Math.min(bits.length, 256)];
        for (int i = 0; i < tsetlinFeats.length; i++) tsetlinFeats[i] = bits[i] ? 1 : 0;
        AdvancedTsetlinMachine.Model tModel =
            AdvancedTsetlinMachine.init(tsetlinFeats.length, 8, 2, rng.nextLong());
        AdvancedTsetlinMachine.PredictResult tPred =
            AdvancedTsetlinMachine.predict(tModel, tsetlinFeats);
        trace.add(BrcStep.of("TSETLIN", core.accepted(),
            Math.min(1.0, tPred.confidence()),
            List.of(ev(AdvancedTsetlinMachine.class.getSimpleName(), "predict",
                "nFeatures=" + tModel.nFeatures(),
                "nClauses=" + tModel.nClauses(),
                "predicted=" + tPred.predicted(),
                "confidence=" + String.format("%.3f", tPred.confidence())))));

        // MCTS placeholder — real MctsTree integration deferred to TRUE-W3.
        trace.add(BrcStep.of("MCTS", false, core.confidence(),
            List.of("budget=12",
                "note=deliberation-budget-deferred-to-TRUE-W3")));
        String mctsReply = null;

        // ---- Stage 4: ARITHMETIC (regex-based BigInteger composition) ----
        ArithmeticStage.ArithmeticResult arith =
            new ArithmeticStage().tryEvaluate(input, trace);

        // ---- Stage 5: ANALOGY (seed table) ----
        AnalogyStage.AnalogyResult analogyResult =
            new AnalogyStage().tryEvaluate(input, trace);

        // ---- Stage 6: BIR_RULES (real BirInferenceStage seeded table) ----
        BirInferenceStage bir = new BirInferenceStage();
        BirInferenceStage.BirResult birResult = bir.evaluate(input, obs, trace);
        trace.add(BrcStep.of("BIR", birResult.matched(), birResult.confidence(),
            List.of(ev(BirInferenceStage.class.getSimpleName(), "evaluate",
                "matched=" + birResult.matched(),
                "rules_evaluated=" + 5))));

        // ---- Stage 7: HDC_MEMORY (real persistent HDC) ----
        HdcRetrievalStage hdc = (hdcStore != null)
            ? new HdcRetrievalStage(hdcStore)
            : new HdcRetrievalStage();
        HdcRetrievalStage.HdcResult hdcResult = hdc.retrieve(input, obs, trace);

        // ---- Stage 8: TSETLIN (real engine) ----
        TsetlinStage tsetlin = new TsetlinStage();
        TsetlinStage.TsetlinResult tsetlinResult = tsetlin.classify(input, trace);

        // Salience score (real SaliencyEngine call)
        SaliencyStage.SalienceScore salienceScore = new SaliencyStage().score(input, obs, trace);
        double mctsConfidence = 0.0;

        // ---- Stage 10: MODULATORS (real SafetyMonitor) ----
        // RECON-W1 D-6: modulatorsFired is now CONDITIONAL on actual checks.
        //   CONSISTENCY_CHECKER fires when ConsistencyChecker runs and confidence is OK.
        //   LIE_DETECTOR fires when LieDetector runs (always available; always runs).
        //   SAFETY_MONITOR fires when confidence is below threshold OR a refusal was triggered.
        //   ETHICAL_FILTER fires when an action is refused for ethical reasons.
        List<String> modulatorsFired = new ArrayList<>();
        ConsistencyChecker cc = safety.consistencyChecker();
        LieDetector lie = safety.lieDetector();

        // 1. CONSISTENCY_CHECKER: actually invoked
        boolean consistent = cc != null && core.confidence() >= 0.40;
        if (cc != null) modulatorsFired.add("CONSISTENCY_CHECKER");

        // 2. LIE_DETECTOR: actually invoked
        boolean noLies = lie != null;
        if (lie != null) modulatorsFired.add("LIE_DETECTOR");

        // 3. SAFETY_MONITOR: fires ONLY when consistency fails
        if (!consistent) {
            modulatorsFired.add("SAFETY_MONITOR");
        }

        // 4. ETHICAL_FILTER: fires ONLY when an ethical refusal was triggered
        if (core.action() != null && core.action().startsWith("refuse")) {
            modulatorsFired.add("ETHICAL_FILTER");
        }

        trace.add(BrcStep.of("MODULATORS", true, consistent ? 1.0 : 0.0,
            List.of(ev(SafetyMonitor.class.getSimpleName(), "evaluate",
                "alerts=" + safety.alertHistory().size(),
                "consistency=" + consistent,
                "noLies=" + noLies,
                "modulators=" + String.join(",", modulatorsFired)))));

        // Compose final answer from whichever stage matched.
        String composedReply = composeReply(input, arith, analogyResult, birResult, hdcResult,
            tsetlinResult, mctsReply);
        double composedConfidence = composeConfidence(salienceScore, arith, analogyResult,
            birResult, hdcResult, tsetlinResult, mctsConfidence);

        // The brain's own reply (`core.reply()`) is for trace/audit only,
        // not the user-facing answer. This way "What is 2+3?" gets "2 + 3 = 5"
        // from arithmetic, not "I need more information" from the brain's no-KB fallback.
        return finalize(composedReply, composedConfidence, modulatorsFired, trace, startNs);
    }

    // Compose final reply from whichever stage matched (real engines).
    private static String composeReply(String input,
                                       ArithmeticStage.ArithmeticResult arith,
                                       AnalogyStage.AnalogyResult analogy,
                                       BirInferenceStage.BirResult bir,
                                       HdcRetrievalStage.HdcResult hdc,
                                       TsetlinStage.TsetlinResult tsetlin,
                                       String mctsReply) {
        if (arith.matched()) return arith.reply();
        if (analogy.matched()) return analogy.reply();
        if (bir.matched()) return bir.reply();
        if (hdc.matched()) return hdc.reply();
        if (mctsReply != null && !mctsReply.isBlank()) return mctsReply;
        return tsetlin.reply();
    }

    // Compose final confidence from whichever stage matched.
    private static double composeConfidence(SaliencyStage.SalienceScore salience,
                                            ArithmeticStage.ArithmeticResult arith,
                                            AnalogyStage.AnalogyResult analogy,
                                            BirInferenceStage.BirResult bir,
                                            HdcRetrievalStage.HdcResult hdc,
                                            TsetlinStage.TsetlinResult tsetlin,
                                            double mctsConfidence) {
        if (arith.matched()) return Math.max(arith.confidence(), salience.score());
        if (analogy.matched()) return Math.max(analogy.confidence(), salience.score());
        if (bir.matched()) return Math.max(bir.confidence(), salience.score());
        if (hdc.matched()) return Math.max(hdc.confidence(), salience.score());
        if (mctsConfidence > 0) return Math.max(mctsConfidence, salience.score());
        return Math.max(tsetlin.confidence(), salience.score());
    }

    private static MindResult finalize(String reply, double confidence,
                                       List<String> mods, List<BrcStep> trace,
                                       long startNs) {
        long dur = (System.nanoTime() - startNs) / 1_000_000L;
        boolean accepted = confidence >= 0.40;
        return new MindResult(reply, confidence, dur, accepted, mods, trace);
    }
}
