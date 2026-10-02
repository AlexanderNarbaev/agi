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
     * Uses in-memory HDC store (CI mode, tests).
     */
    public MindCycle() {
        this(new Random(42L), null);
    }

    /**
     * Persistent constructor — wires HDC stage to a {@link PersistentHdcStore}
     * so taught facts survive JVM restarts. Pass {@code null} for in-memory mode.
     */
    public MindCycle(PersistentHdcStore hdcStore) {
        this(new Random(42L), hdcStore);
    }

    /**
     * Explicit RNG constructor — for tests only.
     */
    public MindCycle(Random rng, PersistentHdcStore hdcStore) {
        this.rng = rng;
        this.reflex = new ReflexStage();
        this.signal = new SignalStage();
        this.saliency = new SaliencyStage();
        // RECON-W23: pass a real PlanningStage so compound arithmetic carries MCTS
        // deliberation evidence. The stage no longer REQUIRES it (the answer is
        // arithmetic, not planning), but the evidence is worth having.
        this.arithmetic = new ArithmeticStage(new io.matrix.brain.runtime.stages.PlanningStage());
        this.analogy = new AnalogyStage();
        this.birRules = new BirInferenceStage();
        this.hdcMemory = (hdcStore != null) ? new HdcRetrievalStage(hdcStore) : new HdcRetrievalStage();
        this.tsetlin = new TsetlinStage();
        this.modulators = new ModulatorStage();
    }

    /**
     * Run one full cognitive cycle. Returns a {@link MindResult} with reply,
     * confidence, modulators fired, and ordered BRC trace.
     */
    public MindResult think(String input) {
        return think(input, input);
    }

    /**
     * RECON-W22 — run the cycle with BOTH the retrieval form and the original form.
     *
     * <p><b>Why two forms.</b> Since W2 the gateway transliterates Cyrillic to
     * Latin before calling the mind, so the pipeline receives
     * {@code "stolitsa frantsii"} instead of {@code "столица франции"}. The
     * transliteration is useful for HDC retrieval against Latin-script facts, but
     * it DESTROYS the language identity that any bilingual reasoning needs — a
     * language-aware stage can no longer tell Russian from an English sentence
     * that happens to be transliterated. GE-4/GE-5 failed for exactly this
     * reason while the English equivalents passed.</p>
     *
     * <p>Rather than removing the transliteration (which W2 added deliberately),
     * both forms are now carried: {@code input} is the retrieval form used by the
     * lookup stages, {@code originalInput} is the untouched text used by the
     * language-aware stages. Nothing is lost and nothing is hidden.</p>
     *
     * <p>Article III: with both arguments equal this is byte-for-byte the previous
     * behaviour, so existing determinism guarantees are preserved.</p>
     */
    public MindResult think(String input, String originalInput) {
        long startMs = System.nanoTime();
        List<BrcStep> trace = new ArrayList<>();
        final String original = (originalInput == null) ? input : originalInput;

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
        // RECON-W31.4: score BOTH forms. The store now holds genuinely Cyrillic facts
        // (952 of them), and the transliterated query shares no tokens with them, so
        // retrieval on `input` alone left the whole Russian corpus unreachable.
        HdcRetrievalStage.HdcResult hdc = hdcMemory.retrieve(input, original, obs, trace);

        // Stage 8: TSETLIN — small-footprint classifier over learned clauses
        TsetlinStage.TsetlinResult tsetlinResult = tsetlin.classify(input, trace);

        // Stage 9 (MCTS): only invoked if confidence is low OR multi-step pattern detected.
        // MIND-W1 honest implementation: we do NOT run a real tree search here (that is
        // a W7 hardening). For W1 MCTS is a "deliberation budget" annotation that
        // records whether further exploration would be triggered, without fabricating
        // plan evidence that did not actually occur.
        BrcStep mctsStep;
        String mctsReply = null;
        double mctsConfidence = 0.0;
        boolean mctsWouldFire = tsetlinResult.confidence() < 0.55 || isMultiStep(input);
        if (mctsWouldFire) {
            mctsStep = new BrcStep(
                "MCTS",
                false,
                tsetlinResult.confidence(),
                List.of("budget=12", "would_fire=true", "reason=low-confidence-or-multi-step",
                    "note=mcts-deliberation-budget-not-implemented-in-W1")
            );
        } else {
            mctsStep = new BrcStep(
                "MCTS",
                false,
                1.0,
                List.of("budget=12", "would_fire=false", "reason=high-confidence-no-deliberation-needed")
            );
        }
        trace.add(mctsStep);

        // RECON-W22: relational + bilingual-fact stages, evaluated on the RAW input
        // before any lookup stage. MindCycle is the pipeline the gateway actually
        // serves (TrueMindCycle is the offline/audit variant), so wiring only the
        // latter left the live behaviour unchanged.
        io.matrix.brain.runtime.stages.RelationalReasoningStage relationalStage =
            new io.matrix.brain.runtime.stages.RelationalReasoningStage();
        io.matrix.brain.runtime.stages.RelationalReasoningStage.RelationalResult
            relational = relationalStage.tryEvaluate(original);
        trace.add(BrcStep.of("RELATIONAL", relational.matched(), relational.confidence(),
            List.of("rule=" + relational.rule(),
                    "answer=" + relational.reply(),
                    relational.matched() ? "fired=true"
                        : "declined=" + relational.declined())));

        io.matrix.brain.runtime.stages.BilingualFactLookup factStage =
            new io.matrix.brain.runtime.stages.BilingualFactLookup();
        io.matrix.brain.runtime.stages.BilingualFactLookup.FactResult facts =
            factStage.lookup(original);
        trace.add(BrcStep.of("BILINGUAL_FACTS", facts.matched(), facts.confidence(),
            List.of("rule=" + facts.rule(),
                    "answer=" + facts.reply(),
                    facts.matched() ? "fired=true"
                        : "declined=" + facts.declined())));

        // Compose final reply + confidence
        String reply = composeReply(input, arith, analogyResult, relational, facts,
            bir, hdc, tsetlinResult, mctsReply);
        double confidence = composeConfidence(salience, arith, analogyResult, bir, hdc,
            tsetlinResult, mctsConfidence);
        if (reply != null && !reply.isBlank() && confidence < 0.40
                && (relational.matched() || facts.matched())) {
            // A stage that genuinely fired must not be demoted below the accept
            // threshold by an unrelated salience score.
            confidence = Math.max(confidence,
                relational.matched() ? relational.confidence() : facts.confidence());
        }

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

    /**
     * RECON-W22 — the serving pipeline's reply composition.
     *
     * <p>Stage order is load-bearing. Relational and bilingual-fact reasoning run
     * BEFORE the BIR/HDC lookups: a rule that genuinely fires on the input must
     * outrank a coincidental cosine match (GE-7 previously matched a stored
     * "9.8 m/s^2" physics datum and answered that instead of "small").</p>
     *
     * <p><b>Article VIII fix.</b> The terminal branch previously returned
     * {@code tsetlin.reply()}, which is {@code ""} whenever
     * {@code simulacrumEnabled == false} — the correct Article I production
     * setting. Every un-answered question therefore returned an empty string at
     * full salience confidence: a silent zero. It now returns an explicit refusal
     * and the trace names the stage that declined.</p>
     */
    private static String composeReply(String input,
                                       ArithmeticStage.ArithmeticResult arith,
                                       AnalogyStage.AnalogyResult analogy,
                                       io.matrix.brain.runtime.stages.RelationalReasoningStage.RelationalResult relational,
                                       io.matrix.brain.runtime.stages.BilingualFactLookup.FactResult facts,
                                       BirInferenceStage.BirResult bir,
                                       HdcRetrievalStage.HdcResult hdc,
                                       TsetlinStage.TsetlinResult tsetlin,
                                       String mctsReply) {
        if (arith.matched()) return arith.reply();
        if (analogy.matched()) return analogy.reply();
        if (relational.matched()) return relational.reply();
        if (facts.matched()) return facts.reply();
        if (bir.matched()) return bir.reply();
        if (hdc.matched()) return hdc.reply();
        if (mctsReply != null && !mctsReply.isBlank()) return mctsReply;
        if (tsetlin.matched() && tsetlin.reply() != null && !tsetlin.reply().isBlank()) {
            return tsetlin.reply();
        }
        return "I don't have a confident answer to that. "
             + "No reasoning stage could establish one from what I know.";
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
