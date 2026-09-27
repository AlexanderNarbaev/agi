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

    /** 11-field record: sourceId, datasetOrPattern, inputBits, samplesUsed,
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
    ) {}

    private final Path ledgerPath;

    public DistillationLedger(Path ledgerPath) {
        this.ledgerPath = ledgerPath;
    }

    public Path path() { return ledgerPath; }

    public synchronized void record(Entry e) throws java.io.IOException {
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
        if (!Files.exists(ledgerPath)) return out;
        for (String line : Files.readAllLines(ledgerPath)) {
            if (line.isBlank() || !line.startsWith("{")) continue;
            // Simple JSON parse (no nested objects).
            String srcId = extract(line, "sourceId");
            String pat  = extract(line, "datasetOrPattern");
            int inBits  = Integer.parseInt(extract(line, "inputBits"));
            int samples = Integer.parseInt(extract(line, "samplesUsed"));
            int hdcPromoted = Integer.parseInt(extract(line, "hdcPromoted"));
            int birClausesSynthesized = Integer.parseInt(extract(line, "birClausesSynthesized"));
            int tsetlinLiterals = Integer.parseInt(extract(line, "tsetlinLiterals"));
            double fid   = Double.parseDouble(extract(line, "fidelity"));
            long dur    = Long.parseLong(extract(line, "durationMs"));
            long ts     = Long.parseLong(extract(line, "timestampMs"));
            String hash  = extract(line, "artifactHash");
            out.add(new Entry(srcId, pat, inBits, samples, hdcPromoted,
                birClausesSynthesized, tsetlinLiterals, fid, dur, ts, hash));
        }
        return out;
    }

    private static String extract(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return "";
        int colon = json.indexOf(':', k);
        // Skip past opening quote (string value) OR start at digit (numeric value)
        int start = colon + 1;
        if (json.charAt(start) == ' ') start++;
        if (json.charAt(start) == '"') start++;
        // Find end: comma, closing brace, or end of input
        int end = json.length();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ',' || c == '}') { end = i; break; }
            if (c == '"' && json.charAt(i - 1) != '\\') { end = i; break; }
        }
        return json.substring(start, end);
    }
}
