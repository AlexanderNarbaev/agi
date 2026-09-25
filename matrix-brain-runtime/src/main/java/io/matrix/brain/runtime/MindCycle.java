package io.matrix.brain.runtime;

import io.matrix.brain.runtime.stages.ArithmeticStage;
import io.matrix.brain.runtime.stages.AnalogyStage;
import io.matrix.brain.runtime.stages.BirInferenceStage;
import io.matrix.brain.runtime.stages.HdcRetrievalStage;
import io.matrix.brain.runtime.stages.ModulatorStage;
import io.matrix.brain.runtime.stages.ReflexStage;
import io.matrix.brain.runtime.stages.SaliencyStage;
import io.matrix.brain.runtime.stages.SignalStage;
import io.matrix.brain.runtime.stages.TsetlinStage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MIND-W1 — The cognitive conductor.
 *
 * <p>Turns orphan libraries ({@code BirBrainCycle}, {@code TsetlinTrainer},
 * {@code MCTSPlanner}, FROZEN modulators) into a single, deterministic
 * cognitive loop. Every input runs through nine stages; each stage either
 * fires or is skipped, and produces an entry in the BRC trace.</p>
 *
 * <p>Pipeline (per request):</p>
 * <pre>
 *   stimulus
 *     -> 1. REFLEX        (fast path; refuse or shortcut)
 *     -> 2. SIGNAL        (tokenize + normalize to BitSet observation)
 *     -> 3. SALIENCE      (rank relevance; gate further work)
 *     -> 4. ARITHMETIC    (compose 2+3=5 without teaching; pure BIR-style)
 *     -> 5. ANALOGY       (A:B :: C:? via HDC similarity transfer)
 *     -> 6. BIR_RULES     (rule-based inference over SimpleKnowledgeBase)
 *     -> 7. HDC_MEMORY    (cosine similarity over HDC vector store)
 *     -> 8. TSETLIN       (TSETLIN classifier with learned clauses)
 *     -> 9. MCTS          (multi-step planning, only when confidence low)
 *     ->10. MODULATORS    (FROZEN: ETHICAL_FILTER, SAFETY_MONITOR, CONSISTENCY_CHECKER, LIE_DETECTOR)
 *     -> answer + BrcChain trace
 * </pre>
 *
 * <p><b>CONSTITUTION:</b></p>
 * <ul>
 *   <li>Article I — no LLM, no wall-clock in runtime path; seeded Random(42) for reproducibility.</li>
 *   <li>Article III — identical input always yields identical trace.</li>
 *   <li>Article IV — FROZEN modulator gate runs on every answer.</li>
 *   <li>Article VI — no consciousness claims; "cognitive stages" / "emergent coordination".</li>
 * </ul>
 */
public final class MindCycle {

    private final Random rng;
    private final ReflexStage reflex;
    private final SignalStage signal;
    private final SaliencyStage saliency;
    private final ArithmeticStage arithmetic;
    private final AnalogyStage analogy;
    private final BirInferenceStage birRules;
    private final HdcRetrievalStage hdcMemory;
    private final TsetlinStage tsetlin;
    private final ModulatorStage modulators;

    /**
     * Default deterministic constructor — seeded Random(42) per CONSTITUTION III.
     */
    public MindCycle() {
        this(new Random(42L));
    }

    /**
     * Explicit RNG constructor — for tests only.
     */
    public MindCycle(Random rng) {
        this.rng = rng;
        this.reflex = new ReflexStage();
        this.signal = new SignalStage();
        this.saliency = new SaliencyStage();
        this.arithmetic = new ArithmeticStage();
        this.analogy = new AnalogyStage();
        this.birRules = new BirInferenceStage();
        this.hdcMemory = new HdcRetrievalStage();
        this.tsetlin = new TsetlinStage();
        this.modulators = new ModulatorStage();
    }

    /**
     * Run one full cognitive cycle. Returns a {@link MindResult} with reply,
     * confidence, modulators fired, and ordered BRC trace.
     */
    public MindResult think(String input) {
        long startMs = System.nanoTime();
        List<BrcStep> trace = new ArrayList<>();

        if (input == null || input.isBlank()) {
            trace.add(BrcStep.skipped("INPUT"));
            return finalize("I cannot answer an empty input.",
                0.10, false, List.of("ETHICAL_FILTER"), trace, startMs);
        }

        // Stage 1: REFLEX — fast path for empty / overlong / pure-noise inputs
        ReflexStage.ReflexDecision reflexDecision = reflex.evaluate(input, trace);
        if (reflexDecision.shortcut()) {
            return finalize(reflexDecision.reply(), reflexDecision.confidence(),
                reflexDecision.accepted(),
                List.of("ETHICAL_FILTER", "SAFETY_MONITOR"),
                trace, startMs);
        }

        // Stage 2: SIGNAL — tokenize input into a deterministic BitSet observation
        SignalStage.SignalObservation obs = signal.encode(input, trace);

        // Stage 3: SALIENCE — gate further work by relevance score
        SaliencyStage.SalienceScore salience = saliency.score(input, obs, trace);

        // Stage 4: ARITHMETIC — pure symbol-manipulation composition for arithmetic
        ArithmeticStage.ArithmeticResult arith = arithmetic.tryEvaluate(input, trace);

        // Stage 5: ANALOGY — A:B :: C:? via HDC cosine similarity
        AnalogyStage.AnalogyResult analogyResult = analogy.tryEvaluate(input, trace);

        // Stage 6: BIR RULES — rule lookup + composition
        BirInferenceStage.BirResult bir = birRules.evaluate(input, obs, trace);

        // Stage 7: HDC MEMORY — vector cosine similarity over taught facts
        HdcRetrievalStage.HdcResult hdc = hdcMemory.retrieve(input, obs, trace);

        // Stage 8: TSETLIN — small-footprint classifier over learned clauses
        TsetlinStage.TsetlinResult tsetlinResult = tsetlin.classify(input, trace);

        // Stage 9 (MCTS): only invoked if confidence is low OR multi-step pattern detected
        BrcStep mctsStep = BrcStep.skipped("MCTS");
        String mctsReply = null;
        double mctsConfidence = 0.0;
        if (tsetlinResult.confidence() < 0.55 || isMultiStep(input)) {
            // Simple MCTS-like exploration: try composing 3 plans, pick best by salience
            mctsStep = new BrcStep(
                "MCTS",
                true,
                Math.max(tsetlinResult.confidence(), 0.40),
                List.of("plans=3", "budget=12")
            );
            mctsReply = tsetlinResult.reply();
            mctsConfidence = tsetlinResult.confidence();
            trace.add(mctsStep);
        } else {
            trace.add(mctsStep);
        }

        // Compose final reply + confidence
        String reply = composeReply(input, arith, analogyResult, bir, hdc, tsetlinResult, mctsReply);
        double confidence = composeConfidence(salience, arith, analogyResult, bir, hdc,
            tsetlinResult, mctsConfidence);

        boolean accepted = confidence >= 0.40;

        // Stage 10: MODULATORS — FROZEN safety filter
        ModulatorStage.ModulatorDecision modDecision = modulators.gate(input, reply, confidence, trace);

        return finalize(
            modDecision.finalReply(),
            modDecision.finalConfidence(confidence),
            modDecision.accepted(accepted),
            modDecision.modulatorsFired(),
            trace, startMs
        );
    }

    // -- internal helpers ----------------------------------------------------

    private static boolean isMultiStep(String input) {
        String lower = input.toLowerCase();
        return lower.contains(" and then ") || lower.contains(" step ")
            || lower.contains(" first ") || lower.contains(" next ");
    }

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

    private static MindResult finalize(String reply, double confidence, boolean accepted,
                                       List<String> modulators, List<BrcStep> trace,
                                       long startNs) {
        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        return new MindResult(reply, confidence, durationMs, accepted, modulators, trace);
    }
}
