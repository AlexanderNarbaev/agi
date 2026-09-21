package io.matrix.auditor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RUN 187 — AuditCheck (periodic decision-path self-audit).
 *
 * <p>Runs DecisionPathAuditor on the project's main source
 * directory and produces a summary. Can be invoked from any
 * CI pipeline or pre-deployment hook.
 */
public final class AuditCheck {

    public enum Severity { PASS, WARN, FAIL }

    public record AuditSummary(Severity severity,
                               int filesScanned,
                               int violations,
                               int onnxImports,
                               int randomCalls,
                               int wallClockCalls) {}

    public static AuditSummary runOn(Path sourceDir) throws IOException {
        if (!Files.exists(sourceDir)) {
            return new AuditSummary(Severity.FAIL, 0, 0, 0, 0, 0);
        }
        DecisionPathAuditor auditor = new DecisionPathAuditor();
        var report = auditor.audit(sourceDir);
        int onnx = 0, random = 0, wallClock = 0;
        for (var v : report.violations()) {
            String s = v.symbol();
            if (s.contains("Onnx") || s.contains("onnx")) onnx++;
            else if (s.equals("Math.random") || s.contains("Random")) random++;
            else if (s.contains("currentTimeMillis") || s.contains("Instant.now")) wallClock++;
        }
        Severity sev;
        if (report.passed()) sev = Severity.PASS;
        else if (onnx > 0) sev = Severity.FAIL;
        else if (report.violationCount() > 50) sev = Severity.FAIL;
        else sev = Severity.WARN;
        return new AuditSummary(sev, report.filesScanned(),
                report.violationCount(), onnx, random, wallClock);
    }

    public static String formatSummary(AuditSummary s) {
        return String.format(
                "AuditCheck{ severity=%s, files=%d, violations=%d, onnx=%d, random=%d, wallClock=%d }",
                s.severity(), s.filesScanned(), s.violations(),
                s.onnxImports(), s.randomCalls(), s.wallClockCalls());
    }
}
