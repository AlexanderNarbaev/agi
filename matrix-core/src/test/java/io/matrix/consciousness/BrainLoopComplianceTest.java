package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 227 — BrainLoopCompliance unit tests. */
class BrainLoopComplianceTest {

    @Test
    void emptyDirReturnsNoSource(@TempDir Path tmp) throws Exception {
        var r = BrainLoopCompliance.check(tmp.resolve("missing"));
        assertThat(r.compliant()).isFalse();
        assertThat(r.note()).isEqualTo("no source");
    }

    @Test
    void cleanDirCompliant(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("A.java"),
                "package x;\npublic class A {}\n");
        var r = BrainLoopCompliance.check(src);
        assertThat(r.compliant()).isTrue();
        assertThat(r.onnxImports()).isZero();
    }

    @Test
    void onnxImportFailsCompliance(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("src");
        Path packageDir = src.resolve("io/matrix/consciousness");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("A.java"),
                "package io.matrix.consciousness;\n" +
                "import com.microsoft.onnxruntime.OrtSession;\n" +
                "public class A { }\n");
        var r = BrainLoopCompliance.check(src);
        assertThat(r.compliant()).isFalse();
        assertThat(r.onnxImports()).isGreaterThan(0);
    }

    @Test
    void complianceRecord() {
var r = new BrainLoopCompliance.ComplianceResult(
                  true, 0, 0, 0, 0, 0, 0, "all checks pass");
          assertThat(r.compliant()).isTrue();
          assertThat(r.onnxImports()).isZero();
          // W35.2: the gate now decides on blockingCount, so the record must expose the two
          // counts it decided on rather than only the raw tallies it prints.
          assertThat(r.blockingCount()).isZero();
          // W36.1: unclassified wall-clock now BLOCKS, so the record must expose the count the
          // decision was actually made on, not only the raw tallies it prints.
          assertThat(r.unclassified()).isZero();
          assertThat(r.permittedCount()).isZero();
          assertThat(r.note()).isEqualTo("all checks pass");
    }
}
