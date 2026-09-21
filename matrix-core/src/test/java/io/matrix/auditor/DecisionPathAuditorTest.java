package io.matrix.auditor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 142 — DecisionPathAuditor unit tests. */
class DecisionPathAuditorTest {

    private Path createTempTree() throws IOException {
        return Files.createTempDirectory("decpath-test");
    }

    private void writeFile(Path root, String relPath, String content) throws IOException {
        Path p = root.resolve(relPath);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @Test
    void emptyTreeReportsPassed() throws IOException {
        DecisionPathAuditor auditor = new DecisionPathAuditor();
        Path root = createTempTree();
        var report = auditor.audit(root);
        assertThat(report.filesScanned()).isZero();
        assertThat(report.violationCount()).isZero();
        assertThat(report.passed()).isTrue();
    }

    @Test
    void onnxImportInDecisionPathFlagsViolation() throws IOException {
        DecisionPathAuditor auditor = new DecisionPathAuditor();
        Path root = createTempTree();
        writeFile(root, "io/matrix/consciousness/MyClass.java",
                "package io.matrix.consciousness;\n" +
                "import com.microsoft.onnxruntime.OrtSession;\n" +
                "public class MyClass {}\n");
        var report = auditor.audit(root);
        assertThat(report.violationCount()).isGreaterThan(0);
        assertThat(report.passed()).isFalse();
    }

    @Test
    void onnxImportOutsideDecisionPathNotFlagged() throws IOException {
        DecisionPathAuditor auditor = new DecisionPathAuditor();
        Path root = createTempTree();
        writeFile(root, "io/matrix/api/MyClass.java",
                "package io.matrix.api;\n" +
                "import com.microsoft.onnxruntime.OrtSession;\n" +
                "public class MyClass {}\n");
        var report = auditor.audit(root);
        assertThat(report.passed()).isTrue();
    }

    @Test
    void mathRandomInDecisionPathFlagged() throws IOException {
        DecisionPathAuditor auditor = new DecisionPathAuditor();
        Path root = createTempTree();
        writeFile(root, "io/matrix/brain/MyClass.java",
                "package io.matrix.brain;\n" +
                "public class MyClass { void m() { double x = Math.random(); } }\n");
        var report = auditor.audit(root);
        assertThat(report.violationCount()).isGreaterThan(0);
    }

    @Test
    void cleanDecisionPathFilePasses() throws IOException {
        DecisionPathAuditor auditor = new DecisionPathAuditor();
        Path root = createTempTree();
        writeFile(root, "io/matrix/consciousness/MyClass.java",
                "package io.matrix.consciousness;\n" +
                "public class MyClass { void m() { int x = 1 + 2; } }\n");
        var report = auditor.audit(root);
        assertThat(report.passed()).isTrue();
    }

    @Test
    void formatReportIncludesSummary() throws IOException {
        DecisionPathAuditor auditor = new DecisionPathAuditor();
        Path root = createTempTree();
        writeFile(root, "io/matrix/consciousness/Bad.java",
                "import com.microsoft.onnxruntime.OrtSession;\n");
        var report = auditor.audit(root);
        String text = auditor.formatReport(report);
        assertThat(text).contains("Decision-Path Audit Report");
        assertThat(text).contains("Files scanned");
        assertThat(text).contains("Violations");
    }

    @Test
    void violationRecord() {
        DecisionPathAuditor.Violation v =
                new DecisionPathAuditor.Violation("file.java", 10, "sym", "reason");
        assertThat(v.file()).isEqualTo("file.java");
        assertThat(v.line()).isEqualTo(10);
        assertThat(v.symbol()).isEqualTo("sym");
        assertThat(v.reason()).isEqualTo("reason");
    }

    @Test
    void auditReportViolationCount() {
        DecisionPathAuditor.AuditReport r =
                new DecisionPathAuditor.AuditReport(5, List.of(), true);
        assertThat(r.filesScanned()).isEqualTo(5);
        assertThat(r.violationCount()).isZero();
        assertThat(r.passed()).isTrue();
    }
}
