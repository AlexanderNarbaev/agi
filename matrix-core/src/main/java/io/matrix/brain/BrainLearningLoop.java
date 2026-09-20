package io.matrix.brain;

import io.matrix.knowledge.SimpleKnowledgeBase;
import io.matrix.learning.ConversationLearner;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * W495 — Brain Learning Loop.
 * 
 * Closes the loop between conversations and learning:
 * 1. User talks to brain (via LlmBrainLoopRag)
 * 2. Conversation is recorded (via NDJSON)
 * 3. Brain learns from recorded conversations (via ConversationLearner)
 * 4. Learned facts improve future responses (via RAG)
 * 
 * This is the SELF-IMPROVEMENT LOOP:
 * - More conversations → more knowledge → better answers
 * - Brain gets smarter over time
 * 
 * Usage:
 *   BrainLearningLoop loop = new BrainLearningLoop("models/onnx/qwen05b");
 *   String reply = loop.talk("What is gravity?");
 *   int learned = loop.learn();  // learns from all recorded conversations
 *   String better_reply = loop.talk("What is gravity?");  // should be better
 */
public final class BrainLearningLoop {
    
    private final LlmBrainLoopRag brain;
    private final ConversationLearner learner;
    private final SimpleKnowledgeBase knowledgeBase;
    private final Path conversationsDir;
    private int totalTalks = 0;
    private int totalLearned = 0;
    
    public BrainLearningLoop(String modelPath) {
        this.conversationsDir = Paths.get("data/conversations");
        this.knowledgeBase = new SimpleKnowledgeBase();
        this.brain = new LlmBrainLoopRag(modelPath, knowledgeBase);
        this.learner = new ConversationLearner(knowledgeBase, conversationsDir);
    }
    
    /**
     * Talk to the brain. Records the conversation for later learning.
     */
    public BrainCycle.CycleResult talk(String message) {
        BrainCycle.CycleResult result = brain.cycle(message);
        totalTalks++;
        return result;
    }
    
    /**
     * Learn from all recorded conversations. Returns number of facts learned.
     */
    public int learn() {
        try {
            int learned = learner.learnAll();
            totalLearned += learned;
            return learned;
        } catch (IOException e) {
            System.err.println("[brain-learn] Error: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Get the knowledge base (for inspection).
     */
    public SimpleKnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    
    /**
     * Get total talks performed.
     */
    public int getTotalTalks() { return totalTalks; }
    
    /**
     * Get total facts learned.
     */
    public int getTotalLearned() { return totalLearned; }
    
    public void close() {
        brain.close();
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: BrainLearningLoop <model-path>");
            System.exit(1);
        }
        String modelPath = args[0];
        
        System.out.println("=== Brain Learning Loop ===");
        System.out.println("Model: " + modelPath);
        System.out.println();
        
        BrainLearningLoop loop = new BrainLearningLoop(modelPath);
        
        // Step 1: Learn from existing conversations
        System.out.println("Step 1: Learning from conversations...");
        int learned = loop.learn();
        System.out.println("Learned " + learned + " facts from conversations");
        System.out.println("KB size: " + loop.getKnowledgeBase().size());
        System.out.println();
        
        // Step 2: Talk and see if answers improve
        System.out.println("Step 2: Testing answers...");
        String[] questions = {
            "What is the capital of France?",
            "Who created MATRIX?",
            "What is gravity?",
        };
        
        for (String q : questions) {
            BrainCycle.CycleResult r = loop.talk(q);
            System.out.println("Q: " + q);
            System.out.println("A: " + (r.reply() != null ? r.reply().substring(0, Math.min(100, r.reply().length())) + "..." : "(empty)"));
            System.out.println("Confidence: " + String.format("%.2f", r.confidence()));
            System.out.println("Accepted: " + r.accepted());
            System.out.println();
        }
        
        System.out.println("=== Summary ===");
        System.out.println("Talks: " + loop.getTotalTalks());
        System.out.println("Learned: " + loop.getTotalLearned());
        System.out.println("KB: " + loop.getKnowledgeBase().size());
        
        loop.close();
    }
}
