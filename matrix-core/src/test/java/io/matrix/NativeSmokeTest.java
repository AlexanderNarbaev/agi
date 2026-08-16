package io.matrix;

import io.quarkus.test.junit.main.QuarkusMainTest;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLI smoke tests for GraalVM native image verification.
 *
 * <p>Requires {@code -Dquarkus.native.enabled=true} and full infrastructure
 * (Kafka, Redis, PostgreSQL). Run as part of CI native-image pipeline.
 */
@QuarkusMainTest
@Tag("native")
@Disabled("Requires GraalVM native image or full infrastructure. Run with: ./gradlew build -Dquarkus.native.enabled=true")
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
