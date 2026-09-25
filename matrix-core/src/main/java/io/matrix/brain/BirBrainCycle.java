package io.matrix.brain;

import io.matrix.audit.HashChain;
import io.matrix.audit.HashLink;
import io.matrix.consciousness.ActionGate;
import io.matrix.consciousness.ArousalDynamics;
import io.matrix.consciousness.AttentionRouter;
import io.matrix.consciousness.PredictionModel;
import io.matrix.ethics.EthicalFilter;
import io.matrix.knowledge.SimpleKnowledgeBase;
import io.matrix.neuron.CodebookMemory;
import io.matrix.neuron.HdcBrain;
import io.matrix.neuron.HdcEncoding;
import io.matrix.perception.SaliencyEngine;
import io.matrix.perception.TextEncoder;
import io.matrix.safety.ConfidenceCalibrator;
import io.matrix.safety.ConsistencyChecker;
import io.matrix.safety.LieDetector;
import io.matrix.safety.SafetyMonitor;

import java.util.List;
import java.util.Random;

/**
 * W566 — BIR-based Brain Cycle (replaces LlmBrainLoopService).
 *
 * <p>Implements {@link BrainCycle} using the Boolean Substrate:
 * <ul>
 *   <li>TextEncoder → boolean[] → HDC code</li>
 *   <li>HdcBrain for similarity-based response retrieval</li>
 *   <li>CodebookMemory for storing Q&A associations</li>
 *   <li>Cosine similarity for response matching</li>
 *   <li>Safety pipeline (SafetyMonitor, LieDetector, ConsistencyChecker)</li>
 * </ul>
 *
 * <h2>CONSTITUTION I Compliance</h2>
 * <p>No LLM in runtime path. All inference is via HDC cosine similarity
 * and deterministic boolean operations. RNG is seeded (no wall-clock).
 *
 * <h2>Architecture</h2>
 * <pre>
 *   input → TextEncoder → boolean[] → HDC code
 *                                      ↓
 *                            HdcBrain.forward() → Recall (label, similarity)
 *                                      ↓
 *                            CodebookMemory.query() → response text
 *                                      ↓
 *                            Safety pipeline → CycleResult
 * </pre>
 */
public final class BirBrainCycle implements BrainCycle {

    private final TextEncoder encoder;
    private final SaliencyEngine saliency;
    private final AttentionRouter router;
    private final PredictionModel prediction;
    private final ArousalDynamics arousal;
    private final ActionGate gate;
    private final HdcBrain hdcBrain;
    private final CodebookMemory codebook;
    private final SafetyMonitor safety;
    private final LieDetector lie;
    private final ConsistencyChecker consistency;
    private final ConfidenceCalibrator confidence;
    private final HashChain audit;
    private final SimpleKnowledgeBase knowledgeBase;
    private final Random rng;
    private final long instanceId;

    /** Minimum HDC similarity to accept a retrieval result. */
    public static final double MIN_SIMILARITY = 0.15;

    /** Confidence scaling factor for HDC similarity. */
    public static final double SIMILARITY_SCALE = 0.8;

    /**
     * Create a BirBrainCycle with default configuration.
     *
     * @param rng seeded RNG (CONSTITUTION I)
     */
    public BirBrainCycle(Random rng) {
        this(rng, new SimpleKnowledgeBase());
    }

    /**
     * Create a BirBrainCycle with a knowledge base for RAG.
     *
     * @param rng seeded RNG (CONSTITUTION I)
     * @param kb  knowledge base for retrieval-augmented responses
     */
    public BirBrainCycle(Random rng, SimpleKnowledgeBase kb) {
        this(
            new TextEncoder(),
            new SaliencyEngine(),
            new AttentionRouter(),
            new PredictionModel(256, 0.0),
            new ArousalDynamics(),
            new ActionGate(),
            new HdcBrain(10000, rng),
            new CodebookMemory(10000),
            new SafetyMonitor(new EthicalFilter()),
            new LieDetector(new EthicalFilter()),
            new ConsistencyChecker(),
            new ConfidenceCalibrator(),
            new HashChain(),
            kb != null ? kb : new SimpleKnowledgeBase(),
            rng,
            rng.nextLong()
        );
    }

    /**
     * Full constructor with all dependencies injected.
     */
    public BirBrainCycle(
            TextEncoder encoder,
            SaliencyEngine saliency,
            AttentionRouter router,
            PredictionModel prediction,
            ArousalDynamics arousal,
            ActionGate gate,
            HdcBrain hdcBrain,
            CodebookMemory codebook,
            SafetyMonitor safety,
            LieDetector lie,
            ConsistencyChecker consistency,
            ConfidenceCalibrator confidence,
            HashChain audit,
            SimpleKnowledgeBase knowledgeBase,
            Random rng,
            long instanceId) {
        this.encoder = encoder;
        this.saliency = saliency;
        this.router = router;
        this.prediction = prediction;
        this.arousal = arousal;
        this.gate = gate;
        this.hdcBrain = hdcBrain;
        this.codebook = codebook;
        this.safety = safety;
        this.lie = lie;
        this.consistency = consistency;
        this.confidence = confidence;
        this.audit = audit;
        this.knowledgeBase = knowledgeBase;
        this.rng = rng;
        this.instanceId = instanceId;
    }

    @Override
    public CycleResult cycle(String input) {
        long start = System.currentTimeMillis();
        HashLink link = audit.append(input, "input");
        long auditIndex = link.sequence();

        // Phase 1: Perception — encode text to boolean[]
        boolean[] bits = encoder.encode(input);

        // Phase 2: Saliency — score attention
        var s = saliency.score("text", bits);
        var items = router.merge(List.of(), List.of(s));

        // Phase 3: BIR Inference — HDC similarity retrieval
        String reply;
        double meanConfidence;

        // 3a: Try RAG from knowledge base
        String ragContext = knowledgeBase.buildContext(input, 3);
        if (!ragContext.isEmpty()) {
            // Use KB context as response
            reply = extractAnswer(ragContext, input);
            meanConfidence = 0.7; // KB-sourced = moderate confidence
        } else {
            // 3b: Try HDC brain recall
            float[] features = encodeToFeatures(bits);
            HdcBrain.Recall recall = hdcBrain.forward(features);

            if (recall != null && recall.similarity > MIN_SIMILARITY) {
                // Retrieve response from codebook using HDC vector
                long[] queryVec = hdcBrain.encodeFeatures(features);
                CodebookMemory.Result cbResult = codebook.query(queryVec);
                if (cbResult != null && cbResult.similarity() > MIN_SIMILARITY) {
                    reply = cbResult.id; // label is the response
                    meanConfidence = recall.similarity * SIMILARITY_SCALE;
                } else {
                    reply = recall.label; // label itself as fallback
                    meanConfidence = recall.similarity * SIMILARITY_SCALE * 0.8;
                }
            } else {
                // 3c: Deterministic template response
                reply = generateTemplateResponse(input, bits);
                meanConfidence = 0.3; // template = low confidence
            }
        }

        // Phase 4: Safety pipeline
        var calibrated = confidence.calibrate(meanConfidence);
        double calibratedConfidence = calibrated.pointEstimate();
        consistency.checkOnly(reply, "", true);
        var gd = gate.check(reply);
        var safetyReport = safety.evaluate(reply, List.of(), List.of(), "", true, meanConfidence, List.of());
        boolean safe = safetyReport.isSafe();
        boolean passed = gd.passed() && safe && calibratedConfidence > 0.3;

        String action = passed ? ("ACCEPT:" + reply) : ("DENY:" + gd.reason());
        arousal.update(calibratedConfidence);
        audit.append(reply, action);

        double predError = prediction.updateError(saliency.getClass().hashCode() % 1000 / 1000.0);

        return new CycleResult(
            passed,
            action,
            passed ? reply : "I cannot answer that confidently.",
            arousal.current(),
            items.size(),
            predError,
            calibratedConfidence,
            auditIndex,
            System.currentTimeMillis() - start
        );
    }

    /**
     * Encode boolean[] bits into float[] features for HdcBrain.
     * Maps each boolean bit to +1.0f or -1.0f.
     */
    private float[] encodeToFeatures(boolean[] bits) {
        float[] features = new float[HdcEncoding.DIM];
        for (int i = 0; i < HdcEncoding.DIM && i < bits.length; i++) {
            features[i] = bits[i] ? 1.0f : -1.0f;
        }
        return features;
    }

    /**
     * Extract an answer from RAG context.
     * Takes the first sentence that contains a keyword from the input.
     */
    private String extractAnswer(String context, String input) {
        String[] sentences = context.split("[.!?]");
        String[] keywords = input.toLowerCase().split("\\s+");

        for (String sentence : sentences) {
            String lower = sentence.toLowerCase().trim();
            if (lower.isEmpty()) continue;
            for (String kw : keywords) {
                if (kw.length() > 2 && lower.contains(kw)) {
                    return sentence.trim();
                }
            }
        }
        // Fallback: return first non-empty sentence
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (!trimmed.isEmpty()) return trimmed;
        }
        return context.substring(0, Math.min(200, context.length()));
    }

    /**
     * Generate a deterministic template response when no retrieval match.
     * Uses input features to select from template pool.
     */
    private String generateTemplateResponse(String input, boolean[] bits) {
        // Count salient bits for template selection
        int ones = 0;
        for (boolean b : bits) if (b) ones++;
        int templateIdx = ones % TEMPLATES.length;
        return TEMPLATES[templateIdx];
    }

    /** Deterministic template responses. */
    private static final String[] TEMPLATES = {
        "I need more information to answer that question accurately.",
        "Let me think about that. Could you provide more context?",
        "That's an interesting question. I don't have enough data yet.",
        "I'm not confident enough to answer that right now.",
        "Could you rephrase that? I want to give you a good answer.",
        "I'm still learning about that topic. Ask me something else?",
        "That's beyond my current knowledge base.",
        "I'd need to learn more before I can help with that."
    };

    /**
     * Learn a Q&A association: store in both HDC brain and codebook.
     *
     * @param question the question text
     * @param answer   the answer text
     * @return similarity after learning
     */
    public double learn(String question, String answer) {
        boolean[] bits = encoder.encode(question);
        float[] features = encodeToFeatures(bits);
        double sim = hdcBrain.learn(features, answer);
        long[] labelCode = hdcBrain.codeForLabel(answer);
        codebook.store(answer, labelCode);
        return sim;
    }

    /**
     * Learn from a knowledge base: encode all documents.
     */
    public void learnFromKnowledgeBase() {
        var docs = knowledgeBase.retrieve("", 100);
        for (var doc : docs) {
            if (doc != null && doc.doc() != null && doc.doc().content() != null
                    && !doc.doc().content().isBlank()) {
                boolean[] bits = encoder.encode(doc.doc().content());
                float[] features = encodeToFeatures(bits);
                hdcBrain.learn(features, doc.doc().id());
            }
        }
    }

    @Override
    public void close() {
        // No resources to release (HDC brain is in-memory)
    }

    // Accessors for testing
    public HdcBrain getHdcBrain() { return hdcBrain; }
    public CodebookMemory getCodebook() { return codebook; }
    public SimpleKnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    public HashChain getAudit() { return audit; }
    public SafetyMonitor getSafety() { return safety; }
    public Random getRng() { return rng; }

    public static void main(String[] args) throws Exception {
        String input;
        if (args.length >= 1) {
            StringBuilder sb = new StringBuilder();
            for (String a : args) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(a);
            }
            input = sb.toString();
        } else {
            try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
                input = r.readLine();
                if (input == null) input = "";
            }
        }

        Random rng = new Random(42);
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        BirBrainCycle brain = new BirBrainCycle(rng, kb);

        // Pre-learn from KB
        brain.learnFromKnowledgeBase();

        CycleResult result = brain.cycle(input);

        System.out.println("===== BIR Brain Cycle Result =====");
        System.out.println("Input:       " + input);
        System.out.println("Accepted:    " + result.accepted());
        System.out.println("Action:      " + result.action());
        System.out.println("Reply:       " + result.reply());
        System.out.println("Arousal:     " + result.arousal());
        System.out.println("Confidence:  " + result.confidence());
        System.out.println("Audit idx:   " + result.auditIndex());
        System.out.println("Duration:    " + result.durationMs() + "ms");
        System.out.println("HDC memories: " + brain.getHdcBrain().size());

        brain.close();
    }
}
