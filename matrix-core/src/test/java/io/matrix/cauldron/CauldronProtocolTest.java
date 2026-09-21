package io.matrix.cauldron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class CauldronProtocolTest {

    @Test
    void shouldEvolveInSimpleEnvironment() {
        CauldronProtocol cauldron = new CauldronProtocol(new Random(42));

        var result = cauldron.evolve(10, 10, 3, 2, 50, 2, 10, 5, 18);

        assertThat(result.state()).isEqualTo(CauldronProtocol.CauldronState.COMPLETED);
        assertThat(result.bestBrain()).isNotNull();
        assertThat(result.bestFitness()).isGreaterThan(0);
    }

    @Test
    void shouldEvolveForNamedTask() {
        CauldronProtocol cauldron = new CauldronProtocol(new Random(42));

        var result = cauldron.evolveForTask("resource_collection");

        assertThat(result.state()).isEqualTo(CauldronProtocol.CauldronState.COMPLETED);
    }

    @Test
    void shouldPackageResult() {
        CauldronProtocol cauldron = new CauldronProtocol(new Random(42));
        var result = cauldron.evolveForTask("navigation");

        var fnl = cauldron.packageResult(result, "navigation", "NAVIGATION", "instance-1");

        assertThat(fnl).isNotNull();
        assertThat(fnl.name()).contains("navigation");
        assertThat(fnl.type()).isEqualTo("NAVIGATION");
        assertThat(fnl.tags()).contains("cauldron", "navigation");
    }

    @Test
    void shouldLogCauldronActivity() {
        CauldronProtocol cauldron = new CauldronProtocol(new Random(42));
        cauldron.evolveForTask("simple_task");

        assertThat(cauldron.cauldronLog()).isNotEmpty();
        assertThat(cauldron.cauldronLog().get(0)).contains("CAULDRON:START");
    }

    @Test
    void shouldReturnNullPackageForFailedResult() {
        CauldronProtocol cauldron = new CauldronProtocol(new Random(42));
        var failed = CauldronProtocol.CauldronResult.failed("test failure");

        var fnl = cauldron.packageResult(failed, "test", "TEST", "i1");
        assertThat(fnl).isNull();
    }

    @Test
    void shouldAdaptTaskComplexity() {
        CauldronProtocol cauldron = new CauldronProtocol(new Random(42));

        var simple = cauldron.evolveForTask("simple");
        var complex = cauldron.evolveForTask("complex task");

        assertThat(simple.bestFitness()).isGreaterThan(0);
        assertThat(complex.bestFitness()).isGreaterThan(0);
    }
}
