package io.matrix.nativebin;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W290 — Native binary CLI test.
 *
 * <p>Verifies all CLI commands work in the native binary.
 */
class NativeBinaryCLITest {

    private static final Path BINARY = Paths.get("build/native/nativeCompile/matrix-core");

    private String run(String... args) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(BINARY.toString());
        for (String arg : args) cmd.add(arg);
        Process p = new ProcessBuilder(cmd).start();
        InputStream is = p.getInputStream();
        String output = new String(is.readAllBytes());
        is.close();
        p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        p.destroyForcibly();
        return output;
    }

    @Test
    void versionFlagWorks() throws Exception {
        String out = run("--version");
        assertThat(out).contains("MATRIX v2.0.0");
        assertThat(out).contains("native image");
    }

    @Test
    void helpFlagWorks() throws Exception {
        String out = run("--help");
        assertThat(out).contains("--version");
        assertThat(out).contains("--help");
        assertThat(out).contains("--status");
        assertThat(out).contains("--bench");
    }

    @Test
    void statusFlagWorks() throws Exception {
        String out = run("--status");
        assertThat(out).contains("Version:");
        assertThat(out).contains("GC:");
        assertThat(out).contains("epsilon");
    }

    @Test
    void benchFlagWorks() throws Exception {
        String out = run("--bench");
        assertThat(out).contains("Benchmark:");
        assertThat(out).contains("Throughput");
    }

    @Test
    void noArgsPrintsBanner() throws Exception {
        String out = run();
        assertThat(out).contains("MATRIX v2.0.0");
        assertThat(out).contains("native image started");
    }

    @Test
    void unknownFlagShowsHelp() throws Exception {
        String out = run("--unknown-flag");
        assertThat(out).contains("Unknown flag");
        assertThat(out).contains("--help");
    }
}
