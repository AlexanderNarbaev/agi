package io.matrix.api.llm_quarantine;

import io.matrix.lifecycle.ImpulseScheduler;
import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.autonomy.AutonomyEngine;
import io.matrix.api.llm_quarantine.LlmBrainLoopRag;
import io.matrix.api.llm_quarantine.BrainLearningLoop;
import io.matrix.lifecycle.ImpulseScheduler;
import io.matrix.knowledge.SimpleKnowledgeBase;
import io.matrix.ethics.EthicalFilter;

/**
 * W35.1 — CLI launcher extracted out of {@link AutonomyEngine}.
 *
 * <p>The engine itself lives in matrix-core because matrix-brain-runtime
 * constructs it, and Runtime sits below Gateway in the dependency direction.
 * Only the {@code main} method needed an LLM, so only the launcher moved here.
 * This keeps CONSTITUTION Article I intact in both directions: the core stays
 * LLM-free, and the Runtime is not made to depend on the Gateway.</p>
 */
public final class AutonomyEngineLauncher {
    private AutonomyEngineLauncher() { }

    
    public static void runCli(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: AutonomyEngine <model-path> [duration-sec]");
            System.exit(1);
        }
        String modelPath = args[0];
        int durationSec = args.length > 1 ? Integer.parseInt(args[1]) : 35;
        
        io.matrix.budgeter.ConjugateBudgeter budgeter = new ConjugateBudgeter();
        EthicalFilter ethics = new EthicalFilter();
        ImpulseScheduler scheduler = new ImpulseScheduler(budgeter, ethics);
        
        LlmBrainLoopRag brain = new LlmBrainLoopRag(modelPath, new SimpleKnowledgeBase());
        AutonomyEngine engine = new AutonomyEngine(brain, scheduler);
        engine.start();
        
        System.out.println("===== Autonomy Engine Started =====");
        System.out.println("Model: " + modelPath);
        System.out.println("Duration: " + durationSec + "s");
        System.out.println("Schedule: idle=30s, curiosity=120s, integrity=300s");
        System.out.println();
        System.out.println("Self-initiating brain cycles. Press Ctrl+C to stop.");
        
        Thread.sleep(durationSec * 1000L);
        
        System.out.println();
        System.out.println("===== Autonomy Engine Report =====");
        System.out.println("Total cycles: " + engine.getCycleCount());
        System.out.println("Total reflections: " + engine.getTotalReflections());
        String last = engine.getLastReflection();
        System.out.println("Last reflection: " + 
            (last != null ? 
             last.substring(0, Math.min(200, last.length())) + "..." 
             : "(none)"));
        
        engine.stop();
        brain.close();
    }

    public static void main(String[] args) throws Exception {
        runCli(args);
    }
}
