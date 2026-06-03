package io.matrix.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatrixMetricsTest {

    private MeterRegistry registry;
    private MatrixMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MatrixMetrics(registry);
    }

    @Test
    void shouldRegisterAllMetrics() {
        assertThat(registry.getMeters()).isNotEmpty();
        assertThat(registry.get("matrix_evolution_generations_total").counter()).isNotNull();
        assertThat(registry.get("matrix_evolution_fitness_best").gauge()).isNotNull();
        assertThat(registry.get("matrix_evolution_fitness_avg").gauge()).isNotNull();
        assertThat(registry.get("matrix_neurons_active").gauge()).isNotNull();
        assertThat(registry.get("matrix_neurons_frozen").gauge()).isNotNull();
        assertThat(registry.get("matrix_actor_messages_total").counter()).isNotNull();
        assertThat(registry.get("matrix_actor_errors_total").counter()).isNotNull();
    }

    @Test
    void shouldTrackEvolutionMetrics() {
        metrics.evolutionGeneration();
        metrics.evolutionGeneration();

        assertThat(registry.get("matrix_evolution_generations_total")
                .counter().count()).isEqualTo(2.0);
    }

    @Test
    void shouldTrackFitness() {
        metrics.fitnessBest(150);
        metrics.fitnessAvg(100);

        assertThat(registry.get("matrix_evolution_fitness_best")
                .gauge().value()).isEqualTo(150.0);
        assertThat(registry.get("matrix_evolution_fitness_avg")
                .gauge().value()).isEqualTo(100.0);
    }

    @Test
    void shouldTrackNeuronCounts() {
        metrics.neuronsActive(42);
        metrics.neuronsFrozen(7);

        assertThat(registry.get("matrix_neurons_active")
                .gauge().value()).isEqualTo(42.0);
        assertThat(registry.get("matrix_neurons_frozen")
                .gauge().value()).isEqualTo(7.0);
    }

    @Test
    void shouldTrackActorMessages() {
        metrics.actorMessage();
        metrics.actorMessage();
        metrics.actorError();

        assertThat(registry.get("matrix_actor_messages_total")
                .counter().count()).isEqualTo(2.0);
        assertThat(registry.get("matrix_actor_errors_total")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void shouldTrackActorProcessingTime() {
        var sample = metrics.startActorTimer();
        metrics.stopActorTimer(sample);

        assertThat(registry.get("matrix_actor_processing_seconds")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void shouldTrackHadesMetrics() {
        metrics.hadesAlert();
        metrics.hadesAlert();
        metrics.hadesIsolation();

        assertThat(registry.get("matrix_hades_alerts_total")
                .counter().count()).isEqualTo(2.0);
        assertThat(registry.get("matrix_hades_isolations_total")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void shouldTrackDriverMetrics() {
        metrics.driverEnergy(75);
        metrics.driverCuriosity(60);
        metrics.driverSafety(30);

        assertThat(registry.get("matrix_driver_energy")
                .gauge().value()).isEqualTo(75.0);
        assertThat(registry.get("matrix_driver_curiosity")
                .gauge().value()).isEqualTo(60.0);
        assertThat(registry.get("matrix_driver_safety")
                .gauge().value()).isEqualTo(30.0);
    }

    @Test
    void shouldTrackNeuronEvaluationTime() {
        var sample = metrics.startNeuronEval();
        metrics.stopNeuronEval(sample);

        assertThat(registry.get("matrix_neuron_evaluate_seconds")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void shouldTrackSurvivalMetrics() {
        var sample = metrics.startSurvivalRun();
        metrics.survivalRun();
        metrics.stopSurvivalRun(sample);

        assertThat(registry.get("matrix_survival_runs_total")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("matrix_survival_run_seconds")
                .timer().count()).isEqualTo(1);
    }
}
