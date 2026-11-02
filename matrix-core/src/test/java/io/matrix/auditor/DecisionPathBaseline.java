package io.matrix.auditor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The accepted decision-path findings, and the comparison against them.
 *
 * <p>RECON-W43.0. One place that answers "is this finding already
 * accepted?", so that the report test and the gate test cannot drift
 * apart — they compared different things for one wave, and the one that
 * asserted nothing was the one that read as a check.
 *
 * <p><b>Why the key has no line number.</b> The first version of the
 * baseline keyed each accepted finding by its line number, and inserting
 * four hundred lines of unrelated code above a finding silently invalidated
 * its entry: the gate then reported the finding as new AND the entry as
 * stale, and the honest response to that is to re-read the file and
 * re-approve it — which is exactly the maintenance burden that makes a
 * baseline rot. The key is now the file and the symbol, and what is recorded
 * is how many findings of that symbol the file has. A code insertion does
 * not change it; adding or removing a finding does.
 */
final class DecisionPathBaseline {

    /** The explicit, owned and time-bounded baseline of accepted findings. */
    static final String BASELINE_FILE = "docs-v2/quality/decision-path-baseline.txt";

    private DecisionPathBaseline() {
    }

    /**
     * The accepted groups: a group key to how many findings of it are accepted.
     *
     * <p>The key is {@code <root>:<file>:<symbol>}.
     */
    static Map<String, Integer> acceptedCounts(Path baseline) throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        for (String line : Files.readAllLines(baseline, StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains(".java:")) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            int pipe = trimmed.indexOf('|');
            if (equals < 0 || pipe < 0) {
                continue;
            }
            String key = trimmed.substring(0, equals).strip();
            String count = trimmed.substring(equals + 1, pipe).strip();
            if (!count.matches("\\d+")) {
                continue;
            }
            counts.merge(key, Integer.parseInt(count), Integer::sum);
        }
        return counts;
    }

    /**
     * The accepted groups of ONE root.
     *
     * <p>The report test covers matrix-core alone and the gate covers every
     * root, so the comparison has to be able to ask about one root without
     * seeing the others' entries as stale.
     *
     * @param counts the accepted counts
     * @param root the root prefix, e.g. {@code matrix-core/src/main/java}
     * @return the counts of that root
     */
    static Map<String, Integer> forRoot(Map<String, Integer> counts, String root) {
        Map<String, Integer> filtered = new TreeMap<>();
        String prefix = root + ":";
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    /** The expiry of the baseline, or an empty string. */
    static String expiry(Path baseline) throws IOException {
        for (String line : Files.readAllLines(baseline, StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("expires=")) {
                return trimmed.substring("expires=".length()).strip();
            }
        }
        return "";
    }

    /** Whether a finding blocks. */
    static boolean blocks(DecisionPathAuditor.Violation v) {
        if (v.permitReason() != null) {
            return false;
        }
        return v.severity() == DecisionPathAuditor.Severity.VIOLATION
                || v.severity() == DecisionPathAuditor.Severity.UNCLASSIFIED;
    }

    /** The group key of a finding: the root, the file and the symbol. */
    static String key(String root, DecisionPathAuditor.Violation v) {
        return root + ":" + v.file() + ":" + v.symbol();
    }

    /** The blocking findings of one root, grouped by key. */
    static Map<String, Integer> actualCounts(String root, DecisionPathAuditor.AuditReport report) {
        Map<String, Integer> counts = new TreeMap<>();
        for (DecisionPathAuditor.Violation v : report.violations()) {
            if (blocks(v)) {
                counts.merge(key(root, v), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * The groups that do not agree with the baseline.
     *
     * <p>Three ways to disagree, and all three are failures: a group that
     * is not in the baseline (a NEW finding), a group whose count differs
     * (a finding was added or removed), and a group in the baseline that no
     * longer occurs (the baseline is stale and nobody updated it).
     *
     * @return the descriptions of the disagreements
     */
    static List<String> mismatches(Map<String, Integer> accepted, Map<String, Integer> actual) {
        List<String> out = new ArrayList<>();
        Map<String, Integer> merged = new LinkedHashMap<>();
        merged.putAll(accepted);
        merged.putAll(actual);
        for (Map.Entry<String, Integer> entry : merged.entrySet()) {
            String key = entry.getKey();
            int acceptedCount = accepted.getOrDefault(key, 0);
            int actualCount = actual.getOrDefault(key, 0);
            if (acceptedCount == actualCount) {
                continue;
            }
            if (acceptedCount == 0) {
                out.add("NEW: " + key + " (" + actualCount + ")");
            } else if (actualCount == 0) {
                out.add("STALE: " + key + " (the baseline accepts " + acceptedCount + ")");
            } else {
                out.add("CHANGED: " + key + " (the baseline accepts " + acceptedCount
                        + ", the source has " + actualCount + ")");
            }
        }
        return out;
    }

    /** Whether a date has passed. */
    static boolean expired(String expiry) {
        try {
            long at = java.time.LocalDate.parse(expiry)
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().getEpochSecond();
            return java.time.Instant.now().getEpochSecond() > at;
        } catch (RuntimeException unparseable) {
            return true;
        }
    }

    /** The distinct group keys of a set of counts. */
    static java.util.Set<String> keys(Map<String, Integer> counts) {
        return new TreeSet<>(counts.keySet());
    }
}
