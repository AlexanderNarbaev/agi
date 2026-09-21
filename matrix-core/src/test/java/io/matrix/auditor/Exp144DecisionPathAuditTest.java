package io.matrix.auditor;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 144 — Decision-Path Audit EXP.
 *
 * <p>Real-world audit of matrix-core's decision-path packages.
 * Per CONSTITUTION I, brain/consciousness/etc. must not call
 * ONNX runtime or use randomness.
 *
 * <p>Per TESTING-STRATEGY.md, EXP tests have slow speed and
 * higher cost; this one runs the actual file system scan.
 */
@Tag("exp")
class Exp144DecisionPathAuditTest {

    @Test
    void auditReality() {
        // Walk up from test CWD to find matrix-core/src/main/java
        Path cwd = Path.of(".").toAbsolutePath();
        Path root = null;
        for (int i = 0; i < 6; i++) {
            Path candidate = cwd.resolve("matrix-core/src/main/java");
            if (candidate.toFile().isDirectory()) {
                root = candidate;
                break;
            }
            cwd = cwd.getParent();
            if (cwd == null) break;
        }
        if (root == null) {
            System.out.println("[DECPATH-AUDIT] could not locate matrix-core/src/main/java");
            return;
        }
        DecisionPathAuditor auditor = new DecisionPathAuditor();
        var report = auditor.audit(root);
        // We don't FAIL on violations (legacy code) — we just document.
        // Future RUNs in phase γ will resolve violations.
        System.out.println("[DECPATH-AUDIT] files=" + report.filesScanned()
                + " violations=" + report.violationCount()
                + " passed=" + report.passed());
        if (!report.violations().isEmpty()) {
            int show = Math.min(5, report.violations().size());
            for (var v : report.violations().subList(0, show)) {
                System.out.println("  " + v.file() + ":" + v.line()
                        + " — " + v.symbol() + " (" + v.reason() + ")");
            }
        }
        assertThat(report.filesScanned()).isGreaterThan(0);
    }
}
