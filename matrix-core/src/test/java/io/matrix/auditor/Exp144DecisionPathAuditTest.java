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
    void auditReality() throws java.io.IOException {
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
        // RECON-W43.0: this used to PRINT the findings and assert only that
        // the scan had looked at more than zero files — a check that could
        // not fail, and one that was mistaken for a check. It now asserts
        // the result. matrix-core's decision path has NO blocking finding,
        // so this assertion holds today and goes red the moment someone
        // puts a wall-clock read or an unseeded Random into a decision
        // package.
        //
        // The cross-module gate, with its explicit baseline of the accepted
        // findings in matrix-brain-runtime, is DecisionPathGateTest.
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

        // RECON-W43.0: the assertion is against the ACCEPTED baseline, not
        // against zero — matrix-core's decision path does have accepted
        // findings, and what must hold is that there is no finding the
        // baseline does not know about. The comparison lives in one place
        // (DecisionPathBaseline) so that this test and the gate test cannot
        // drift apart.
        Path repo = repoRoot();
        Path baseline = repo.resolve(DecisionPathBaseline.BASELINE_FILE);
        assertThat(baseline).as("the accepted baseline of decision-path findings").exists();
        var accepted = DecisionPathBaseline.forRoot(
                DecisionPathBaseline.acceptedCounts(baseline), "matrix-core/src/main/java");
        var actual = DecisionPathBaseline.actualCounts("matrix-core/src/main/java", report);
        var mismatches = DecisionPathBaseline.mismatches(accepted, actual);
        mismatches.forEach(m -> System.out.println("[DECPATH-AUDIT] " + m));
        assertThat(mismatches)
                .as("decision-path findings in matrix-core that the accepted baseline does not "
                        + "list, or lists with a different count")
                .isEmpty();
    }

    /** The repository root, found by walking up from the test's directory. */
    private static Path repoRoot() {
        Path candidate = Path.of(".").toAbsolutePath();
        for (int i = 0; i < 6 && candidate != null; i++) {
            if (java.nio.file.Files.isDirectory(candidate.resolve("formal"))
                    && java.nio.file.Files.isDirectory(candidate.resolve("matrix-core"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("cannot locate the repository root");
    }
}
