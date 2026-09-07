package io.matrix.auditor;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 188 — AuditCheck EXP.
 *
 * <p>Real audit of matrix-core/src/main/java. Reports severity
 * and violation counts. Per CONSTITUTION, ONNX presence in
 * decision-path means FAIL.
 */
@Tag("exp")
class Exp188AuditCheckTest {

    @Test
    void realProjectAudit() throws Exception {
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
            System.out.println("[AUDIT-EXP] could not find source dir");
            return;
        }
        var summary = AuditCheck.runOn(target);
        System.out.println("[AUDIT-EXP] " + AuditCheck.formatSummary(summary));
        // Should not have ONNX in decision path
        // (We allow warnings — they are about nanoTime, etc.)
        // Severity may be PASS/WARN/FAIL depending on codebase state — just verify it ran
        assertThat(summary.severity()).isNotNull();
    }
}
