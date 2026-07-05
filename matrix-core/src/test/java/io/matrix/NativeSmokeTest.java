package io.matrix;

import io.quarkus.test.junit.main.QuarkusMainTest;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Smoke tests for the Quarkus main application CLI.
 *
 * <p>Requires full CDI with Kafka + Redis + PostgreSQL.
 * Automatically skipped when infrastructure is unavailable.
 */
@QuarkusMainTest
class NativeSmokeTest {

    @BeforeAll
    static void checkInfra() {
        boolean kafkaUp = isPortOpen("localhost", 9092);
        boolean redisUp = isPortOpen("localhost", 6379);
        assumeTrue(kafkaUp && redisUp,
                "Skipping smoke tests — infrastructure not available. Run: ./scripts/matrix-full.sh start");
    }

    private static boolean isPortOpen(String host, int port) {
        try (var s = new java.net.Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

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
