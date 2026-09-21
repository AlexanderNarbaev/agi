package io.matrix.quality.reporter;

import io.matrix.quality.gates.Gate;
import io.matrix.quality.gates.GateOrchestrator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * WAVE T-10 — Automated Compliance Reporter.
 *
 * Produces a complete compliance report from Gate Orchestrator results:
 * - Gate-by-gate PASS/FAIL with evidence
 * - Aggregate quality score
 * - Report hash for tamper-evidence
 * - Recommendations
 */
public final class ComplianceReporter {

    public enum Format { MARKDOWN, JSON, TEXT }

    public record Report(
        String generatedAt,
        String gateCountSummary,
        int qualityScore,
        boolean allPassed,
        List<Gate.GateResult> results,
        List<String> recommendations,
        String reportHash
    ) {}

    public Report generate(GateOrchestrator.Report result) {
        List<String> recommendations = generateRecommendations(result);
        String hash = computeHash(result);
        String summary = result.passed() + "/" + result.total() + " passed";
        return new Report(
            Instant.now().toString(),
            summary,
            result.qualityScore(),
            result.allPass(),
            result.results(),
            recommendations,
            hash
        );
    }

    public String render(Report report, Format format) {
        return switch (format) {
            case MARKDOWN -> renderMarkdown(report);
            case JSON -> renderJson(report);
            case TEXT -> renderText(report);
        };
    }

    private String renderMarkdown(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# MATRIX Quality Report\n\n");
        sb.append("- **Generated:** ").append(report.generatedAt()).append("\n");
        sb.append("- **Gates:** ").append(report.gateCountSummary()).append("\n");
        sb.append("- **Score:** ").append(report.qualityScore()).append("/100\n");
        sb.append("- **All Passed:** ").append(report.allPassed() ? "✅" : "❌").append("\n");
        sb.append("- **Report Hash:** `").append(report.reportHash()).append("`\n\n");
        sb.append("## Gate Results\n\n");
        sb.append("| Gate | Status | Summary |\n");
        sb.append("|------|--------|---------|\n");
        for (Gate.GateResult r : report.results()) {
            String icon = switch (r.status()) {
                case PASS -> "✅";
                case WARN -> "⚠️";
                case FAIL -> "❌";
            };
            sb.append("| ").append(r.gateName()).append(" | ").append(icon)
              .append(" ").append(r.status()).append(" | ").append(r.summary()).append(" |\n");
        }
        if (!report.recommendations().isEmpty()) {
            sb.append("\n## Recommendations\n\n");
            for (String rec : report.recommendations()) {
                sb.append("- ").append(rec).append("\n");
            }
        }
        return sb.toString();
    }

    private String renderJson(Report report) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsString(report);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String renderText(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("MATRIX QUALITY REPORT\n");
        sb.append("======================\n");
        sb.append("Generated: ").append(report.generatedAt()).append("\n");
        sb.append("Score: ").append(report.qualityScore()).append("/100\n");
        sb.append("All passed: ").append(report.allPassed()).append("\n\n");
        for (Gate.GateResult r : report.results()) {
            sb.append("[").append(r.status()).append("] ")
              .append(r.gateName()).append(": ").append(r.summary()).append("\n");
        }
        return sb.toString();
    }

    private List<String> generateRecommendations(GateOrchestrator.Report result) {
        List<String> recs = new ArrayList<>();
        for (Gate.GateResult r : result.results()) {
            if (r.status() == Gate.GateResult.Status.FAIL) {
                recs.add("Fix " + r.gateName() + ": " + r.summary());
            }
        }
        return recs;
    }

    private String computeHash(GateOrchestrator.Report result) {
        StringBuilder sb = new StringBuilder();
        for (Gate.GateResult r : result.results()) {
            sb.append(r.gateName()).append(":").append(r.status()).append("|");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "unavailable";
        }
    }
}
