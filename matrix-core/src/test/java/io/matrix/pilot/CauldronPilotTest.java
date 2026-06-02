package io.matrix.pilot;

import io.matrix.cauldron.CauldronProtocol;
import io.matrix.noosphere.FnlPackage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pilot #5: Cauldron — FNL auto-generation via GA")
class CauldronPilotTest {

    private final Random rng = new Random(42);

    @Test
    @DisplayName("Should evolve an FNL for a simple task")
    void should_evolveSimpleFnl() {
        CauldronProtocol cauldron = new CauldronProtocol(rng);
        var result = cauldron.evolve(10, 10, 3, 2, 30, 1, 10, 5, 16);

        assertThat(result.state()).isEqualTo(CauldronProtocol.CauldronState.COMPLETED);
        assertThat(result.bestBrain()).isNotNull();
        assertThat(result.bestFitness()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should run Cauldron for named tasks")
    void should_evolveForNamedTask() {
        CauldronProtocol cauldron = new CauldronProtocol(rng);
        var result = cauldron.evolveForTask("simple navigation");

        assertThat(result.state()).isEqualTo(CauldronProtocol.CauldronState.COMPLETED);
        assertThat(result.generations()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should package Cauldron result as FNL")
    void should_packageResultAsFnl() {
        CauldronProtocol cauldron = new CauldronProtocol(rng);
        var result = cauldron.evolveForTask("simple test");

        FnlPackage pkg = cauldron.packageResult(result, "simple test", "NAVIGATION", "test-instance");

        assertThat(pkg).isNotNull();
        assertThat(pkg.name()).contains("test");
        assertThat(pkg.type()).isEqualTo("NAVIGATION");
        assertThat(pkg.version()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("Should log Cauldron operations")
    void should_logOperations() {
        CauldronProtocol cauldron = new CauldronProtocol(rng);
        cauldron.evolveForTask("test");

        assertThat(cauldron.cauldronLog()).isNotEmpty();
        assertThat(cauldron.cauldronLog().get(0)).contains("CAULDRON:START");
    }
}
