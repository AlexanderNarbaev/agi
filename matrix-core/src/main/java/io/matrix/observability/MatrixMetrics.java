package io.matrix.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MATRIX-specific Micrometer metrics — counters, gauges, timers
 * for evolution, neurons, HADES, mediators, and survival simulation.
 */
public class MatrixMetrics {

    private final Counter evolutionGenerations;
    private final AtomicLong fitnessBest = new AtomicLong(0);
    private final AtomicLong fitnessAvg = new AtomicLong(0);

    private final AtomicInteger neuronsActive = new AtomicInteger(0);
    private final AtomicInteger neuronsFrozen = new AtomicInteger(0);

    private final Counter actorMessages;
    private final Counter actorErrors;
    private final Timer actorProcessingTime;

    private final Counter hadesAlerts;
    private final Counter hadesIsolations;

    private final AtomicLong driverEnergy = new AtomicLong(0);
    private final AtomicLong driverCuriosity = new AtomicLong(0);
    private final AtomicLong driverSafety = new AtomicLong(0);

    private final Timer neuronEvaluateTime;
    private final Counter survivalRuns;
    private final Timer survivalRunTime;

    public MatrixMetrics(MeterRegistry registry) {
        evolutionGenerations = registry.counter("matrix_evolution_generations_total");
        Gauge.builder("matrix_evolution_fitness_best", fitnessBest, AtomicLong::get)
                .register(registry);
        Gauge.builder("matrix_evolution_fitness_avg", fitnessAvg, AtomicLong::get)
                .register(registry);

        Gauge.builder("matrix_neurons_active", neuronsActive, AtomicInteger::get)
                .register(registry);
        Gauge.builder("matrix_neurons_frozen", neuronsFrozen, AtomicInteger::get)
                .register(registry);

        actorMessages = registry.counter("matrix_actor_messages_total");
        actorErrors = registry.counter("matrix_actor_errors_total");
        actorProcessingTime = registry.timer("matrix_actor_processing_seconds");

        hadesAlerts = registry.counter("matrix_hades_alerts_total");
        hadesIsolations = registry.counter("matrix_hades_isolations_total");

        Gauge.builder("matrix_driver_energy", driverEnergy, AtomicLong::get)
                .register(registry);
        Gauge.builder("matrix_driver_curiosity", driverCuriosity, AtomicLong::get)
                .register(registry);
        Gauge.builder("matrix_driver_safety", driverSafety, AtomicLong::get)
                .register(registry);

        neuronEvaluateTime = registry.timer("matrix_neuron_evaluate_seconds");
        survivalRuns = registry.counter("matrix_survival_runs_total");
        survivalRunTime = registry.timer("matrix_survival_run_seconds");
    }

    public void evolutionGeneration() { evolutionGenerations.increment(); }
    public void fitnessBest(long v) { fitnessBest.set(v); }
    public void fitnessAvg(long v) { fitnessAvg.set(v); }

    public void neuronsActive(int n) { neuronsActive.set(n); }
    public void neuronsFrozen(int n) { neuronsFrozen.set(n); }

    public void actorMessage() { actorMessages.increment(); }
    public void actorError() { actorErrors.increment(); }
    public Timer.Sample startActorTimer() { return Timer.start(); }
    public void stopActorTimer(Timer.Sample sample) { sample.stop(actorProcessingTime); }

    public void hadesAlert() { hadesAlerts.increment(); }
    public void hadesIsolation() { hadesIsolations.increment(); }

    public void driverEnergy(long v) { driverEnergy.set(v); }
    public void driverCuriosity(long v) { driverCuriosity.set(v); }
    public void driverSafety(long v) { driverSafety.set(v); }

    public Timer.Sample startNeuronEval() { return Timer.start(); }
    public void stopNeuronEval(Timer.Sample sample) { sample.stop(neuronEvaluateTime); }

    public void survivalRun() { survivalRuns.increment(); }
    public Timer.Sample startSurvivalRun() { return Timer.start(); }
    public void stopSurvivalRun(Timer.Sample sample) { sample.stop(survivalRunTime); }
}
