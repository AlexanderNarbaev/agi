package io.matrix.brain;

import io.matrix.api.GenerationResult;
import io.matrix.api.QwenOnnxBridge;
import io.matrix.auditor.MatrixTrace;
import io.matrix.audit.HashChain;
import io.matrix.consciousness.ActionGate;
import io.matrix.consciousness.ArousalDynamics;
import io.matrix.consciousness.AttentionRouter;
import io.matrix.consciousness.PredictionModel;
import io.matrix.ethics.EthicalFilter;
import io.matrix.knowledge.SimpleKnowledgeBase;
import io.matrix.perception.SaliencyEngine;
import io.matrix.perception.TextEncoder;
import io.matrix.safety.ConsistencyChecker;
import io.matrix.safety.ConfidenceCalibrator;
import io.matrix.safety.LieDetector;
import io.matrix.safety.SafetyMonitor;

import java.nio.file.Paths;
import java.util.List;

/**
 * W478 — RAG-enhanced LLM Brain Loop.
 * 
 * Wraps LlmBrainLoopService with Retrieval-Augmented Generation.
 * Before each cycle, retrieves relevant context from the knowledge
 * base and prepends it to the LLM prompt.
 * 
 * This is critical for anti-hallucination: grounds the LLM in
 * known facts rather than parametric memory.
 */
public final class LlmBrainLoopRag implements BrainCycle {
    
    private final BrainCycle brain;
    private final SimpleKnowledgeBase knowledgeBase;
    private final MatrixTrace trace = new MatrixTrace();
    
    public LlmBrainLoopRag(String modelPath, SimpleKnowledgeBase kb) {
        this.brain = new LlmBrainLoopService(modelPath);
        this.knowledgeBase = kb;
    }
    
    /**
     * Run one cognitive cycle with RAG augmentation.
     */
    public BrainCycle.CycleResult cycle(String input) {
        // RAG: retrieve context
        String context = knowledgeBase.buildContext(input, 3);
        
        // Build augmented prompt
        String augmentedInput;
        if (context.isEmpty()) {
            augmentedInput = input;
        } else {
            augmentedInput = context + "\nUser question: " + input;
        }
        
        // Track RAG usage
        try (var span = trace.begin("brain.rag")) {
            span.output("context=" + context.length() + " chars, input=" + input.length() + " chars");
            BrainCycle.CycleResult result = brain.cycle(augmentedInput);
            span.output("accepted=" + result.accepted() + ", reply=" + result.reply().length() + " chars");
            return result;
        }
    }
    
    /**
     * Run cycle with custom KB.
     */
    public BrainCycle.CycleResult cycle(String input, SimpleKnowledgeBase kb) {
        String context = kb.buildContext(input, 3);
        String augmentedInput = context.isEmpty() ? input : context + "\nUser question: " + input;
        return brain.cycle(augmentedInput);
    }
    
    public void close() {
        brain.close();
    }
    
    public BrainCycle getBrain() { return brain; }
    public SimpleKnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: LlmBrainLoopRag <input> [model-path]");
            System.exit(1);
        }
        String input = args[0];
        String modelPath = args.length > 1 ? args[1] : "models/onnx/qwen05b";
        
        // Load knowledge base from default location
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        System.out.println("Knowledge base size: " + kb.size());
        
        LlmBrainLoopRag rag = new LlmBrainLoopRag(modelPath, kb);
        BrainCycle.CycleResult r = rag.cycle(input);
        
        System.out.println("===== RAG Brain Cycle Result =====");
        System.out.println("Input:       " + input);
        System.out.println("Accepted:    " + r.accepted());
        System.out.println("Action:      " + r.action());
        System.out.println("Reply:       " + r.reply());
        System.out.println("Confidence:  " + r.confidence());
        System.out.println("Duration:    " + r.durationMs() + "ms");
        
        rag.close();
    }
}
