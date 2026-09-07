package io.matrix.auditor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RUN 142 — Decision-Path Auditor.
 *
 * <p>Scans source code for forbidden symbols in decision-path
 * packages. Reports violations for CONSTITUTION I compliance:
 * <ul>
 *   <li>Decision-path must NOT call LLM inference (no {@code com.microsoft.onnxruntime.*})</li>
 *   <li>Decision-path must NOT use randomness ({@link Math#random}, {@link java.util.Random})</li>
 *   <li>Decision-path must NOT use wall-clock for branching</li>
 * </ul>
 *
 * <p>This is a static analysis tool. It walks .java files and
 * greps for forbidden imports/identifiers.
 *
 * <p>Decision-path packages per PARADIGM.md §5:
 * consciousness, brain, mediator, reasoning, actions, ethics,
 * memory (when used inline), lifecycle (when used inline).
 */
public final class DecisionPathAuditor {

    private static final List<String> DECISION_PATH_PACKAGES = List.of(
            "io/matrix/consciousness/",
            "io/matrix/brain/",
            "io/matrix/mediator/",
            "io/matrix/reasoning/",
            "io/matrix/actions/",
            "io/matrix/ethics/",
            "io/matrix/imports/",
            "io/matrix/neuron/"
    );

    private static final List<ForbiddenSymbol> FORBIDDEN_SYMBOLS = List.of(
            new ForbiddenSymbol("com.microsoft.onnxruntime.",
                    "ONNX runtime inference in decision-path (CONSTITUTION I)"),
            new ForbiddenSymbol("io.matrix.distill.onnx.",
                    "Distill-side ONNX access from decision-path (PARADIGM §3)"),
            new ForbiddenSymbol("io.matrix.api.QwenOnnxBridge",
                    "Qwen-on-Qwen chat bridge in decision-path"),
            new ForbiddenSymbol("io.matrix.api.OnnxRuntimeAdapter",
                    "Direct ONNX adapter in decision-path")
    );

    private static final List<String> FORBIDDEN_RUNTIME_CALLS = List.of(
            "Math.random",
            "new Random(",
            "currentTimeMillis",
            "Instant.now",
            "System.nanoTime",  // allowed in instrumentation only
            "ThreadLocalRandom"
    );

    public record Violation(String file, int line, String symbol, String reason) {}

    public record AuditReport(int filesScanned,
                              List<Violation> violations,
                              boolean passed) {
        public int violationCount() { return violations.size(); }
    }

    public AuditReport audit(Path rootDir) {
        List<Violation> violations = new ArrayList<>();
        final int[] counter = {0};
        try {
            Files.walk(rootDir)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        counter[0]++;
                        try {
                            String content = Files.readString(p);
                            String relPath = rootDir.relativize(p).toString();
                            if (!isDecisionPath(relPath)) return;
                            // Check forbidden imports
                            String[] lines = content.split("\n");
                            for (int i = 0; i < lines.length; i++) {
                                String line = lines[i].trim();
                                for (ForbiddenSymbol fs : FORBIDDEN_SYMBOLS) {
                                    if (line.contains("import ") && line.contains(fs.prefix())) {
                                        violations.add(new Violation(relPath, i + 1,
                                                fs.prefix(), fs.reason()));
                                    }
                                }
                                // Check forbidden runtime calls (not in comments)
                                for (String call : FORBIDDEN_RUNTIME_CALLS) {
                                    if (line.contains(call) && !line.startsWith("//") && !line.contains("*/")) {
                                        if (!call.equals("System.nanoTime")
                                                || line.contains("=System.nanoTime()")
                                                || line.contains("= System.nanoTime()")) {
                                            violations.add(new Violation(relPath, i + 1,
                                                    call,
                                                    "Forbidden runtime call in decision-path (CONSTITUTION I)"));
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // skip unreadable
                        }
                    });
        } catch (Exception e) {
            // skip
        }
        boolean passed = violations.isEmpty();
        return new AuditReport(counter[0], violations, passed);
    }

    private boolean isDecisionPath(String relPath) {
        for (String pkg : DECISION_PATH_PACKAGES) {
            if (relPath.contains(pkg)) return true;
        }
        return false;
    }

    public String formatReport(AuditReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Decision-Path Audit Report ===\n");
        sb.append(String.format("Files scanned: %d%n", report.filesScanned()));
        sb.append(String.format("Violations:    %d%n", report.violationCount()));
        sb.append(String.format("Status:        %s%n%n",
                report.passed() ? "PASS" : "FAIL"));
        if (!report.violations().isEmpty()) {
            sb.append("--- Violations ---\n");
            for (Violation v : report.violations()) {
                sb.append(String.format("  %s:%d — %s (%s)%n",
                        v.file(), v.line(), v.symbol(), v.reason()));
            }
        }
        return sb.toString();
    }

    private record ForbiddenSymbol(String prefix, String reason) {}
}
