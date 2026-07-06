package io.matrix.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MATRIX comprehensive Micrometer metrics — evolution, neurons, HADES,
 * bots, brain health, API traffic, and survival simulation.
 */
@ApplicationScoped
public class MatrixMetrics {

    private final MeterRegistry registry;

    // ── Evolution metrics (preserved) ──
    private final Counter evolutionGenerations;
    private final AtomicLong fitnessBest;
    private final AtomicLong fitnessAvg;

    // ── Neuron metrics (preserved) ──
    private final AtomicInteger neuronsActive;
    private final AtomicInteger neuronsFrozen;

    // ── Actor metrics (preserved) ──
    private final Counter actorMessages;
    private final Counter actorErrors;
    private final Timer actorProcessingTime;

    // ── HADES metrics (preserved) ──
    private final Counter hadesAlerts;
    private final Counter hadesIsolations;

    // ── Driver metrics (preserved) ──
    private final AtomicLong driverEnergy;
    private final AtomicLong driverCuriosity;
    private final AtomicLong driverSafety;

    // ── Neuron evaluation + survival (preserved) ──
    private final Timer neuronEvaluateTime;
    private final Counter survivalRuns;
    private final Timer survivalRunTime;

    // ── Brain health metrics (new) ──
    private final AtomicInteger stuckEvents;
    private final AtomicInteger explorationTicks;

    @Inject
    public MatrixMetrics(MeterRegistry registry) {
        this.registry = registry;

        // ── Evolution ──
        this.evolutionGenerations = registry.counter("matrix.evolution.generations",
                "description", "Total evolution generations run");
        this.fitnessBest = new AtomicLong(0);
        this.fitnessAvg = new AtomicLong(0);
        Gauge.builder("matrix.evolution.fitness.best", fitnessBest, AtomicLong::get)
                .description("Best fitness in current generation")
                .register(registry);
        Gauge.builder("matrix.evolution.fitness.avg", fitnessAvg, AtomicLong::get)
                .description("Average fitness in current generation")
                .register(registry);

        // ── Neurons ──
        this.neuronsActive = new AtomicInteger(0);
        this.neuronsFrozen = new AtomicInteger(0);
        Gauge.builder("matrix.neurons.active", neuronsActive, AtomicInteger::get)
                .description("Number of active neurons")
                .register(registry);
        Gauge.builder("matrix.neurons.frozen", neuronsFrozen, AtomicInteger::get)
                .description("Number of frozen neurons")
                .register(registry);

        // ── Actors ──
        this.actorMessages = registry.counter("matrix.actor.messages",
                "description", "Total actor messages processed");
        this.actorErrors = registry.counter("matrix.actor.errors",
                "description", "Total actor processing errors");
        this.actorProcessingTime = registry.timer("matrix.actor.processing.time",
                "description", "Actor message processing duration");

        // ── HADES ──
        this.hadesAlerts = registry.counter("matrix.hades.alerts",
                "description", "Total HADES derangement alerts");
        this.hadesIsolations = registry.counter("matrix.hades.isolations",
                "description", "Total HADES neuron isolations");

        // ── Drivers ──
        this.driverEnergy = new AtomicLong(0);
        this.driverCuriosity = new AtomicLong(0);
        this.driverSafety = new AtomicLong(0);
        Gauge.builder("matrix.driver.energy", driverEnergy, AtomicLong::get)
                .description("Energy driver level")
                .register(registry);
        Gauge.builder("matrix.driver.curiosity", driverCuriosity, AtomicLong::get)
                .description("Curiosity driver level")
                .register(registry);
        Gauge.builder("matrix.driver.safety", driverSafety, AtomicLong::get)
                .description("Safety driver level")
                .register(registry);

        // ── Neuron Eval ──
        this.neuronEvaluateTime = registry.timer("matrix.neuron.evaluate.time",
                "description", "Neuron evaluation duration");
        this.survivalRuns = registry.counter("matrix.survival.runs",
                "description", "Total survival simulation runs");
        this.survivalRunTime = registry.timer("matrix.survival.run.time",
                "description", "Survival simulation run duration");

        // ── Brain health (new) ──
        this.stuckEvents = new AtomicInteger(0);
        this.explorationTicks = new AtomicInteger(0);
        Gauge.builder("matrix.stuck.events", stuckEvents, AtomicInteger::get)
                .description("Cumulative stuck-detection events triggered")
                .register(registry);
        Gauge.builder("matrix.exploration.active", explorationTicks, AtomicInteger::get)
                .description("Exploration mode ticks remaining (0 = inactive)")
                .register(registry);
    }

    // ── Evolution ──
    public void evolutionGeneration() { evolutionGenerations.increment(); }
    public void fitnessBest(long v) { fitnessBest.set(v); }
    public void fitnessAvg(long v) { fitnessAvg.set(v); }

    // ── Neurons ──
    public void neuronsActive(int n) { neuronsActive.set(n); }
    public void neuronsFrozen(int n) { neuronsFrozen.set(n); }

    // ── Actors ──
    public void actorMessage() { actorMessages.increment(); }
    public void actorError() { actorErrors.increment(); }
    public Timer.Sample startActorTimer() { return Timer.start(); }
    public void stopActorTimer(Timer.Sample sample) { sample.stop(actorProcessingTime); }

    // ── HADES ──
    public void hadesAlert() { hadesAlerts.increment(); }
    public void hadesIsolation() { hadesIsolations.increment(); }

    // ── Drivers ──
    public void driverEnergy(long v) { driverEnergy.set(v); }
    public void driverCuriosity(long v) { driverCuriosity.set(v); }
    public void driverSafety(long v) { driverSafety.set(v); }

    // ── Neuron Eval ──
    public Timer.Sample startNeuronEval() { return Timer.start(); }
    public void stopNeuronEval(Timer.Sample sample) { sample.stop(neuronEvaluateTime); }

    // ── Survival ──
    public void survivalRun() { survivalRuns.increment(); }
    public Timer.Sample startSurvivalRun() { return Timer.start(); }
    public void stopSurvivalRun(Timer.Sample sample) { sample.stop(survivalRunTime); }

    // ── Bot metrics (per-bot counters with tags) ──

    /** Record a bot action tick. */
    public void recordBotTick(String botName, String role, String action) {
        registry.counter("matrix.bot.ticks",
                "bot", botName,
                "role", role).increment();
        registry.counter("matrix.bot.action",
                "bot", botName,
                "role", role,
                "action", action).increment();
    }

    /** Record a block mined by a bot. */
    public void recordBotMined(String botName, String role) {
        registry.counter("matrix.bot.mined",
                "bot", botName,
                "role", role).increment();
    }

    /** Record feedback received by a bot. */
    public void recordBotFeedback(String botName, String role) {
        registry.counter("matrix.bot.feedback",
                "bot", botName,
                "role", role).increment();
    }

    // ── Brain health ──

    /** Record a stuck-detection event. */
    public void recordStuck() { stuckEvents.incrementAndGet(); }

    /** Update exploration mode ticks remaining. */
    public void recordExploration(int remaining) { explorationTicks.set(remaining); }

    // ── API traffic ──

    /** Record a chat completion API request. */
    public void recordChatRequest() {
        registry.counter("matrix.api.requests", "endpoint", "chat").increment();
    }

    /** Record a sensor API request (WebSocket). */
    public void recordSensorRequest() {
        registry.counter("matrix.api.requests", "endpoint", "sensors").increment();
    }

    /** Record a training API request. */
    public void recordTrainRequest() {
        registry.counter("matrix.api.requests", "endpoint", "train").increment();
    }
}
