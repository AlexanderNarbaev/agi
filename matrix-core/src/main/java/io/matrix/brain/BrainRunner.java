package io.matrix.brain;

import io.matrix.knowledge.SimpleKnowledgeBase;
import io.matrix.learning.ConversationLearner;
import io.matrix.learning.BrainImprover;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

/**
 * W517 — Brain Runner (continuous brain).
 * 
 * The "living brain" that runs continuously:
 * 1. Talks to user (with RAG + confidence filter)
 * 2. Records conversations to NDJSON
 * 3. Learns from recorded conversations periodically
 * 4. Improves knowledge base over time
 * 5. Logs metrics
 * 
 * Usage:
 *   java io.matrix.brain.BrainRunner [model-path]
 *   Default: models/onnx/qwen05b
 * 
 * Commands: 'quit', 'learn', 'stats', 'help'
 */
public final class BrainRunner {
    
    private final LlmBrainLoopRag brain;
    private final ConfidenceFilter filter;
    private final BrainImprover improver;
    private final SimpleKnowledgeBase knowledgeBase;
    private final Path conversationsDir;
    private final AtomicLong totalTalks = new AtomicLong(0);
    private final AtomicLong totalLearned = new AtomicLong(0);
    private final AtomicLong rejectedCount = new AtomicLong(0);
    
    public BrainRunner(String modelPath) {
        this.conversationsDir = Paths.get("data/conversations");
        this.knowledgeBase = new SimpleKnowledgeBase();
        this.brain = new LlmBrainLoopRag(modelPath, knowledgeBase);
        this.filter = new ConfidenceFilter(0.3);
        this.improver = new BrainImprover(conversationsDir);
        
        // Initial learning from existing conversations
        try {
            int learned = improver.improveOnce();
            totalLearned.addAndGet(learned);
        } catch (IOException e) {
            // ignore
        }
    }
    
    /**
     * Talk to the brain. Returns response if accepted, or rejection message.
     */
    public String talk(String message) {
        totalTalks.incrementAndGet();
        BrainCycle.CycleResult result = brain.cycle(message);
        
        String filtered = filter.filter(result.reply(), result.confidence());
        if (filtered == null) {
            rejectedCount.incrementAndGet();
            return filter.rejectionMessage(result.confidence());
        }
        
        return result.reply();
    }
    
    /**
     * Learn from conversations. Returns facts learned.
     */
    public int learn() {
        try {
            int learned = improver.improveOnce();
            totalLearned.addAndGet(learned);
            return learned;
        } catch (IOException e) {
            return 0;
        }
    }
    
    /**
     * Get statistics.
     */
    public String stats() {
        return String.format(
            "Talks: %d, Rejected: %d, Learned: %d, KB: %d",
            totalTalks.get(), rejectedCount.get(), totalLearned.get(), knowledgeBase.size()
        );
    }
    
    public void close() {
        brain.close();
    }
    
    public static void main(String[] args) throws Exception {
        String modelPath = args.length > 0 ? args[0] : "models/onnx/qwen05b";
        
        System.out.println("MATRIX Brain Runner (W517)");
        System.out.println("Model: " + modelPath);
        System.out.println("Commands: quit, learn, stats, help");
        System.out.println();
        
        BrainRunner runner = new BrainRunner(modelPath);
        
        // Initial learning
        int learned = runner.learn();
        System.out.println("Initial learning: " + learned + " facts");
        System.out.println("KB size: " + runner.knowledgeBase.size());
        System.out.println();
        
        // Interactive loop
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print(">>> ");
            System.out.flush();
            String line = reader.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if ("quit".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)) {
                System.out.println("Goodbye.");
                break;
            }
            if ("learn".equalsIgnoreCase(line)) {
                int l = runner.learn();
                System.out.println("Learned " + l + " facts");
                continue;
            }
            if ("stats".equalsIgnoreCase(line)) {
                System.out.println(runner.stats());
                continue;
            }
            if ("help".equalsIgnoreCase(line)) {
                System.out.println("Commands: quit, learn, stats, help");
                continue;
            }
            
            String reply = runner.talk(line);
            System.out.println("[MATRIX] " + reply);
            System.out.println();
        }
        
        System.out.println(runner.stats());
        runner.close();
    }
}
