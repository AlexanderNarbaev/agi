package io.matrix.learning;

import io.matrix.brain.LlmBrainLoopRag;
import io.matrix.knowledge.SimpleKnowledgeBase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * W486 — Brain Improver (self-improvement loop).
 * 
 * Integrates learning with the brain:
 * 1. Learns from conversations (ConversationLearner)
 * 2. Adds learned facts to KnowledgeBase
 * 3. Brain uses learned facts via RAG
 * 4. Measures improvement
 * 
 * This closes the loop: conversations → learning → knowledge → better answers
 */
public final class BrainImprover {
    
    private final ConversationLearner learner;
    private final SimpleKnowledgeBase knowledgeBase;
    private int totalLearned = 0;
    private int cyclesCompleted = 0;
    
    public BrainImprover(Path conversationsDir) {
        this.knowledgeBase = new SimpleKnowledgeBase();
        this.learner = new ConversationLearner(knowledgeBase, conversationsDir);
    }
    
    /**
     * Run one improvement cycle:
     * 1. Learn from new conversations
     * 2. Return how many pairs learned
     */
    public int improveOnce() throws IOException {
        int learned = learner.learnAll();
        totalLearned += learned;
        cyclesCompleted++;
        return learned;
    }
    
    /**
     * Run continuous improvement (for integration with autonomy engine).
     * Learns, then returns learned count.
     */
    public int continuousImprove() throws IOException {
        int learned = improveOnce();
        return learned;
    }
    
    public int getTotalLearned() { return totalLearned; }
    public int getCyclesCompleted() { return cyclesCompleted; }
    public SimpleKnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    
    public static void main(String[] args) throws Exception {
        BrainImprover improver = new BrainImprover(Paths.get("data/conversations"));
        
        System.out.println("=== Brain Improver ===");
        System.out.println("KB size before: " + improver.knowledgeBase.size());
        
        int learned = improver.improveOnce();
        
        System.out.println("Learned: " + learned + " pairs");
        System.out.println("KB size after: " + improver.knowledgeBase.size());
        System.out.println();
        
        // Show what was learned
        System.out.println("Sample learned facts:");
        var results = improver.knowledgeBase.retrieve("capital", 3);
        for (var r : results) {
            System.out.println("  [" + r.doc().id() + "] Q: " + r.doc().title());
            System.out.println("    A: " + r.doc().content().substring(0, Math.min(100, r.doc().content().length())) + "...");
        }
    }
}
