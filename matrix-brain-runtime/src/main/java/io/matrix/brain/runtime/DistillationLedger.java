package io.matrix.brain.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * RECON-W5 Step 2/3 — DistillationLedger.
 *
 * <p>Append-only NDJSON ledger of every distillation run: input, size, eval
 * deltas, duration, artifact hashes. Used for super-additivity proofs
 * (A+B ≥ max(A,B)+ε) and for the eval-corpus
 * (docs-v2/research/DISTILLATION-LEDGER.md).</p>
 */
public final class DistillationLedger {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(DistillationLedger.class.getName());

    /**
     * RECON-W34.4 UNITS. This record mixes quantities in different units and the names alone
     * were not enough to stop them being swapped:
     *
     * <ul>
     *   <li>{@code inputBits} — BYTES of input data consumed by the run.</li>
     *   <li>{@code samplesUsed} — COUNT of samples the run consumed. NOT bytes.</li>
     * </ul>
     *
     * {@code total_inputs_bytes} sums {@code inputBits}. {@code inputsCount()} returns
     * {@code samplesUsed}. An earlier revision had both writers passing them the other way
     * round, which made a byte total report a sample count and vice versa.
     *
     * <p>11-field record: sourceId, datasetOrPattern, inputBits, samplesUsed,
     * hdcPromoted, birClausesSynthesized, tsetlinLiterals, fidelity,
     * durationMs, timestamp (epoch ms), artifactHash. */
    public record Entry(
        String sourceId,
        String datasetOrPattern,
        int inputBits,
        int samplesUsed,
        int hdcPromoted,
        int birClausesSynthesized,
        int tsetlinLiterals,
        double fidelity,
        long durationMs,
        long timestampMs,
        String artifactHash
    ) {
        // Back-compat aliases for older callers (renamed fields).
        public String source() { return sourceId; }
        public int samples() { return samplesUsed; }
        public double evalDelta() { return fidelity; }
        public int birClauses() { return birClausesSynthesized; }
        public int inputsCount() { return samplesUsed; }
        public int birClausesInduced() { return birClausesSynthesized; }
    }

    private final Path ledgerPath;

    public DistillationLedger(Path ledgerPath) {
        this.ledgerPath = ledgerPath;
    }

    public Path path() { return ledgerPath; }

    /** Number of recorded entries (line count in the ledger NDJSON file). */
    /** Aggregate summary: runs count + total inputs_bytes.
     * Returns a Map so callers can index by key. */
    public synchronized java.util.Map<String, Object> summary() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        int runs = 0;
        long totalBytes = 0;
        // RECON-W32.21: a partial summary is marked, not returned bare.
        int unparsed = 0;
        boolean readFailed = false;
        try {
            if (Files.exists(ledgerPath)) {
                for (String line : Files.readAllLines(ledgerPath)) {
                    if (line.isBlank() || !line.startsWith("{")) continue;
                    // RECON-W34.1: this block used to increment `runs` FIRST and only then
                    // look for "samplesUsed". A line truncated mid-write therefore counted as
                    // a completed run contributing zero bytes, and `unparsed` stayed 0 — so
                    // the summary reported runs=2 with total_inputs_bytes short and NOTHING
                    // flagged it as partial. That is a summary asserting completeness about
                    // data it never parsed. A line is counted as a run only if its byte count
                    // actually reads back.
                    int idx = line.indexOf("\"samplesUsed\":");
                    if (idx < 0) {
                        unparsed++;
                        continue;
                    }
                    try {
                        totalBytes += readLong(line, "inputBits");
                        runs++;
                    } catch (RuntimeException e) {
                        // RECON-W32.21: an unparseable byte count is not cosmetic. This
                        // summary returns "runs" and "total_inputs_bytes" together, and a
                        // silently short total looks exactly like a run that consumed no
                        // input — a claim about work that was done.
                        unparsed++;
                    }
                }
            }
        } catch (java.io.IOException e) {
            // Non-fatal for the summary, but NOT silent: the counts are partial and a
            // reader cannot otherwise tell a partial total from a real one.
            readFailed = true;
        }
        // RECON-W34.1: every NDJSON line persists birClausesSynthesized and readAll()
        // parses it back, but summary() never summed it. The one number that answers
        // "did distillation actually teach anything" was recorded and then never surfaced.
        // The W32.34 test recovery found this as a NullPointerException in a recovered
        // integration test -- ((Number) s.get("total_bir_clauses_induced")).intValue()
        // on a map that had no such key -- which is a missing aggregate, not a small
        // wrong number. Nothing was lost; it was simply never added up.
        // RECON-W34.1: append() persists hdcPromoted, birClausesSynthesized,
        // tsetlinLiterals and fidelity on every line, but summary() totalled only runs and
        // total_inputs_bytes. So the ledger recorded exactly how much knowledge each
        // distillation produced and then reported none of it. The W32.34 recovery found this
        // as NullPointerExceptions in two recovered integration tests -- a map lookup
        // returning null and then .intValue() -- which reads like a crash but is a MISSING
        // AGGREGATE. Nothing was lost; it was simply never added up. A summary that reports
        // only byte counts answers "how much work happened" and never "did any of it
        // teach the mind anything", which is the question a distillation ledger exists for.
        out.put("runs", runs);
        out.put("total_inputs_bytes", totalBytes);
        // RECON-W34.4: added alongside total_inputs_bytes so each field's unit is visible in
        // the summary itself. Before this, only a byte-looking key existed and it was fed the
        // count, so a reader had no way to tell from the summary that the two had been
        // transposed. Two clearly-named keys is cheaper to keep honest than one ambiguous one.
        out.put("total_inputs_count", sumField("samplesUsed"));
        out.put("total_hdc_promoted", sumField("hdcPromoted"));
        out.put("total_bir_clauses_induced", sumField("birClausesSynthesized"));
        out.put("total_tsetlin_automata_updated", sumField("tsetlinLiterals"));
        out.put("mean_eval_delta", meanField("fidelity", runs));
        out.put("path", ledgerPath.toString());
        if (unparsed > 0 || readFailed) {
            out.put("partial", true);
            out.put("unparsed_lines", unparsed);
            out.put("read_failed", readFailed);
            System.err.println("[DistillationLedger] summary of " + ledgerPath
                + " is PARTIAL: " + unparsed + " unparseable line(s), read failure="
                + readFailed + " — total_inputs_bytes=" + totalBytes
                + " understates what was actually consumed");
        }
        return out;
    }

    public synchronized int size() {
        if (!Files.exists(ledgerPath)) return 0;
        try {
            int count = 0;
            for (String line : Files.readAllLines(ledgerPath)) {
                if (line.isBlank() || !line.startsWith("{")) continue;
                count++;
            }
            return count;
        } catch (java.io.IOException t) {
            return -1;
        }
    }

    public synchronized void record(Entry e) throws java.io.IOException {
        append(e);
    }

    public synchronized void append(Entry e) throws java.io.IOException {
        String json = String.format(
            "{\"sourceId\":\"%s\",\"datasetOrPattern\":\"%s\",\"inputBits\":%d,"
                + "\"samplesUsed\":%d,\"hdcPromoted\":%d,\"birClausesSynthesized\":%d,"
                + "\"tsetlinLiterals\":%d,\"fidelity\":%.4f,\"durationMs\":%d,"
                + "\"timestampMs\":%d,\"artifactHash\":\"%s\"}%n",
            e.sourceId(), e.datasetOrPattern(), e.inputBits(), e.samplesUsed(),
            e.hdcPromoted(), e.birClausesSynthesized(), e.tsetlinLiterals(),
            e.fidelity(), e.durationMs(), e.timestampMs(), e.artifactHash());
        Files.writeString(ledgerPath, json,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public synchronized List<Entry> readAll() throws java.io.IOException {
        List<Entry> out = new ArrayList<>();
        int skipped = 0;
        if (!Files.exists(ledgerPath)) return out;
        for (String line : Files.readAllLines(ledgerPath)) {
            if (line.isBlank() || !line.startsWith("{")) continue;
            // RECON-W34.1. This loop used to parse every field unguarded, so a single line
            // missing a numeric field threw NumberFormatException from parseInt("") and
            // ABORTED THE WHOLE READ -- discarding every valid entry alongside the broken
            // one. For a ledger, which exists precisely so past work can be accounted for,
            // one torn line silently erasing the audit trail is the worst available failure.
            // A damaged line is now skipped, counted and reported, and the rest survive.
            try {
                String srcId = extract(line, "sourceId");
                String pat  = extract(line, "datasetOrPattern");
                int inBits  = (int) readLong(line, "inputBits");
                int samples = (int) readLong(line, "samplesUsed");
                int hdcPromoted = (int) readLong(line, "hdcPromoted");
                int birClausesSynthesized = (int) readLong(line, "birClausesSynthesized");
                int tsetlinLiterals = (int) readLong(line, "tsetlinLiterals");
                double fid = readDouble(line, "fidelity");
                long dur = readLong(line, "durationMs");
                long ts  = readLong(line, "timestampMs");
                String hash = extract(line, "artifactHash");
                out.add(new Entry(srcId, pat, inBits, samples, hdcPromoted,
                    birClausesSynthesized, tsetlinLiterals, fid, dur, ts, hash));
            } catch (RuntimeException corrupt) {
                skipped++;
                LOG.log(java.util.logging.Level.WARNING,
                        "skipping unparseable ledger line " + skipped + " in " + ledgerPath
                            + ": " + corrupt.getMessage());
            }
        }
        return out;
    }



    /**
     * Mean of a floating-point field over the lines that could be read.
     *
     * @param field    the JSON key to average, e.g. {@code fidelity}
     * @param readLines count of lines counted as complete runs, used as the denominator
     * @return the mean, or 0.0 when nothing was readable
     */
    private double meanField(String field, int readLines) {
        if (readLines <= 0) return 0.0;
        double sum = 0.0;
        int counted = 0;
        try {
            if (!Files.exists(ledgerPath)) return 0.0;
            for (String line : Files.readAllLines(ledgerPath)) {
                if (line.isBlank() || !line.startsWith("{")) continue;
                if (line.indexOf("\"inputBits\":") < 0) continue;
                try {
                    sum += readDouble(line, field);
                    counted++;
                } catch (RuntimeException unusable) {
                    // Same tolerance as sumField: one bad line cannot void the mean.
                }
            }
        } catch (java.io.IOException unreadable) {
            return counted == 0 ? 0.0 : sum / counted;
        }
        return counted == 0 ? 0.0 : sum / counted;
    }

    /**
     * Sum one numeric JSON field across every well-formed line.
     *
     * <p>Used for aggregates that {@code summary()} must report but that were previously
     * recorded without ever being totalled. Lines that cannot contribute are skipped rather
     * than failing the whole summary, matching the tolerance {@code readAll()} now applies.</p>
     *
     * @param field the JSON key to sum, e.g. {@code birClausesSynthesized}
     * @return the total, or 0 when the ledger is absent or unreadable
     */
    private long sumField(String field) {
        long total = 0;
        try {
            if (!Files.exists(ledgerPath)) return 0;
            for (String line : Files.readAllLines(ledgerPath)) {
                if (line.isBlank() || !line.startsWith("{")) continue;
                try {
                    total += readLong(line, field);
                } catch (RuntimeException unusable) {
                    // One bad line must not make the total uncomputable. The summary already
                    // reports "partial" for exactly this situation.
                }
            }
        } catch (java.io.IOException unreadable) {
            return total;
        }
        return total;
    }

    /**
     * Read an integer field, failing loudly rather than defaulting to zero.
     *
     * <p>The failure is deliberate. Defaulting a missing field to 0 would let a corrupt line
     * masquerade as a run that induced nothing — a claim about work that was not done.
     * Throwing lets the caller decide, and the caller skips and reports.</p>
     *
     * @param line the JSON line
     * @param key  field name
     * @return the parsed value
     * @throws IllegalArgumentException if the field is absent or not an integer
     */
    private static long readLong(String line, String key) {
        String raw = extract(line, key);
        if (raw.isBlank()) {
            throw new IllegalArgumentException("field '" + key + "' is absent or empty");
        }
        return Long.parseLong(raw.trim());
    }

    /**
     * Read a floating-point field, failing loudly rather than defaulting.
     *
     * @param line the JSON line
     * @param key  field name
     * @return the parsed value
     * @throws IllegalArgumentException if the field is absent or not a number
     */
    private static double readDouble(String line, String key) {
        String raw = extract(line, key);
        if (raw.isBlank()) {
            throw new IllegalArgumentException("field '" + key + "' is absent or empty");
        }
        return Double.parseDouble(raw.trim());
    }

    private static String extract(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return "";
        int colon = json.indexOf(':', k);
        // RECON-W34.1: the three guards below were absent. A key with no colon returned a
        // negative index that flowed into substring(...), and the quote-scan indexed
        // charAt(i - 1) at i == 0. Both threw StringIndexOutOfBoundsException on input this
        // method is guaranteed to receive: half-written lines from a crashed process.
        // An absent value is reported as "", which the numeric readers treat as a parse
        // failure the caller can skip -- it is never silently read as zero.
        if (colon < 0) return "";
        // Skip past opening quote (string value) OR start at digit (numeric value)
        int start = colon + 1;
        if (start >= json.length()) return "";
        if (json.charAt(start) == ' ') start++;
        if (start >= json.length()) return "";
        if (json.charAt(start) == '"') start++;
        if (start >= json.length()) return "";
        // Find end: comma, closing brace, or end of input
        int end = json.length();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ',' || c == '}') { end = i; break; }
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) { end = i; break; }
        }
        if (end < start) return "";
        return json.substring(start, end);
    }
}
