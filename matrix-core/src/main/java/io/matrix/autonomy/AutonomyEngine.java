package io.matrix.autonomy;
import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.brain.BrainCycle;
import io.matrix.knowledge.SimpleKnowledgeBase;

import io.matrix.brain.BrainCycle;
import io.matrix.ethics.EthicalFilter;
import io.matrix.lifecycle.AutonomyImpulse;
import io.matrix.lifecycle.ImpulseScheduler;
import io.matrix.auditor.MatrixTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * W476 — Autonomy Engine: self-initiating brain loop.
 *
 * <p>The engine reflects, and it expresses its internal drives — curiosity and
 * the integrity check — as IMPULSES. An impulse is not decoration: firing it is
 * what pays for the drive out of the conjugate budget and what puts it through
 * the FROZEN ethical gate. The engine therefore REQUIRES a scheduler: it is a
 * collaborator, not an optional extra.
 */
public final class AutonomyEngine {

    private static final Logger log = LoggerFactory.getLogger(AutonomyEngine.class);

    private final BrainCycle brain;
    private final ImpulseScheduler scheduler;
    private final ScheduledExecutorService clock;
    private final AtomicLong cycleCount = new AtomicLong();
    private final AtomicLong totalReflections = new AtomicLong();
    private final AtomicReference<String> lastReflection = new AtomicReference<>();
    private final MatrixTrace trace = new MatrixTrace();
    private volatile boolean running = false;

    /**
     * @param brain the brain cycle to reflect with
     * @param scheduler the scheduler the engine fires its drives through; it
     *        must be a real one — a null scheduler is not a degraded engine, it
     *        is an engine whose drives can never be expressed, and the failure
     *        used to arrive silently one hundred and twenty seconds later, when
     *        the first scheduled cycle dereferenced it
     */
    public AutonomyEngine(BrainCycle brain, ImpulseScheduler scheduler) {
        this.brain = Objects.requireNonNull(brain, "brain");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Executors.newScheduledThreadPool(2);
    }

    /** The scheduler this engine fires its drives through. */
    public ImpulseScheduler scheduler() { return scheduler; }

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
                fire(AutonomyImpulse.CURIOSITY, 100);
            }
        } catch (Exception e) {
            // A scheduled task that throws is CANCELLED by the executor, silently
            // and for good. A cycle that dies here is a dead cycle, so the failure
            // is logged and the next one still runs.
            log.warn("AutonomyEngine: the idle cycle failed: {}", e.getMessage());
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
            fire(AutonomyImpulse.CURIOSITY, 50);
        } catch (Exception e) {
            log.warn("AutonomyEngine: the curiosity cycle failed: {}", e.getMessage());
        }
        cycleCount.incrementAndGet();
    }

    void integrityCycle() {
        try (var span = trace.begin("autonomy.integrity")) {
            fire(AutonomyImpulse.INTEGRITY_CHECK, 25);
            brain.cycle("Validate current state.");
        } catch (Exception e) {
            log.warn("AutonomyEngine: the integrity cycle failed: {}", e.getMessage());
        }
        cycleCount.incrementAndGet();
    }

    /**
     * Fire one drive.
     *
     * <p>A drive that the budget or the ethics refuse is not silently dropped:
     * the refusal is logged, because a curiosity that never fires and a curiosity
     * that was refused look identical from the outside otherwise.
     */
    private void fire(AutonomyImpulse impulse, long envelope) {
        ImpulseScheduler.FireRecord record = scheduler.fire(impulse, envelope, Map.of());
        if (record.outcome() != ImpulseScheduler.ImpulseOutcome.FIRED) {
            log.info("AutonomyEngine: the {} impulse was not fired: {}",
                    impulse, record.outcome());
        }
    }

    public long getCycleCount() { return cycleCount.get(); }
    public long getTotalReflections() { return totalReflections.get(); }
    public String getLastReflection() { return lastReflection.get(); }
    public boolean isRunning() { return running; }
}
