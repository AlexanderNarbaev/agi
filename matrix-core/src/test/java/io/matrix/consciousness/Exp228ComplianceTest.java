package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 228 — BrainLoopCompliance EXP.
 *
 * <p>Real compliance check on matrix-core. Verifies production
 * source has 0 ONNX in decision-path.
 */
@Tag("exp")
class Exp228ComplianceTest {

    @Test
    void realProjectCompliance() throws Exception {
        // Find matrix-core/src/main/java
        Path cwd = Path.of(".").toAbsolutePath();
        Path target = null;
        for (int i = 0; i < 6; i++) {
            Path candidate = cwd.resolve("matrix-core/src/main/java");
            if (candidate.toFile().isDirectory()) {
                target = candidate;
                break;
            }
            cwd = cwd.getParent();
            if (cwd == null) break;
        }
        if (target == null) {
            // W48-06: a source-integrity check that cannot find the source must FAIL.
            // The previous version printed a line and returned, and the test passed —
            // a check that verifies nothing while reporting success.
            throw new IllegalStateException(
                    "[COMPLIANCE-EXP] the source tree could not be located from "
                            + Path.of(".").toAbsolutePath()
                            + ": the compliance check cannot verify what it cannot find");
        }
        var result = BrainLoopCompliance.check(target);
        System.out.printf("[COMPLIANCE-EXP] %s, onnx=%d, random=%d, wallClock=%d, note=%s%n",
                result.compliant() ? "COMPLIANT" : "FAIL",
                result.onnxImports(),
                result.randomCalls(),
                result.wallClockCalls(),
                result.note());
        assertThat(result.compliant()).isTrue();
    }
}
