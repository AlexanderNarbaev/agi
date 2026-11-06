package io.matrix.brain.runtime;

import io.matrix.autonomy.AutonomyEngine;
import io.matrix.autonomy.ImpulseProposalConsumer;
import io.matrix.brain.BrainCycle;
import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.ethics.EthicalFilter;
import io.matrix.goals.GoalTracker;
import io.matrix.lifecycle.ImpulseScheduler;
import io.matrix.reasoning.ArousalDynamics;
import io.matrix.reasoning.EmergenceAnalyzer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * TRUE-W4 — Autonomy loop with REAL core engines.
 *
 * <p>Wraps the real {@link AutonomyEngine} from {@code matrix-core/autonomy/}
 * alongside {@link ArousalDynamics} and {@link EmergenceAnalyzer} for
 * curiosity / novelty tracking. Goal lifecycle (spawn→pursue→complete)
 * is managed by the real {@link GoalTracker} from {@code matrix-core/goals/}.</p>
 *
 * <p>The mind now acts unprompted: at every {@link #noteActivity()}
 * the loop records arousal; on idle cycles (background thread) it
 * surveys open goals and proposes refinements.</p>
 */
public final class AutonomyLoop implements AutoCloseable {

    private final AutonomyEngine engine;
    private final ArousalDynamics arousal;
    private final EmergenceAnalyzer emergence;
    private final GoalTracker goals;
    private final Thread idleThread;
    private volatile boolean running = true;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public AutonomyLoop(BrainCycle brain) {
        // W47: the engine needs a REAL ImpulseScheduler.
        //
        // This loop used to pass null. The engine's curiosity and integrity
        // cycles fire their drives through the scheduler, so with a null
        // scheduler the first scheduled cycle threw a NullPointerException,
        // and a ScheduledExecutorService CANCELS a task that throws — silently
        // and permanently. The gateway therefore had one curiosity cycle, at
        // t=120s, and then no curiosity cycle at all; and no integrity cycle
        // ever, because the integrity check fires BEFORE the validation.
        //
        // A null scheduler is not a degraded engine, it is an engine whose
        // drives can never be expressed, so the scheduler is built here for
        // real: the same construction the gateway's own launchers use, with the
        // conjugate budgeter that pays for the drive and the ethical filter
        // that gates it.
        ImpulseScheduler scheduler =
                new ImpulseScheduler(new ConjugateBudgeter(), new EthicalFilter());
        // W49 (OD-045): the accepted impulses become bounded internal
        // proposals. The consumer records an intent and does nothing else.
        ImpulseProposalConsumer proposals =
                ImpulseProposalConsumer.forServing(new EthicalFilter());
        this.engine = new AutonomyEngine(brain, scheduler, proposals);
        this.arousal = new ArousalDynamics(0.20, 0.05);
        this.emergence = new EmergenceAnalyzer(42L);
        this.goals = new GoalTracker();
        this.idleThread = new Thread(this::idleLoop, "matrix-autonomy-idle");
        this.idleThread.setDaemon(true);
    }

    /** Start the autonomy loop (idempotent). */
    public void start() {
        engine.start();
        if (!idleThread.isAlive()) idleThread.start();
    }

    /** Note an external stimulus (cycles activity counter; bumps arousal). */
    public void noteActivity() {
        // Predict small error to gently increase arousal.
        arousal.update(0.10);
        engine.start();  // ensure running
    }

    /** Submit a goal to the real GoalTracker. */
    public int proposeGoal(String description, int priority) {
        return goals.add(description, priority).id();
    }

    public void completeGoal(int id) {
        goals.complete(id);
    }

    public void abandonGoal(int id) {
        goals.abandon(id);
    }

    /** Run a manual reflection tick (compute emergence snapshot, reset arousal). */
    public synchronized ReflectionReport reflect() {
        int n = (int) Math.max(1, engine.getCycleCount());
        var snapshot = emergence.runCycles(1, 1, n);
        var lastSnap = snapshot.isEmpty() ? null
            : snapshot.get(snapshot.size() - 1);
        arousal.reset();
        return new ReflectionReport(
            engine.getCycleCount(),
            engine.getTotalReflections(),
            arousal.getArousal(),
            lastSnap != null ? lastSnap.entropy() : 0.0,
            engine.getLastReflection(),
            engine.getCycleCount()
        );
    }

    public AutonomyEngine engine() { return engine; }
    public ArousalDynamics arousal() { return arousal; }
    public EmergenceAnalyzer emergence() { return emergence; }
    public GoalTracker goals() { return goals; }

    public Map<String, Object> snapshot() {
        lock.readLock().lock();
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("running", engine.isRunning());
            m.put("cycle_count", engine.getCycleCount());
            m.put("total_reflections", engine.getTotalReflections());
            m.put("arousal", arousal.getArousal());
            m.put("active_goals", goals.activeGoals().size());
            m.put("completed_goals", goals.completedCount());
            m.put("abandoned_goals", 0);
            m.put("last_reflection", engine.getLastReflection());
            return m;
        } finally { lock.readLock().unlock(); }
    }

    /** Snapshot of one reflection tick (engine-identity-tagged). */
    public record ReflectionReport(
        long tickId,
        long totalReflections,
        double arousal,
        double emergenceEntropy,
        String lastReflection,
        long cycleCount
    ) {}

    @Override
    public void close() {
        running = false;
        engine.stop();
        idleThread.interrupt();
    }

    private void idleLoop() {
        while (running) {
            try {
                Thread.sleep(60_000);  // 1 min
                if (running) {
                    reflect();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                // Never let the autonomy loop die silently; log + continue.
                System.err.println("AutonomyLoop: " + t.getMessage());
            }
        }
    }
}
