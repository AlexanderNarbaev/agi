package io.matrix.brain.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RECON-W13 — Regression detector for EvalBattery CSV outputs.
 *
 * <p>Compares the current run CSV with the previous one and reports
 * category-level regressions (Δ > -0.05 = lost ground). Used to catch
 * unintended regressions before promotion.</p>
 */
public final class BenchmarkRegression {

    public record CategoryComparison(
        String category,
        int totalCurr, int passedCurr, double rateCurr,
        int totalPrev, int passedPrev, double ratePrev,
        double delta
    ) {}

    public record RegressionReport(
        List<CategoryComparison> byCategory,
        boolean hasRegression
    ) {}

    /** Compare two CSVs row-by-row by probe_id, then compare by-category rates. */
    public RegressionReport compare(Path current, Path previous) throws IOException {
        Map<String, int[]> curr = parseByCat(current);
        Map<String, int[]> prev = parseByCat(previous);
        var out = new java.util.ArrayList<CategoryComparison>();
        boolean regression = false;
        // Union of categories
        for (String cat : curr.keySet()) {
            int[] c = curr.get(cat);
            int[] p = prev.getOrDefault(cat, new int[]{0, 0});
            double rCurr = c[0] > 0 ? (double) c[1] / c[0] : 0.0;
            double rPrev = p[0] > 0 ? (double) p[1] / p[0] : 0.0;
            double delta = rCurr - rPrev;
            if (delta < -0.05) regression = true;
            out.add(new CategoryComparison(cat, c[0], c[1], rCurr, p[0], p[1], rPrev, delta));
        }
        return new RegressionReport(out, regression);
    }

    private Map<String, int[]> parseByCat(Path csv) throws IOException {
        Map<String, int[]> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(csv)) {
            if (line.startsWith("probe_id")) continue; // header
            String[] parts = parseRow(line);
            if (parts.length < 7) continue;
            String cat = parts[1];
            boolean passed = "true".equals(parts[6]);
            int[] arr = out.computeIfAbsent(cat, k -> new int[]{0, 0});
            arr[0]++;
            if (passed) arr[1]++;
        }
        return out;
    }

    private String[] parseRow(String csv) {
        // simple CSV parser — supports {""..."";""...""} JSON content blocks
        var parts = new java.util.ArrayList<String>();
        int i = 0, depth = 0;
        var sb = new StringBuilder();
        boolean inQuotes = false;
        while (i < csv.length()) {
            char c = csv.charAt(i);
            if (c == '"') {
                if (inQuotes && i+1 < csv.length() && csv.charAt(i+1) == '"') {
                    sb.append('"'); i += 2; continue;
                }
                inQuotes = !inQuotes;
                sb.append(c); i++;
            } else if (c == ',' && !inQuotes && depth == 0) {
                parts.add(sb.toString().trim().replaceAll("^\"|\"$", ""));
                sb.setLength(0); i++;
            } else {
                sb.append(c); i++;
            }
        }
        parts.add(sb.toString().trim().replaceAll("^\"|\"$", ""));
        return parts.toArray(new String[0]);
    }
}
