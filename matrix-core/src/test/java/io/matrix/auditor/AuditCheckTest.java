package io.matrix.auditor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 187 — AuditCheck unit tests. */
class AuditCheckTest {

    @Test
    void emptyDirFails() throws Exception {
        Path tmp = Files.createTempDirectory("audit-empty");
        var s = AuditCheck.runOn(tmp.resolve("nonexistent"));
        // Non-existent dir — currently we return FAIL/0
        assertThat(s.filesScanned()).isZero();
    }

    @Test
    void cleanDirPasses() throws Exception {
        Path tmp = Files.createTempDirectory("audit-clean");
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("ClassA.java"),
                "package x;\npublic class ClassA {}\n");
        Files.writeString(src.resolve("ClassB.java"),
                "package x;\npublic class ClassB {}\n");
        var s = AuditCheck.runOn(src);
        assertThat(s.severity()).isEqualTo(AuditCheck.Severity.PASS);
        assertThat(s.filesScanned()).isEqualTo(2);
    }

    @Test
    void severityEnum() {
        assertThat(AuditCheck.Severity.values()).hasSize(3);
        assertThat(AuditCheck.Severity.valueOf("PASS")).isNotNull();
        assertThat(AuditCheck.Severity.valueOf("WARN")).isNotNull();
        assertThat(AuditCheck.Severity.valueOf("FAIL")).isNotNull();
    }

    @Test
    void formatSummary() {
        var s = new AuditCheck.AuditSummary(
                AuditCheck.Severity.PASS, 100, 0, 0, 0, 0);
        String text = AuditCheck.formatSummary(s);
        assertThat(text).contains("PASS");
        assertThat(text).contains("files=100");
        assertThat(text).contains("violations=0");
    }
}
