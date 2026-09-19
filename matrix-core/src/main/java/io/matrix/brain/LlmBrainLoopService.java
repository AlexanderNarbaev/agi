package io.matrix.brain;

import io.matrix.api.GenerationResult;
import io.matrix.api.QwenOnnxBridge;
import io.matrix.auditor.MatrixTrace;
import io.matrix.audit.HashChain;
import io.matrix.audit.HashLink;
import io.matrix.consciousness.ActionGate;
import io.matrix.consciousness.ArousalDynamics;
import io.matrix.consciousness.AttentionRouter;
import io.matrix.consciousness.PredictionModel;
import io.matrix.ethics.EthicalFilter;
import io.matrix.perception.SaliencyEngine;
import io.matrix.perception.TextEncoder;
import io.matrix.safety.ConsistencyChecker;
import io.matrix.safety.ConfidenceCalibrator;
import io.matrix.safety.LieDetector;
import io.matrix.safety.SafetyMonitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.List;

/**
 * W473 — LLM-Backed Brain Loop.
 * 
 * Wires the actual Qwen2.5-0.5B model into the cognitive cycle.
 */
public final class LlmBrainLoopService {
    
    private final TextEncoder encoder;
    private final SaliencyEngine saliency;
    private final AttentionRouter router;
    private final PredictionModel prediction;
    private final ArousalDynamics arousal;
    private final ActionGate gate;
    private final QwenOnnxBridge llm;
    private final SafetyMonitor safety;
    private final LieDetector lie;
    private final ConsistencyChecker consistency;
    private final ConfidenceCalibrator confidence;
    private final HashChain audit;
    private final MatrixTrace trace = new MatrixTrace();
    private final long instanceId;
    
    public LlmBrainLoopService(String modelPath) {
        this(new TextEncoder(),
             new SaliencyEngine(),
             new AttentionRouter(),
             new PredictionModel(256, 0.0),
             new ArousalDynamics(),
             new ActionGate(),
             loadLlm(modelPath),
             new SafetyMonitor(new EthicalFilter()),
             new LieDetector(new EthicalFilter()),
             new ConsistencyChecker(),
             new ConfidenceCalibrator(),
             new HashChain(),
             System.currentTimeMillis());
    }
    
    public LlmBrainLoopService(TextEncoder encoder,
                                 SaliencyEngine saliency,
                                 AttentionRouter router,
                                 PredictionModel prediction,
                                 ArousalDynamics arousal,
                                 ActionGate gate,
                                 QwenOnnxBridge llm,
                                 SafetyMonitor safety,
                                 LieDetector lie,
                                 ConsistencyChecker consistency,
                                 ConfidenceCalibrator confidence,
                                 HashChain audit,
                                 long instanceId) {
        this.encoder = encoder;
        this.saliency = saliency;
        this.router = router;
        this.prediction = prediction;
        this.arousal = arousal;
        this.gate = gate;
        this.llm = llm;
        this.safety = safety;
        this.lie = lie;
        this.consistency = consistency;
        this.confidence = confidence;
        this.audit = audit;
        this.instanceId = instanceId;
    }
    
    private static QwenOnnxBridge loadLlm(String modelPath) {
        QwenOnnxBridge bridge = new QwenOnnxBridge(Paths.get(modelPath));
        bridge.useGpu(false);
        bridge.setMaxNewTokens(128);
        bridge.load();
        return bridge;
    }
    
    public record CycleResult(
        boolean accepted,
        String action,
        String reply,
        double arousal,
        int focusCount,
        double predictionError,
        double confidence,
        long auditIndex,
        long durationMs
    ) {}
    
    public CycleResult cycle(String input) {
        long start = System.currentTimeMillis();
        HashLink link = audit.append(input, "input");
        long auditIndex = link.sequence();
        boolean[] bits = encoder.encode(input);
        var s = saliency.score("text", bits);
        var items = router.merge(List.of(), List.of(s));
        String reply;
        double meanConfidence = 0.0;
        GenerationResult probResult = llm.generateWithProbs(input, 128);
        reply = probResult.text();
        meanConfidence = probResult.avgConfidence();
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
    
    public CycleResult chat(String sessionId, String userMessage) {
        return cycle(userMessage);
    }
    
    public void close() {
        if (llm != null) llm.close();
    }
    
    public QwenOnnxBridge getLlm() { return llm; }
    public HashChain getAudit() { return audit; }
    public SafetyMonitor getSafety() { return safety; }
    
    public static void main(String[] args) throws Exception {
        String input;
        String modelPath = "models/onnx/qwen05b";
        int modelPathIdx = 0;
        
        if (args.length >= 1) {
            // Last arg might be model path (contains "/onnx/" or "models/")
            for (int i = 0; i < args.length; i++) {
                if (args[i].contains("/") || args[i].contains("models")) {
                    modelPath = args[i];
                    modelPathIdx = i;
                    break;
                }
            }
            // Build input from remaining args
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i == modelPathIdx) continue;
                if (sb.length() > 0) sb.append(" ");
                sb.append(args[i]);
            }
            input = sb.toString();
        } else {
            // Read from stdin
            try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in))) {
                input = r.readLine();
                if (input == null) input = "";
            } catch (IOException e) {
                input = "";
            }
        }
        
        LlmBrainLoopService brain = new LlmBrainLoopService(modelPath);
        CycleResult result = brain.cycle(input);
        
        System.out.println("===== Brain Cycle Result =====");
        System.out.println("Input:       " + input);
        System.out.println("Accepted:    " + result.accepted());
        System.out.println("Action:      " + result.action());
        System.out.println("Reply:       " + result.reply());
        System.out.println("Arousal:     " + result.arousal());
        System.out.println("Confidence:  " + result.confidence());
        System.out.println("Audit idx:   " + result.auditIndex());
        System.out.println("Duration:    " + result.durationMs() + "ms");
        
        brain.close();
    }
}
