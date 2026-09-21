package io.matrix.auditor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RUN 224 — AuditCheckRunner (CI-friendly runner).
 *
 * <p>Runs DecisionPathAuditor on a target directory and writes
 * a JSON report. Designed to be invoked from CI pre-merge hook.
 *
 * <p>Returns exit code 0 for PASS/WARN, 1 for FAIL.
 */
public final class AuditCheckRunner {

    public record RunResult(int exitCode, String summary,
                            AuditCheck.AuditSummary audit) {}

    public static RunResult run(Path sourceDir, Path reportOut) throws IOException {
        AuditCheck.AuditSummary s = AuditCheck.runOn(sourceDir);
        String json = "{" +
                "\"severity\":\"" + s.severity() + "\"," +
                "\"files\":" + s.filesScanned() + "," +
                "\"violations\":" + s.violations() + "," +
                "\"onnx\":" + s.onnxImports() + "," +
                "\"random\":" + s.randomCalls() + "," +
                "\"wallClock\":" + s.wallClockCalls() +
                "}";
        if (reportOut != null) {
            Files.createDirectories(reportOut.getParent());
            Files.writeString(reportOut, json);
        }
        int exit = switch (s.severity()) {
            case PASS -> 0;
            case WARN -> 0;
            case FAIL -> 1;
        };
        return new RunResult(exit, json, s);
    }

    public static int runOn(Path sourceDir) throws IOException {
        return run(sourceDir, null).exitCode();
    }
}
