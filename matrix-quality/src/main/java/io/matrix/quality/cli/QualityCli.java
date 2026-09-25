package io.matrix.quality.cli;

import io.matrix.quality.gates.Gate;
import io.matrix.quality.gates.GateOrchestrator;
import io.matrix.quality.reporter.ComplianceReporter;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * WAVE T-10 — Quality CLI.
 *
 * Command-line interface to run the 14 Goal Guard gates:
 * <pre>{@code
 *   ./gradlew :matrix-quality:run --args="run /path/to/project"
 *   ./gradlew :matrix-quality:run --args="report /path/to/project md"
 * }</pre>
 */
public final class QualityCli {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println(usage());
            System.exit(1);
        }
        String command = args[0];
        try {
            switch (command) {
                case "run" -> runGates(Paths.get(args.length > 1 ? args[1] : "."));
                case "report" -> {
                    Path root = Paths.get(args.length > 1 ? args[1] : ".");
                    String fmt = args.length > 2 ? args[2] : "md";
                    generateReport(root, fmt);
                }
                case "list" -> listGates();
                default -> {
                    System.out.println("Unknown command: " + command);
                    System.out.println(usage());
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runGates(Path root) {
        GateOrchestrator orch = new GateOrchestrator(root);
        GateOrchestrator.Report result = orch.runAll();
        System.out.println("Gate results:");
        for (Gate.GateResult r : result.results()) {
            String icon = r.status() == Gate.GateResult.Status.PASS ? "✅"
                : r.status() == Gate.GateResult.Status.WARN ? "⚠️" : "❌";
            System.out.println("  " + icon + " " + r.gateName() + ": " + r.summary());
        }
        System.out.println();
        System.out.println("Score: " + result.qualityScore() + "/100");
        System.out.println("Result: " + (result.allPass() ? "ALL PASS" : "FAILURES PRESENT"));
        System.exit(result.allPass() ? 0 : 1);
    }

    private static void generateReport(Path root, String fmt) {
        GateOrchestrator orch = new GateOrchestrator(root);
        GateOrchestrator.Report result = orch.runAll();
        ComplianceReporter reporter = new ComplianceReporter();
        ComplianceReporter.Report report = reporter.generate(result);
        ComplianceReporter.Format format = switch (fmt.toLowerCase()) {
            case "json" -> ComplianceReporter.Format.JSON;
            case "text", "txt" -> ComplianceReporter.Format.TEXT;
            default -> ComplianceReporter.Format.MARKDOWN;
        };
        System.out.println(reporter.render(report, format));
    }

    private static void listGates() {
        System.out.println("The 14 Goal Guard gates:");
        String[] names = {
            "goal-prompt-auditor", "goal-reviewer", "goal-diff-reviewer", "goal-verifier",
            "goal-test-reviewer", "goal-data-reviewer", "goal-ops-reviewer", "goal-perf-reviewer",
            "goal-ux-reviewer", "goal-doc-reviewer", "goal-api-reviewer", "goal-quality-gate",
            "goal-security-reviewer", "goal-final-auditor"
        };
        for (int i = 0; i < names.length; i++) {
            System.out.println("  " + (i + 1) + ". " + names[i]);
        }
    }

    private static String usage() {
        return """
            MATRIX Quality CLI (Wave T-10)
            ==============================

            Usage:
              quality run [PROJECT_ROOT]
                  Run all 14 gates and print results to stdout

              quality report [PROJECT_ROOT] [FORMAT]
                  Generate compliance report (FORMAT: md|json|text, default md)

              quality list
                  List all 14 gates
            """;
    }
}
