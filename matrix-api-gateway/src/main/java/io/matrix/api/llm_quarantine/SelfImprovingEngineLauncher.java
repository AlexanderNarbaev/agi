package io.matrix.api.llm_quarantine;

import io.matrix.lifecycle.ImpulseScheduler;
import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.autonomy.SelfImprovingEngine;
import io.matrix.api.llm_quarantine.LlmBrainLoopRag;
import io.matrix.api.llm_quarantine.BrainLearningLoop;
import io.matrix.lifecycle.ImpulseScheduler;
import io.matrix.knowledge.SimpleKnowledgeBase;
import io.matrix.ethics.EthicalFilter;

/**
 * W35.1 — CLI launcher extracted out of {@link SelfImprovingEngine}.
 *
 * <p>The engine itself lives in matrix-core because matrix-brain-runtime
 * constructs it, and Runtime sits below Gateway in the dependency direction.
 * Only the {@code main} method needed an LLM, so only the launcher moved here.
 * This keeps CONSTITUTION Article I intact in both directions: the core stays
 * LLM-free, and the Runtime is not made to depend on the Gateway.</p>
 */
public final class SelfImprovingEngineLauncher {
    private SelfImprovingEngineLauncher() { }

    
    public static void runCli(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: SelfImprovingEngine <model-path>");
            System.exit(1);
        }
        String modelPath = args[0];
        
        System.out.println("=== Self-Improving Engine ===");
        System.out.println("Model: " + modelPath);
        System.out.println();
        
        // Create brain with learning
        BrainLearningLoop loop = new BrainLearningLoop(modelPath);
        
        // Create scheduler
        var budgeter = new io.matrix.budgeter.ConjugateBudgeter();
        var ethics = new io.matrix.ethics.EthicalFilter();
        var scheduler = new ImpulseScheduler(budgeter, ethics);
        
        SelfImprovingEngine engine = new SelfImprovingEngine(loop, scheduler);
        engine.start();
        
        System.out.println("Talk to the brain (type 'quit' to stop):");
        System.out.println();
        
        java.util.Scanner sc = new java.util.Scanner(System.in);
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if ("quit".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)) break;
            if (line.isEmpty()) continue;
            
            String reply = engine.talk(line);
            System.out.println("[MATRIX] " + reply);
            System.out.println();
            
            // Learn periodically
            if (engine.getTalkCycles() % 5 == 0) {
                int learned = engine.learnFromConversations();
                if (learned > 0) {
                    System.out.println("[self-improve] Learned " + learned + " new facts");
                }
            }
        }
        
        engine.close();
        sc.close();
        System.out.println("[self-improve] Stopped");
    }

    public static void main(String[] args) throws Exception {
        runCli(args);
    }
}
