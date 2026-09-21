package io.matrix.benchmark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collects JMH benchmark results, generates performance reports, and tracks
 * performance over time with regression detection.
 *
 * <p>Usage:
 * <pre>
 *   // Parse JMH JSON results
 *   PerformanceReport report = PerformanceReport.parse(jmhJsonPath);
 *
 *   // Generate markdown report
 *   String markdown = report.toMarkdown();
 *
 *   // Check for regressions against baseline
 *   boolean hasRegressions = report.detectRegressions(baselineReport, 0.10);
 * </pre>
 */
public final class PerformanceReport {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Instant timestamp;
    private final Map<String, BenchmarkResult> results;

    private PerformanceReport(Instant timestamp, Map<String, BenchmarkResult> results) {
        this.timestamp = timestamp;
        this.results = Map.copyOf(results);
    }

    /**
     * Creates an empty report with current timestamp.
     */
    public static PerformanceReport create() {
        return new PerformanceReport(Instant.now(), Map.of());
    }

    /**
     * Adds a benchmark result to the report.
     */
    public PerformanceReport withResult(String name, double score, double error, String unit) {
        var newResults = new LinkedHashMap<>(results);
        newResults.put(name, new BenchmarkResult(name, score, error, unit));
        return new PerformanceReport(timestamp, newResults);
    }

    /**
     * Parses JMH JSON output file into a PerformanceReport.
     *
     * <p>Expects the standard JMH JSON format produced by {@code -rf json -rff results.json}.
     */
    public static PerformanceReport parse(Path jsonPath) throws IOException {
        String json = Files.readString(jsonPath);
        return parseJson(json);
    }

    /**
     * Parses JMH JSON string into a PerformanceReport.
     *
     * <p>Simple JSON parser — avoids external dependency.
     */
    static PerformanceReport parseJson(String json) {
        var results = new LinkedHashMap<String, BenchmarkResult>();
        // Simple extraction: find "benchmark", "score", "scoreError", "scoreUnit" fields
        String[] lines = json.split("\n");
        String currentBenchmark = null;
        double score = 0;
        double error = 0;
        String unit = "";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("\"benchmark\":")) {
                currentBenchmark = trimmed.substring(trimmed.indexOf(':') + 1)
                        .trim().replace("\"", "").replace(",", "");
                // Extract short name (last component)
                int lastDot = currentBenchmark.lastIndexOf('.');
                if (lastDot >= 0) {
                    currentBenchmark = currentBenchmark.substring(lastDot + 1);
                }
            } else if (trimmed.startsWith("\"score\":")) {
                score = Double.parseDouble(trimmed.substring(trimmed.indexOf(':') + 1)
                        .trim().replace(",", ""));
            } else if (trimmed.startsWith("\"scoreError\":")) {
                error = Double.parseDouble(trimmed.substring(trimmed.indexOf(':') + 1)
                        .trim().replace(",", ""));
            } else if (trimmed.startsWith("\"scoreUnit\":")) {
                unit = trimmed.substring(trimmed.indexOf(':') + 1)
                        .trim().replace("\"", "").replace(",", "");
            } else if (trimmed.equals("},") || trimmed.equals("}")) {
                if (currentBenchmark != null) {
                    results.put(currentBenchmark,
                            new BenchmarkResult(currentBenchmark, score, error, unit));
                    currentBenchmark = null;
                }
            }
        }
        return new PerformanceReport(Instant.now(), results);
    }

    /**
     * Detects performance regressions compared to a baseline report.
     *
     * @param baseline       baseline performance report
     * @param thresholdPct   regression threshold (0.10 = 10% slower is a regression)
     * @return list of regression descriptions (empty if no regressions)
     */
    public List<String> detectRegressions(PerformanceReport baseline, double thresholdPct) {
        var regressions = new ArrayList<String>();
        for (var entry : results.entrySet()) {
            String name = entry.getKey();
            BenchmarkResult current = entry.getValue();
            BenchmarkResult base = baseline.results.get(name);
            if (base == null) {
                continue; // new benchmark, no baseline
            }
            // For throughput (higher is better), check if current is significantly lower
            // For average time (lower is better), check if current is significantly higher
            boolean isThroughput = current.unit().contains("ops");
            double ratio = current.score() / base.score();
            if (isThroughput) {
                if (ratio < (1.0 - thresholdPct)) {
                    regressions.add(String.format(
                            "REGRESSION %s: %.2f -> %.2f %s (%.1f%% slower)",
                            name, base.score(), current.score(), current.unit(),
                            (1.0 - ratio) * 100));
                }
            } else {
                if (ratio > (1.0 + thresholdPct)) {
                    regressions.add(String.format(
                            "REGRESSION %s: %.2f -> %.2f %s (%.1f%% slower)",
                            name, base.score(), current.score(), current.unit(),
                            (ratio - 1.0) * 100));
                }
            }
        }
        return regressions;
    }

    /**
     * Generates a markdown performance report.
     */
    public String toMarkdown() {
        var sb = new StringBuilder();
        sb.append("# MATRIX Performance Report\n\n");
        sb.append("**Timestamp:** ").append(TIMESTAMP_FMT.format(timestamp)).append("\n\n");
        sb.append("## Benchmark Results\n\n");
        sb.append("| Benchmark | Score | Error | Unit |\n");
        sb.append("|-----------|-------|-------|------|\n");
        for (var entry : results.entrySet()) {
            BenchmarkResult r = entry.getValue();
            sb.append(String.format("| %s | %.2f | ±%.2f | %s |\n",
                    r.name(), r.score(), r.error(), r.unit()));
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Generates a plain text performance report.
     */
    public String toPlainText() {
        var sb = new StringBuilder();
        sb.append("MATRIX Performance Report\n");
        sb.append("Timestamp: ").append(TIMESTAMP_FMT.format(timestamp)).append("\n\n");
        for (var entry : results.entrySet()) {
            BenchmarkResult r = entry.getValue();
            sb.append(String.format("  %-40s %10.2f ± %8.2f  %s\n",
                    r.name(), r.score(), r.error(), r.unit()));
        }
        return sb.toString();
    }

    /**
     * Returns all benchmark results.
     */
    public Map<String, BenchmarkResult> results() {
        return results;
    }

    /**
     * Returns the report timestamp.
     */
    public Instant timestamp() {
        return timestamp;
    }

    /**
     * A single benchmark result.
     */
    public record BenchmarkResult(String name, double score, double error, String unit) {}
}
