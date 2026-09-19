package io.matrix.autonomy;
import io.matrix.budgeter.ConjugateBudgeter;

import io.matrix.brain.LlmBrainLoopService;
import io.matrix.ethics.EthicalFilter;
import io.matrix.lifecycle.AutonomyImpulse;
import io.matrix.lifecycle.ImpulseScheduler;
import io.matrix.auditor.MatrixTrace;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * W476 — Autonomy Engine: self-initiating brain loop.
 */
public final class AutonomyEngine {
    
    private final LlmBrainLoopService brain;
    private final ImpulseScheduler scheduler;
    private final ScheduledExecutorService clock;
    private final AtomicLong cycleCount = new AtomicLong();
    private final AtomicLong totalReflections = new AtomicLong();
    private final AtomicReference<String> lastReflection = new AtomicReference<>();
    private final MatrixTrace trace = new MatrixTrace();
    private volatile boolean running = false;
    
    public AutonomyEngine(LlmBrainLoopService brain, ImpulseScheduler scheduler) {
        this.brain = brain;
        this.scheduler = scheduler;
        this.clock = Executors.newScheduledThreadPool(2);
    }
    
    public void start() {
        if (running) return;
        running = true;
        clock.scheduleAtFixedRate(this::idleCycle, 30, 30, TimeUnit.SECONDS);
        clock.scheduleAtFixedRate(this::curiosityCycle, 120, 120, TimeUnit.SECONDS);
        clock.scheduleAtFixedRate(this::integrityCycle, 300, 300, TimeUnit.SECONDS);
    }
    
    public void stop() {
        clock.shutdown();
        running = false;
    }
    
    void idleCycle() {
        cycleCount.incrementAndGet();
        try (var span = trace.begin("autonomy.idle")) {
            LlmBrainLoopService.CycleResult r = brain.cycle(
                "Reflect briefly on what you might want to do next.");
            totalReflections.incrementAndGet();
            lastReflection.set(r.reply());
            if (r.action().contains("GOAL:") || r.reply().toLowerCase().contains("investigate")) {
                scheduler.fire(AutonomyImpulse.CURIOSITY, 100, java.util.Map.of());
            }
        }
    }
    
    void curiosityCycle() {
        try (var span = trace.begin("autonomy.curiosity")) {
            String[] questions = {
                "What patterns am I noticing?",
                "What should I learn more about?",
                "What connections am I missing?",
                "What is worth remembering?"
            };
            String q = questions[(int) (cycleCount.get() % questions.length)];
            LlmBrainLoopService.CycleResult r = brain.cycle(q);
            lastReflection.set(r.reply());
            scheduler.fire(AutonomyImpulse.CURIOSITY, 50, java.util.Map.of());
        }
        cycleCount.incrementAndGet();
    }
    
    void integrityCycle() {
        try (var span = trace.begin("autonomy.integrity")) {
            scheduler.fire(AutonomyImpulse.INTEGRITY_CHECK, 25, java.util.Map.of());
            brain.cycle("Validate current state.");
        }
        cycleCount.incrementAndGet();
    }
    
    public long getCycleCount() { return cycleCount.get(); }
    public long getTotalReflections() { return totalReflections.get(); }
    public String getLastReflection() { return lastReflection.get(); }
    public boolean isRunning() { return running; }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: AutonomyEngine <model-path> [duration-sec]");
            System.exit(1);
        }
        String modelPath = args[0];
        int durationSec = args.length > 1 ? Integer.parseInt(args[1]) : 35;
        
        LlmBrainLoopService brain = new LlmBrainLoopService(modelPath);
        io.matrix.budgeter.ConjugateBudgeter budgeter = new ConjugateBudgeter();
        EthicalFilter ethics = new EthicalFilter();
        ImpulseScheduler scheduler = new ImpulseScheduler(budgeter, ethics);
        
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
}
