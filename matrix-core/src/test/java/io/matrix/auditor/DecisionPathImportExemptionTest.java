package io.matrix.auditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * R-EPIS-10 — the Article I exemption is MEMBER-granular, and an import is not an annotation.
 *
 * <p><b>The defect this pins.</b> The decision-path auditor armed its
 * {@code @ArticleIPermitted} exemption when it found the <b>text</b> {@code ArticleIPermitted} on
 * any line of a file — the {@code import} statement included. The exemption was therefore armed
 * at brace depth 0, before the type was opened, and it covered the <b>whole class body</b>: a
 * class that merely imported the annotation sat outside the gate's reach whatever it contained,
 * and a synthetic, completely unannotated wall-clock read in an unrelated method of such a class
 * was classified PERMITTED and did not block.</p>
 *
 * <p><b>What these tests assert.</b> Three things, all of them assertions rather than prints —
 * the difference the wave's own probe family got wrong (R-EPIS-11):</p>
 * <ul>
 *   <li>the IMPORT alone exempts nothing: a planted, unannotated wall-clock read in a class that
 *       imports the annotation is found and BLOCKS;</li>
 *   <li>the ANNOTATION still exempts exactly its own member: the same read inside an annotated
 *       member is adjudicated and does not block;</li>
 *   <li>the fix is what makes the difference: with the import rule removed the plant is caught,
 *       and the assertion above is what fails if the rule comes back.</li>
 * </ul>
 */
@DisplayName("R-EPIS-10 — the Article I exemption is member-granular")
class DecisionPathImportExemptionTest {

    /**
     * A class that imports the annotation and reads the wall clock in a method that does NOT
     * carry the annotation.
     */
    private static final String IMPORTING_CLASS_WITHOUT_THE_ANNOTATION =
            "package io.matrix.consciousness;\n" +
            "import io.matrix.auditor.ArticleIPermitted;\n" +
            "import java.time.Instant;\n" +
            "public class Planted {\n" +
            "    public String stamp() {\n" +
            "        return Instant.now().toString();\n" +
            "    }\n" +
            "}\n";

    /** The same class, with the read inside an annotated member. */
    private static final String IMPORTING_CLASS_WITH_THE_ANNOTATION =
            "package io.matrix.consciousness;\n" +
            "import io.matrix.auditor.ArticleIPermitted;\n" +
            "import java.time.Instant;\n" +
            "public class Planted {\n" +
            "    @ArticleIPermitted(\"the stamp decides nothing\")\n" +
            "    public String stamp() {\n" +
            "        return Instant.now().toString();\n" +
            "    }\n" +
            "}\n";

    @Test
    @DisplayName("a class that merely IMPORTS the annotation is inside the gate's reach")
    void anImportDoesNotExemptTheClassBody() throws IOException {
        Path root = Files.createTempDirectory("decpath-import-exempt");
        Path file = root.resolve("io/matrix/consciousness/Planted.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, IMPORTING_CLASS_WITHOUT_THE_ANNOTATION);

        DecisionPathAuditor.AuditReport report = new DecisionPathAuditor().audit(root);

        assertThat(report.violations())
                .as("the planted wall-clock read must be FOUND: the import line carries the "
                        + "annotation's name, and the name is not an annotation")
                .isNotEmpty();
        assertThat(report.violations())
                .as("the planted read must BLOCK: it carries no permit reason, because nothing "
                        + "adjudicated it")
                .anySatisfy(violation -> assertThat(DecisionPathBaseline.blocks(violation))
                        .as("the planted read at line %s is blocking", violation.line())
                        .isTrue());
    }

    @Test
    @DisplayName("the annotation still exempts exactly its own member")
    void theAnnotationStillExemptsItsOwnMember() throws IOException {
        Path root = Files.createTempDirectory("decpath-annotated-member");
        Path file = root.resolve("io/matrix/consciousness/Planted.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, IMPORTING_CLASS_WITH_THE_ANNOTATION);

        DecisionPathAuditor.AuditReport report = new DecisionPathAuditor().audit(root);

        assertThat(report.violations())
                .as("the adjudicated read is still RECORDED, so the report accounts for it")
                .isNotEmpty();
        assertThat(report.violations())
                .as("the adjudicated read does not block")
                .noneSatisfy(violation -> assertThat(DecisionPathBaseline.blocks(violation))
                        .isTrue());
        assertThat(report.violations())
                .as("the read is recorded as PERMITTED, with the reason the annotation gave")
                .anySatisfy(violation -> assertThat(violation.permitReason())
                        .isNotNull()
                        .contains("ArticleIPermitted"));
    }
}
