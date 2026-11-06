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

    /**
     * RECON-W38.4 — the ONNX runtime API lives in {@code ai.onnxruntime}; the
     * {@code com.microsoft.onnxruntime} package holds the generated protobuf
     * classes. Before this rule existed, a decision-path file importing the
     * runtime API - the import that actually performs inference - was NOT
     * flagged, because only the protobuf prefix was listed.
     */
    @Test
    void onnxRuntimeApiImportInDecisionPathFlagsViolation() throws IOException {
        DecisionPathAuditor auditor = new DecisionPathAuditor();
        Path root = createTempTree();
        writeFile(root, "io/matrix/consciousness/MyClass.java",
                "package io.matrix.consciousness;\n" +
                "import ai.onnxruntime.OrtSession;\n" +
                "public class MyClass {}\n");
        var report = auditor.audit(root);
        assertThat(report.violationCount()).isGreaterThan(0);
        assertThat(report.passed()).isFalse();
        assertThat(report.violations())
                .anySatisfy(v -> assertThat(v.symbol()).isEqualTo("ai.onnxruntime."));
    }

    /**
     * RECON-W38.4 — the distill-side rule named the package
     * "io.matrix.distill.onnx.", which does not exist in the tree, so it could
     * never fire. The class that actually performs ONNX inference for
     * distillation is io.matrix.distill.OnnxActivationTeacher.
     */
    @Test
    void distillOnnxTeacherImportInDecisionPathFlagsViolation() throws IOException {
        DecisionPathAuditor auditor = new DecisionPathAuditor();
        Path root = createTempTree();
        writeFile(root, "io/matrix/consciousness/MyClass.java",
                "package io.matrix.consciousness;\n" +
                "import io.matrix.distill.OnnxActivationTeacher;\n" +
                "public class MyClass {}\n");
        var report = auditor.audit(root);
        assertThat(report.violationCount()).isGreaterThan(0);
        assertThat(report.passed()).isFalse();
        assertThat(report.violations())
                .anySatisfy(v -> assertThat(v.symbol())
                        .isEqualTo("io.matrix.distill.OnnxActivationTeacher"));
    }

    @Test
    void onnxRuntimeApiImportOutsideDecisionPathNotFlagged() throws IOException {
        DecisionPathAuditor auditor = new DecisionPathAuditor();
        Path root = createTempTree();
        writeFile(root, "io/matrix/rag/MyClass.java",
                "package io.matrix.rag;\n" +
                "import ai.onnxruntime.OrtSession;\n" +
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

    /**
     * W36.1 — an UNADJUDICATED wall-clock read must now BLOCK.
     *
     * <p>This is the property the wave turned on. It is also the one most likely to silently
     * regress: a gate that no longer blocks is indistinguishable from a gate that never
     * blocked, unless something is deliberately broken.</p>
     */
    @Test
    void unclassifiedWallClockBlocks() throws IOException {
        Path root = createTempTree();
        writeFile(root, "io/matrix/brain/Decider.java",
                "package io.matrix.brain;\n"
                        + "public class Decider {\n"
                        + "  public boolean decide(long t) {\n"
                        + "    if (System.currentTimeMillis() - t > 1000) { return false; }\n"
                        + "    return true;\n"
                        + "  }\n"
                        + "}\n");
        // W36.1 NOTE — the auditor classifies by the path RELATIVE to the root it is given, so
        // these files are written at io/matrix/brain/... INSIDE the temp root and the audit is
        // run on that root. Auditing the package directory itself would relativise every file
        // to a bare name, match no decision-path package, and silently scan NOTHING — a gate
        // that skips everything reports zero findings and looks clean.
        var report = new DecisionPathAuditor().audit(root);
        assertThat(report.filesScanned())
                .as("the file must actually be scanned, not skipped")
                .isGreaterThan(0);
        assertThat(report.blockingCount())
                .as("a confirmed breach still blocks")
                .isZero();
        assertThat(report.unclassifiedCount())
                .as("wall-clock nobody has judged is NOT a lesser violation, it is an unknown "
                        + "one, and it must block until annotated or given an injected clock")
                .isEqualTo(1);
        assertThat(report.nonCompliantCount()).isEqualTo(1);
        assertThat(report.passed())
                .as("the report must not claim PASS while a site is unadjudicated")
                .isFalse();
    }

    /**
     * W36.1 — annotating a member clears it, and the justification is retained.
     */
    @Test
    void articleIPermittedAnnotationClearsASite() throws IOException {
        Path root = createTempTree();
        writeFile(root, "io/matrix/brain/Recorder.java",
                "package io.matrix.brain;\n"
                        + "import io.matrix.auditor.ArticleIPermitted;\n"
                        + "public class Recorder {\n"
                        + "  @ArticleIPermitted(value = \"audit field, no decision reads it\")\n"
                        + "  public long stamp() { return System.currentTimeMillis(); }\n"
                        + "}\n");
        var report = new DecisionPathAuditor().audit(root);
        assertThat(report.unclassifiedCount())
                .as("an annotated member must be adjudicated, not merely annotated")
                .isZero();
        assertThat(report.permittedCount()).isEqualTo(1);
        assertThat(report.passed()).isTrue();
        assertThat(report.violations().get(0).permitReason())
                .as("the adjudication must be ATTRIBUTED, not merely dropped. The report "
                        + "cannot quote the annotation's own text because string literals are "
                        + "stripped before scanning — correctly, since a string is not code — so "
                        + "the reason is carried in the SOURCE annotation, where a reviewer "
                        + "reads it, and the report says the site was annotated.")
                .contains("ArticleIPermitted");
        assertThat(Files.readString(root.resolve("io/matrix/brain/Recorder.java")))
                .as("the human-readable justification stays in the source")
                .contains("audit field, no decision reads it");
    }

    @Test
    void violationRecord() {
        DecisionPathAuditor.Violation v =
new DecisionPathAuditor.Violation("file.java", 10, "sym", "reason",
                          DecisionPathAuditor.Severity.VIOLATION, null);
          assertThat(v.file()).isEqualTo("file.java");
          assertThat(v.line()).isEqualTo(10);
          assertThat(v.symbol()).isEqualTo("sym");
          assertThat(v.reason()).isEqualTo("reason");
          // W35.2: the compliance gate decides on severity, so a Violation that could not
          // state whether it blocks would leave the gate undecidable.
          assertThat(v.severity()).isEqualTo(DecisionPathAuditor.Severity.VIOLATION);
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
