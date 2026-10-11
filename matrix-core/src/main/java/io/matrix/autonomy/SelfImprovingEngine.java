package io.matrix.autonomy;

import io.matrix.lifecycle.AutonomyImpulse;
import io.matrix.lifecycle.ImpulseScheduler;

import java.util.concurrent.atomic.AtomicLong;

/**
 * W496 — Self-Improving Engine.
 * 
 * Extends AutonomyEngine with learning capability:
 * - Runs brain cycles (as AutonomyEngine does)
 * - Learns from conversations periodically
 * - Improves future responses through knowledge base
 * 
 * This is the SELF-IMPROVEMENT LOOP:
 * 1. Brain talks to user
 * 2. Conversations recorded as NDJSON
 * 3. Learns from conversations
 * 4. Knowledge base grows
 * 5. Future responses grounded in learned knowledge
 */
public final class SelfImprovingEngine {
    
    private final LearningLoop learningLoop;
    private final ImpulseScheduler scheduler;
    private final AtomicLong learnCycles = new AtomicLong();
    private final AtomicLong talkCycles = new AtomicLong();
    private volatile boolean running = false;
    
    public SelfImprovingEngine(LearningLoop learningLoop, ImpulseScheduler scheduler) {
        this.learningLoop = learningLoop;
        this.scheduler = scheduler;
    }
    
    /**
     * Run a talk cycle with optional learning.
     */
    public void start() {
        if (running) return;
        running = true;
        
        // Learn immediately on start
        learnFromConversations();
        
        // Then learn periodically (every 5 minutes)
        // In production: scheduleAtFixedRate(learnFromConversations, 300, 300, SECONDS);
    }
    
    public void stop() {
        running = false;
    }
    
    /**
     * Talk to the brain.
     */
    public String talk(String message) {
        talkCycles.incrementAndGet();
        var result = learningLoop.talk(message);
        scheduler.fire(AutonomyImpulse.CURIOSITY, 50, java.util.Map.of());
        return result.reply();
    }
    
    /**
     * Learn from conversations (manual trigger).
     */
    public int learnFromConversations() {
        int learned = learningLoop.learn();
        learnCycles.incrementAndGet();
        if (learned > 0) {
            System.out.println("[self-improve] Learned " + learned + " facts");
        }
        return learned;
    }
    
    public long getLearnCycles() { return learnCycles.get(); }
    public long getTalkCycles() { return talkCycles.get(); }
    public LearningLoop getLearningLoop() { return learningLoop; }
    public boolean isRunning() { return running; }
    
    public void close() {
        learningLoop.close();
    }
}
