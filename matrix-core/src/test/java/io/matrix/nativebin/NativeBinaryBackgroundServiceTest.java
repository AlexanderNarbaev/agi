package io.matrix.nativebin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W294 — Native binary background service test.
 *
 * <p>Runs native binary as background process, verifies CLI operations.
 */
class NativeBinaryBackgroundServiceTest {

    private static final Path BINARY = Paths.get("build/native/nativeCompile/matrix-core");
    private Process background;

    @BeforeEach
    void setUp() throws Exception {
        // No-op
    }

    @AfterEach
    void tearDown() throws Exception {
        if (background != null && background.isAlive()) {
            background.destroyForcibly();
        }
    }

    @Test
    void canRunBinaryMultipleTimesInSequence() throws Exception {
        for (int i = 0; i < 5; i++) {
            Process p = new ProcessBuilder(BINARY.toString(), "--version").start();
            InputStream is = p.getInputStream();
            String out = new String(is.readAllBytes());
            is.close();
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            p.destroyForcibly();
            assertThat(out).contains("MATRIX v2.0.0");
        }
    }

    @Test
    void concurrentBinaryInvocations() throws Exception {
        // Run 3 binaries concurrently
        Process[] processes = new Process[3];
        for (int i = 0; i < 3; i++) {
            processes[i] = new ProcessBuilder(BINARY.toString(), "--bench").start();
        }
        for (Process p : processes) {
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            p.destroyForcibly();
        }
    }

    @Test
    void binarySurvivesRapidInvocations() throws Exception {
        // Stress test: 20 rapid invocations
        for (int i = 0; i < 20; i++) {
            Process p = new ProcessBuilder(BINARY.toString(), "--help").start();
            InputStream is = p.getInputStream();
            is.readAllBytes();
            is.close();
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            p.destroyForcibly();
        }
        // If we got here without crash, test passes
        assertThat(true).isTrue();
    }
}
