package io.matrix.brain.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * MIND-W5 — Distillation ledger (NDJSON).
 *
 * <p>Records every distillation run: inputs (file sizes, ONNX op counts),
 * output (number of HDC entries promoted, BIR clauses induced, Tsetlin
 * automata updated), eval deltas, and artifact hashes. The ledger is
 * append-only NDJSON, mirror of the {@link EpisodicLog} design.</p>
 *
 * <p>This ledger is the empirical proof (CONSTITUTION Article VI) that
 * distillation actually improves the mind on a fixed eval set.</p>
 */
public final class DistillationLedger {

    /** One row per distillation run. */
    public record Entry(
        String id,                  // FNV-1a of inputs+sources+ts
        String source,               // e.g. "onnx:mobilenet-v3.onnx" or "dataset:boolq"
        int inputsCount,             // number of input units (tokens/embeddings/images)
        long inputsBytes,            // raw input size in bytes
        int hdcPromoted,             // HDC vectors promoted
        int birClausesInduced,       // BIR boolean clauses induced
        int tsetlinAutomataUpdated,  // Tsetlin automata updated
        double evalDelta,            // before->after on fixed eval set
        long durationMs,
        long tsMillis,
        String artifactHash          // SHA-256 of the artifact bundle
    ) {
        public String toJson() {
            StringBuilder sb = new StringBuilder(256);
            sb.append("{\"id\":\"").append(escape(id))
              .append("\",\"source\":\"").append(escape(source))
              .append("\",\"inputs_count\":").append(inputsCount)
              .append(",\"inputs_bytes\":").append(inputsBytes)
              .append(",\"hdc_promoted\":").append(hdcPromoted)
              .append(",\"bir_clauses_induced\":").append(birClausesInduced)
              .append(",\"tsetlin_automata_updated\":").append(tsetlinAutomataUpdated)
              .append(",\"eval_delta\":").append(evalDelta)
              .append(",\"duration_ms\":").append(durationMs)
              .append(",\"ts\":").append(tsMillis)
              .append(",\"artifact_hash\":\"").append(escape(artifactHash))
              .append("\"}");
            return sb.toString();
        }
        private static String escape(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder(s.length() + 4);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' || c == '\\') sb.append('\\').append(c);
                else if (c == '\n') sb.append("\\n");
                else if (c == '\r') sb.append("\\r");
                else sb.append(c);
            }
            return sb.toString();
        }
    }

    private final Path ledgerPath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private long appendCount = 0;

    public DistillationLedger(Path ledgerPath) {
        this.ledgerPath = ledgerPath;
    }

    /** Append one distillation entry. Atomic write per row. */
    public void append(Entry e) {
        if (e == null) return;
        lock.writeLock().lock();
        try {
            Files.createDirectories(ledgerPath.getParent() == null
                ? Path.of(".") : ledgerPath.getParent());
            String line = e.toJson() + "\n";
            Files.writeString(ledgerPath, line,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
            appendCount++;
        } catch (IOException ex) {
            throw new RuntimeException("DistillationLedger append failed: " + ledgerPath, ex);
        } finally {
            lock.writeLock().unlock(); }
    }

    public List<Entry> readAll() {
        lock.readLock().lock();
        try {
            if (!Files.exists(ledgerPath)) return List.of();
            List<String> lines = Files.readAllLines(ledgerPath);
            List<Entry> out = new ArrayList<>();
            for (String l : lines) {
                if (l.isBlank()) continue;
                Entry e = parse(l);
                if (e != null) out.add(e);
            }
            return out;
        } catch (IOException ex) {
            throw new RuntimeException("DistillationLedger read failed: " + ledgerPath, ex);
        } finally {
            lock.readLock().unlock(); }
    }

    public int size() {
        lock.readLock().lock();
        try { return (int) appendCount; }
        finally { lock.readLock().unlock(); }
    }

    /** Aggregate stats across the ledger. */
    public Map<String, Object> summary() {
        List<Entry> all = readAll();
        Map<String, Object> m = new LinkedHashMap<>();
        int totalHdc = 0, totalBir = 0, totalTsetlin = 0;
        long totalBytes = 0;
        double sumDelta = 0.0;
        for (Entry e : all) {
            totalHdc += e.hdcPromoted();
            totalBir += e.birClausesInduced();
            totalTsetlin += e.tsetlinAutomataUpdated();
            totalBytes += e.inputsBytes();
            sumDelta += e.evalDelta();
        }
        m.put("runs", all.size());
        m.put("total_inputs_bytes", totalBytes);
        m.put("total_hdc_promoted", totalHdc);
        m.put("total_bir_clauses_induced", totalBir);
        m.put("total_tsetlin_automata_updated", totalTsetlin);
        m.put("mean_eval_delta", all.isEmpty() ? 0.0 : sumDelta / all.size());
        return m;
    }

    /** Minimal NDJSON parser sufficient for our schema. Returns null on malformed. */
    private static Entry parse(String line) {
        if (line == null || line.length() < 64) return null;
        if (!line.startsWith("{")) return null;
        // Reject lines that don't contain any of our schema keys (malformed JSON)
        if (!line.contains("\"id\":") || !line.contains("\"ts\":") || !line.contains("\"source\":")) {
            return null;
        }
        try {
            String id = jsonStr(line, "id");
            String source = jsonStr(line, "source");
            // Malformed lines produce all-empty fields — reject those too.
            if (id.isEmpty() && source.isEmpty()) return null;
            int inputsCount = (int) jsonNum(line, "inputs_count");
            long inputsBytes = (long) jsonNum(line, "inputs_bytes");
            int hdcPromoted = (int) jsonNum(line, "hdc_promoted");
            int birClauses = (int) jsonNum(line, "bir_clauses_induced");
            int tsetlinAutomata = (int) jsonNum(line, "tsetlin_automata_updated");
            double evalDelta = jsonNum(line, "eval_delta");
            long durationMs = (long) jsonNum(line, "duration_ms");
            long ts = (long) jsonNum(line, "ts");
            String artifactHash = jsonStr(line, "artifact_hash");
            return new Entry(id, source, inputsCount, inputsBytes, hdcPromoted,
                birClauses, tsetlinAutomata, evalDelta, durationMs, ts, artifactHash);
        } catch (RuntimeException ex) {
            return null;
        }
    }
    private static String jsonStr(String line, String key) {
        String marker = "\"" + key + "\":\"";
        int i = line.indexOf(marker);
        if (i < 0) return "";
        int s = i + marker.length();
        int e = s;
        while (e < line.length()) {
            char c = line.charAt(e);
            if (c == '\\' && e + 1 < line.length()) { e += 2; continue; }
            if (c == '"') break;
            e++;
        }
        return line.substring(s, e);
    }
    private static double jsonNum(String line, String key) {
        String marker = "\"" + key + "\":";
        int i = line.indexOf(marker);
        if (i < 0) return 0.0;
        int s = i + marker.length();
        int e = s;
        while (e < line.length()) {
            char c = line.charAt(e);
            if (c == ',' || c == '}') break;
            e++;
        }
        try { return Double.parseDouble(line.substring(s, e).trim()); }
        catch (NumberFormatException ex) { return 0.0; }
    }
}
