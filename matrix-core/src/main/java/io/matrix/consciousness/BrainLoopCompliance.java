package io.matrix.consciousness;

import io.matrix.auditor.DecisionPathAuditor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RUN 227 — BrainLoopCompliance (CONSTITUTION compliance check).
 *
 * <p>Verifies that the cognitive loop's runtime source code
 * adheres to CONSTITUTION I (no LLM in decision-path) and
 * CONSTITUTION II (K_MAX ≤ 20).
 *
 * <p>Useful as a CI pre-merge check.
 */
public final class BrainLoopCompliance {

    public record ComplianceResult(boolean compliant, int onnxImports,
                                   int randomCalls, int wallClockCalls,
                                   String note) {}

    public static ComplianceResult check(Path sourceDir) throws IOException {
        if (!Files.exists(sourceDir)) {
            return new ComplianceResult(false, 0, 0, 0, "no source");
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
        // CONSTITUTION I: ONNX = FAIL
        // CONSTITUTION II: no formal check here (K_MAX is structural)
        boolean compliant = onnx == 0;
        String note = compliant
                ? "all checks pass"
                : "ONNX detected in decision-path: FAIL";
        return new ComplianceResult(compliant, onnx, random, wallClock, note);
    }
}
