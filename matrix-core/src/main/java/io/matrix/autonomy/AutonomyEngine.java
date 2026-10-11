package io.matrix.autonomy;
import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.brain.BrainCycle;
import io.matrix.knowledge.SimpleKnowledgeBase;

import io.matrix.brain.BrainCycle;
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
    
    private final BrainCycle brain;
    private final ImpulseScheduler scheduler;
    private final ScheduledExecutorService clock;
    private final AtomicLong cycleCount = new AtomicLong();
    private final AtomicLong totalReflections = new AtomicLong();
    private final AtomicReference<String> lastReflection = new AtomicReference<>();
    private final MatrixTrace trace = new MatrixTrace();
    private volatile boolean running = false;
    
    public AutonomyEngine(BrainCycle brain, ImpulseScheduler scheduler) {
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
            BrainCycle.CycleResult r = brain.cycle(
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
            BrainCycle.CycleResult r = brain.cycle(q);
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
}
