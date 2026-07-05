package io.matrix;

import io.quarkus.test.junit.main.QuarkusMainTest;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the Quarkus main application.
 *
 * <p>These tests require full CDI bootstrap with all infrastructure beans
 * (Kafka, Redis, PostgreSQL). They are designed for native-image verification
 * in CI, not for regular unit testing.
 *
 * <p>Enable when:
 * <ul>
 *   <li>Running with {@code -Dquarkus.native.enabled=true} (GraalVM native image)</li>
 *   <li>Or having full Docker Compose stack running (Kafka + Redis + PostgreSQL)</li>
 * </ul>
 */
@QuarkusMainTest
@Disabled("Requires full infrastructure: Kafka, Redis, PostgreSQL. Run with docker compose up first.")
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
