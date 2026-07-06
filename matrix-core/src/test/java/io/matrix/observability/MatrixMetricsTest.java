package io.matrix.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.matrix.mediator.DriverType;
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
        assertThat(registry.get("matrix.evolution.generations").counter()).isNotNull();
        assertThat(registry.get("matrix.evolution.fitness.best").gauge()).isNotNull();
        assertThat(registry.get("matrix.evolution.fitness.avg").gauge()).isNotNull();
        assertThat(registry.get("matrix.neurons.active").gauge()).isNotNull();
        assertThat(registry.get("matrix.neurons.frozen").gauge()).isNotNull();
        assertThat(registry.get("matrix.actor.messages").counter()).isNotNull();
        assertThat(registry.get("matrix.actor.errors").counter()).isNotNull();
    }

    @Test
    void shouldTrackEvolutionMetrics() {
        metrics.evolutionGeneration();
        metrics.evolutionGeneration();

        assertThat(registry.get("matrix.evolution.generations")
                .counter().count()).isEqualTo(2.0);
    }

    @Test
    void shouldTrackFitness() {
        metrics.fitnessBest(150);
        metrics.fitnessAvg(100);

        assertThat(registry.get("matrix.evolution.fitness.best")
                .gauge().value()).isEqualTo(150.0);
        assertThat(registry.get("matrix.evolution.fitness.avg")
                .gauge().value()).isEqualTo(100.0);
    }

    @Test
    void shouldTrackNeuronCounts() {
        metrics.neuronsActive(42);
        metrics.neuronsFrozen(7);

        assertThat(registry.get("matrix.neurons.active")
                .gauge().value()).isEqualTo(42.0);
        assertThat(registry.get("matrix.neurons.frozen")
                .gauge().value()).isEqualTo(7.0);
    }

    @Test
    void shouldTrackActorMessages() {
        metrics.actorMessage();
        metrics.actorMessage();
        metrics.actorError();

        assertThat(registry.get("matrix.actor.messages")
                .counter().count()).isEqualTo(2.0);
        assertThat(registry.get("matrix.actor.errors")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void shouldTrackActorProcessingTime() {
        var sample = metrics.startActorTimer();
        metrics.stopActorTimer(sample);

        assertThat(registry.get("matrix.actor.processing.time")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void shouldTrackHadesMetrics() {
        metrics.hadesAlert();
        metrics.hadesAlert();
        metrics.hadesIsolation();

        assertThat(registry.get("matrix.hades.alerts")
                .counter().count()).isEqualTo(2.0);
        assertThat(registry.get("matrix.hades.isolations")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void shouldTrackDriverMetrics() {
        metrics.driverEnergy(75);
        metrics.driverCuriosity(60);
        metrics.driverSafety(30);

        assertThat(registry.get("matrix.driver.level")
                .tag("driver", "energy").gauge().value()).isEqualTo(75.0);
        assertThat(registry.get("matrix.driver.level")
                .tag("driver", "curiosity").gauge().value()).isEqualTo(60.0);
        assertThat(registry.get("matrix.driver.level")
                .tag("driver", "safety").gauge().value()).isEqualTo(30.0);
    }

    @Test
    void shouldTrackAll8DriverMetrics() {
        for (DriverType type : DriverType.values()) {
            metrics.driverLevel(type, 50);
            assertThat(registry.get("matrix.driver.level")
                    .tag("driver", type.name().toLowerCase())
                    .gauge().value()).isEqualTo(50.0);
        }
    }

    @Test
    void shouldTrackNeuronEvaluationTime() {
        var sample = metrics.startNeuronEval();
        metrics.stopNeuronEval(sample);

        assertThat(registry.get("matrix.neuron.evaluate.time")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void shouldTrackSurvivalMetrics() {
        var sample = metrics.startSurvivalRun();
        metrics.survivalRun();
        metrics.stopSurvivalRun(sample);

        assertThat(registry.get("matrix.survival.runs")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("matrix.survival.run.time")
                .timer().count()).isEqualTo(1);
    }
}
