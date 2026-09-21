package io.matrix.auditor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 224 — AuditCheckRunner unit tests. */
class AuditCheckRunnerTest {

    @Test
    void cleanDirExitsZero(@TempDir Path tmp) throws Exception {
        // Create a clean Java file
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("A.java"),
                "package x;\npublic class A {}\n");
        Path report = tmp.resolve("report.json");
        var result = AuditCheckRunner.run(src, report);
        assertThat(result.exitCode()).isZero();
        assertThat(Files.exists(report)).isTrue();
    }

    @Test
    void nullReportNoFile(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("A.java"),
                "package x;\npublic class A {}\n");
        var result = AuditCheckRunner.run(src, null);
        assertThat(result.exitCode()).isZero();
    }

    @Test
    void missingDir(@TempDir Path tmp) throws Exception {
        int code = AuditCheckRunner.runOn(tmp.resolve("nonexistent"));
        // Per current impl, missing dir = FAIL = exit 1
        assertThat(code).isEqualTo(1);
    }
}
