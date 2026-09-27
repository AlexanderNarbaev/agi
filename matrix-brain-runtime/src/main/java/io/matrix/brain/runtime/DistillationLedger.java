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

    public record Entry(
        String sourceId,
        String datasetOrPattern,
        int inputBits,
        int samplesUsed,
        double fidelity,
        long durationMs,
        String artifactHash,
        boolean consolidatedToBirregistry,
        String timestamp
    ) {}

    private final Path ledgerPath;

    public DistillationLedger(Path ledgerPath) {
        this.ledgerPath = ledgerPath;
    }

    public Path path() { return ledgerPath; }

    public synchronized void record(Entry e) throws java.io.IOException {
        String json = String.format(
            "{\"sourceId\":\"%s\",\"datasetOrPattern\":\"%s\",\"inputBits\":%d,"
                + "\"samplesUsed\":%d,\"fidelity\":%.4f,\"durationMs\":%d,"
                + "\"artifactHash\":\"%s\",\"consolidatedToBirregistry\":%s,\"timestamp\":\"%s\"}%n",
            e.sourceId(), e.datasetOrPattern(), e.inputBits(), e.samplesUsed(),
            e.fidelity(), e.durationMs(), e.artifactHash(), e.consolidatedToBirregistry(),
            e.timestamp());
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
            double fid   = Double.parseDouble(extract(line, "fidelity"));
            long dur    = Long.parseLong(extract(line, "durationMs"));
            String hash  = extract(line, "artifactHash");
            boolean cons = Boolean.parseBoolean(extract(line, "consolidatedToBirregistry"));
            String ts    = extract(line, "timestamp");
            out.add(new Entry(srcId, pat, inBits, samples, fid, dur, hash, cons, ts));
        }
        return out;
    }

    private static String extract(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return "";
        int colon = json.indexOf(':', k);
        int start = json.indexOf('"', colon + 1);
        int end = json.indexOf('"', start + 1);
        return json.substring(start + 1, end);
    }
}
