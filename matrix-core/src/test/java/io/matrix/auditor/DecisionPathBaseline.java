package io.matrix.auditor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The accepted decision-path findings, and the comparison against them.
 *
 * <p>RECON-W43.0. One place that answers "is this finding already
 * accepted?", so that the report test and the gate test cannot drift
 * apart — they compared different things for one wave, and the one that
 * asserted nothing was the one that read as a check.
 */
final class DecisionPathBaseline {

    /** The explicit, owned and time-bounded baseline of accepted findings. */
    static final String BASELINE_FILE = "docs-v2/quality/decision-path-baseline.txt";

    /** One accepted finding, with the reason it is accepted. */
    record Entry(String key, String reason) { }

    private DecisionPathBaseline() {
    }

    /** The accepted entries. */
    static List<Entry> entries(Path baseline) throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (String line : Files.readAllLines(baseline, StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains(".java:")) {
                continue;
            }
            int pipe = trimmed.indexOf('|');
            String key = pipe < 0 ? trimmed : trimmed.substring(0, pipe).strip();
            String reason = pipe < 0 ? "" : trimmed.substring(pipe + 1).strip();
            entries.add(new Entry(key, reason));
        }
        return entries;
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

    /** The key of a finding, as the baseline spells it. */
    static String key(String root, DecisionPathAuditor.Violation v) {
        return root + ":" + v.file() + ":" + v.line() + " " + v.symbol();
    }

    /**
     * The blocking findings of one root that the baseline does not accept.
     *
     * @param root the source root, as the baseline spells it
     * @param report the audit report of that root
     * @param accepted the accepted keys
     * @return the findings that are not accepted
     */
    static Set<String> newFindings(String root, DecisionPathAuditor.AuditReport report,
            Set<String> accepted) {
        Set<String> found = new TreeSet<>();
        for (DecisionPathAuditor.Violation v : report.violations()) {
            if (blocks(v)) {
                found.add(key(root, v));
            }
        }
        found.removeAll(accepted);
        return found;
    }

    /** The accepted keys of the baseline. */
    static Set<String> acceptedKeys(Path baseline) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (Entry entry : entries(baseline)) {
            keys.add(entry.key());
        }
        return keys;
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
}
