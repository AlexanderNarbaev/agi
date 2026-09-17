package io.matrix.nativebin;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W291 — Native binary full integration test.
 *
 * <p>End-to-end verification: build + run + verify output.
 */
class NativeBinaryFullIntegrationTest {

    private static final Path BINARY = Paths.get("build/native/nativeCompile/matrix-core");

    @Test
    void fullIntegrationBuildAndRun() throws Exception {
        assertThat(BINARY.toFile().exists()).isTrue();

        // Run with all CLI commands
        List<String[]> testCases = List.of(
            new String[]{"--version", "MATRIX v2.0.0"},
            new String[]{"--help", "--version"},
            new String[]{"--status", "GC:"},
            new String[]{"--bench", "Benchmark:"}
        );

        for (String[] tc : testCases) {
            String arg = tc[0];
            String expected = tc[1];
            List<String> cmd = new ArrayList<>();
            cmd.add(BINARY.toString());
            cmd.add(arg);

            Process p = new ProcessBuilder(cmd).start();
            InputStream is = p.getInputStream();
            String out = new String(is.readAllBytes());
            is.close();
            boolean done = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            p.destroyForcibly();

            assertThat(done)
                .as("Process should complete within 5s for flag " + arg)
                .isTrue();
            assertThat(out)
                .as("Output should contain expected text for " + arg)
                .contains(expected);
        }
    }

    @Test
    void startupTimeUnder200ms() throws Exception {
        // Warm up
        for (int i = 0; i < 3; i++) {
            Process p = new ProcessBuilder(BINARY.toString(), "--version").start();
            p.waitFor();
            p.destroyForcibly();
        }

        // Measure
        long start = System.nanoTime();
        Process p = new ProcessBuilder(BINARY.toString(), "--version").start();
        p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        p.destroyForcibly();

        // Should be < 200ms for native image (vs JVM 2-5s)
        assertThat(elapsed).isLessThan(200L);
        System.out.println("Native --version startup: " + elapsed + "ms");
    }
}
