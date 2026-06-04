package io.matrix;

import io.quarkus.test.junit.main.QuarkusMainTest;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusMainTest
class NativeSmokeTest {

    @Test
    @Launch({"--help"})
    void shouldPrintHelp(LaunchResult result) {
        assertThat(result.getOutput())
                .contains("matrix")
                .contains("MATRIX");
    }

    @Test
    @Launch({"simulate", "-g", "1", "-p", "2"})
    void shouldRunSimulate(LaunchResult result) {
        assertThat(result.exitCode()).isEqualTo(0);
    }

    @Test
    @Launch({"evolution", "-g", "1", "-p", "2"})
    void shouldRunEvolution(LaunchResult result) {
        assertThat(result.exitCode()).isEqualTo(0);
    }
}
