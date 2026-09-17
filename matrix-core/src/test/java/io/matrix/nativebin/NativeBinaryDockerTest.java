package io.matrix.nativebin;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * W292 — Native binary Docker test.
 *
 * <p>Verifies Dockerfile.native-slim exists and references the
 * native binary.
 */
class NativeBinaryDockerTest {

    @Test
    void dockerfileExists() {
        Path dockerfile = Paths.get("Dockerfile.native-slim");
        assertThat(dockerfile.toFile().exists()).isTrue();
    }

    @Test
    void dockerfileReferencesBinary() throws Exception {
        String content = new String(java.nio.file.Files.readAllBytes(
            Paths.get("Dockerfile.native-slim")));
        assertThat(content).contains("matrix-core");
        assertThat(content).contains("nativeCompile");
    }

    @Test
    void nativeBinaryIsBuildable() {
        // Verify the source binary exists (the Docker build depends on it)
        Path binary = Paths.get("build/native/nativeCompile/matrix-core");
        assertThat(binary.toFile().exists()).isTrue();
        assertThat(binary.toFile().canExecute()).isTrue();
    }
}
