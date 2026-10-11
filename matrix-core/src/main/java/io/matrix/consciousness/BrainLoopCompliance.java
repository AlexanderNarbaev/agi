package io.matrix.consciousness;

import io.matrix.auditor.DecisionPathAuditor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CONSTITUTION compliance check for the cognitive loop's source tree.
 *
 * <h2>What changed, and why it matters more than it looks</h2>
 *
 * <p>The previous version computed {@code compliant = onnx == 0}. It also computed and
 * returned {@code randomCalls} and {@code wallClockCalls} — and printed both. A reader saw a
 * report containing 121 CONSTITUTION I violations, of which the gate silently enforced 2. The
 * counts were decoration: they made the output look thorough while narrowing the decision to
 * the one thing that happened to be zero after the LLM quarantine moved.</p>
 *
 * <p>It was also, in the other direction, too broad. {@link DecisionPathAuditor} treated every
 * {@code Random} as a violation, while Article I explicitly permits <em>seeded</em> Random in
 * the cognitive layer. So the gate simultaneously enforced too little and flagged the
 * constitution's own sanctioned mechanism.</p>
 *
 * <h2>What it does now</h2>
 *
 * <p>The gate enforces exactly what the auditor classifies as blocking: LLM/ONNX in a decision
 * path, and unseeded randomness outside adversarial mode. Wall-clock sites and seeded Random
 * are returned as advisory counts — still measured, still printed, still part of the record —
 * but they do not block, because deciding from a text scan whether a clock read reaches a
 * branch is not something this class can do honestly, and blocking on that guess would either
 * fail on legitimate audit timestamps or be waived until the rule means nothing.</p>
 *
 * <p>The blocking count is returned rather than hidden, so a caller can see what the decision
 * was made on.</p>
 */
public final class BrainLoopCompliance {

    /**
     * @param compliant     whether the tree has zero blocking findings
     * @param onnxImports   ONNX/LLM symbols found in decision-path (always blocking)
     * @param randomCalls   randomness sites found; blocking only when UNSEEDED
     * @param wallClockCalls clock sites found; advisory, pending human classification
     * @param blockingCount findings that actually caused {@code compliant} to be decided
     * @param advisoryCount findings recorded but not enforced
     * @param note          a human-readable account of the decision
     */
    public record ComplianceResult(boolean compliant, int onnxImports,
                                   int randomCalls, int wallClockCalls,
                                   int blockingCount, int advisoryCount,
                                   String note) { }

    /**
     * Audit a source tree for Article I compliance.
     *
     * @param sourceDir the root to scan
     * @return the compliance result; never null
     * @throws IOException if the directory cannot be inspected at all
     */
    public static ComplianceResult check(Path sourceDir) throws IOException {
        if (!Files.exists(sourceDir)) {
            return new ComplianceResult(false, 0, 0, 0, 0, 0, "no source");
        }
        DecisionPathAuditor auditor = new DecisionPathAuditor();
        var report = auditor.audit(sourceDir);

        int onnx = 0, random = 0, wallClock = 0;
        for (var v : report.violations()) {
            String s = v.symbol();
            if (s.contains("Onnx") || s.contains("onnx")) {
                onnx++;
            } else if (s.equals("wall-clock")) {
                wallClock++;
            } else {
                random++;
            }
        }

        long blocking = report.blockingCount();
        boolean compliant = blocking == 0;

        String note = compliant
                ? "no blocking findings; " + advisoryCount(report)
                  + " advisory sites recorded for review (seeded Random is permitted by "
                  + "Article I; wall-clock classification is not decidable from text)"
                : blocking + " blocking finding(s): " + firstBlocking(report);

        return new ComplianceResult(compliant, onnx, random, wallClock,
                (int) blocking, (int) report.advisoryCount(), note);
    }

    private static long advisoryCount(DecisionPathAuditor.AuditReport report) {
        return report.advisoryCount();
    }

    private static String firstBlocking(DecisionPathAuditor.AuditReport report) {
        return report.violations().stream()
                .filter(v -> v.severity() == DecisionPathAuditor.Severity.VIOLATION)
                .map(DecisionPathAuditor.Violation::toString)
                .findFirst()
                .orElse("(none)");
    }
}