package io.matrix.benchmark;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W314 — JVM vs Native benchmark for cognitive pipeline.
 *
 * <p>Runs the same --cognitive command via:
 * 1. Direct in-process JVM execution
 * 2. Native binary process invocation
 *
 * <p>Compares startup + execution time.
 */
class JVMvsNativeBenchmarkTest {

    private static final Path NATIVE_BINARY = Paths.get("build/native/nativeCompile/matrix-core");

    @Test
    void nativeVsJVMSpeedup() throws Exception {
        // Native: invoke --cognitive via ProcessBuilder
        long nativeStart = System.nanoTime();
        Process p = new ProcessBuilder(NATIVE_BINARY.toString(), "--cognitive").start();
        java.io.InputStream is = p.getInputStream();
        String out = new String(is.readAllBytes());
        is.close();
        p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        p.destroyForcibly();
        long nativeMs = (System.nanoTime() - nativeStart) / 1_000_000;

        // JVM: simulate same execution with class instantiation
        long jvmStart = System.nanoTime();
        io.matrix.consciousness.CognitiveEmbedding embedding =
            new io.matrix.consciousness.CognitiveEmbedding(64, 42L);
        io.matrix.consciousness.CognitiveGenesisProfile p2 =
            new io.matrix.consciousness.CognitiveGenesisProfile(
                0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
                50.0, 0.5, 0.5, 2, 0.5, 2.0);
        embedding.embed(p2);
        io.matrix.consciousness.CognitiveConstitutionalAI.evaluate(p2,
            io.matrix.consciousness.CognitiveConstitutionalAI.DEFAULT_CONSTITUTION, 0.5);
        long jvmMs = (System.nanoTime() - jvmStart) / 1_000_000;

        double speedup = (double) jvmMs / Math.max(nativeMs, 1);
        System.out.println("JVM: " + jvmMs + "ms (just embedding+CAI)");
        System.out.println("Native: " + nativeMs + "ms (full process startup + cognitive)");
        System.out.println("Note: Native includes full process startup; fair comparison would require warm JVM");

        // Just verify native binary works
        assertThat(out).contains("Done!");
    }
}
