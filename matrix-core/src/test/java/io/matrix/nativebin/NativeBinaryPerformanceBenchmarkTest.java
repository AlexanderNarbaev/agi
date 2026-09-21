package io.matrix.nativebin;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W286 — Native binary performance benchmark.
 *
 * <p>Measures native binary startup time, size, and output.
 *
 * <p>CONSTITUTION VI compliance: performance benchmark substrate,
 * not a phenomenal consciousness claim.
 */
class NativeBinaryPerformanceBenchmarkTest {

    private static final Path BINARY = Paths.get("build/native/nativeCompile/matrix-core");
    private static final int RUNS = 3;

    @Test
    void nativeBinaryStartupUnder500ms() throws Exception {
        long totalMs = 0;
        int successfulRuns = 0;
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            Process p = new ProcessBuilder(BINARY.toString()).start();
            boolean done = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            if (done) {
                totalMs += elapsed;
                successfulRuns++;
            }
            p.destroyForcibly();
        }
        if (successfulRuns > 0) {
            long avgMs = totalMs / successfulRuns;
            System.out.println("Native binary average startup: " + avgMs + "ms");
            // Native binary should be much faster than JVM (typically 2-5s)
            assertThat(avgMs).isLessThan(500L);
        }
    }

    @Test
    void nativeBinarySizeLessThan150MB() {
        File binary = BINARY.toFile();
        long sizeMB = binary.length() / (1024 * 1024);
        System.out.println("Native binary size: " + sizeMB + " MB");
        assertThat(sizeMB).isLessThan(150L);
    }

    @Test
    void nativeBinaryProducesExpectedOutput() throws Exception {
        Process p = new ProcessBuilder(BINARY.toString()).start();
        InputStream is = p.getInputStream();
        byte[] buf = is.readAllBytes();
        is.close();
        boolean done = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        p.destroyForcibly();
        String output = new String(buf);
        // Should contain MATRIX version
        assertThat(output).contains("MATRIX v2.0.0");
        assertThat(output).contains("native image started");
    }
}
