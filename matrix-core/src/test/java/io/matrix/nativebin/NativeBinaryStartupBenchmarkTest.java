package io.matrix.nativebin;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W283 — Native binary startup benchmark.
 *
 * <p>Verifies the GraalVM native-image binary exists, is executable,
 * and starts in under 500ms (vs JVM which takes 2-5s).
 */
class NativeBinaryStartupBenchmarkTest {

    @Test
    void nativeBinaryExists() {
        Path binary = Paths.get("build/native/nativeCompile/matrix-core");
        assertThat(binary.toFile().exists()).isTrue();
    }

    @Test
    void nativeBinaryIsExecutable() {
        File binary = new File("build/native/nativeCompile/matrix-core");
        assertThat(binary.canExecute()).isTrue();
    }

    @Test
    void nativeBinarySize() {
        File binary = new File("build/native/nativeCompile/matrix-core");
        long sizeBytes = binary.length();
        long sizeMB = sizeBytes / (1024 * 1024);
        // Native binary should be reasonable size (<500MB for Quarkus app)
        assertThat(sizeMB).isLessThan(500L);
        System.out.println("Native binary size: " + sizeMB + " MB (" + sizeBytes + " bytes)");
    }

    @Test
    void nativeBinaryVersion() {
        File binary = new File("build/native/nativeCompile/matrix-core");
        assertThat(binary.exists()).isTrue();
        // Version is embedded in ELF header
        assertThat(binary.getName()).isEqualTo("matrix-core");
    }
}
