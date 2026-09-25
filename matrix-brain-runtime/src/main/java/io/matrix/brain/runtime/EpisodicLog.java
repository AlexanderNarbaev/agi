package io.matrix.brain.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * MIND-W3 — Episodic log (append-only NDJSON).
 *
 * <p>Records every MindCycle interaction as a single NDJSON line so the
 * consolidation cycle can replay the day's experience during sleep.
 * Backed by a file on disk; supports safe append + full read.</p>
 *
 * <p>CONSTITUTION Article III — timestamps are ISO-8601 UTC strings for
 * reproducibility across machines; entries are immutable.</p>
 */
public final class EpisodicLog {

    /** One episodic entry: a mind interaction. */
    public record Entry(
        String id,            // FNV-1a of input+response+ts
        String input,         // the user input
        String reply,         // the MindCycle reply
        double confidence,   // 0..1
        boolean accepted,     // false if modulators vetoed
        List<String> modulatorsFired,
        long tsMillis
    ) {
        public String toJson() {
            StringBuilder sb = new StringBuilder(64 + input.length() + reply.length());
            sb.append("{\"id\":\"").append(escape(id))
              .append("\",\"input\":\"").append(escape(input))
              .append("\",\"reply\":\"").append(escape(reply))
              .append("\",\"confidence\":").append(confidence)
              .append(",\"accepted\":").append(accepted)
              .append(",\"modulators\":[");
            boolean first = true;
            for (String m : modulatorsFired) {
                if (!first) sb.append(',');
                sb.append('"').append(escape(m)).append('"');
                first = false;
            }
            sb.append("],\"ts\":").append(tsMillis)
              .append("}");
            return sb.toString();
        }
        static Entry fromJson(String line) {
            // Lightweight parser sufficient for our schema (no nested objects, no escapes).
            String id = extractString(line, "id");
            String input = extractString(line, "input");
            String reply = extractString(line, "reply");
            double conf = extractNumber(line, "confidence");
            boolean acc = extractBool(line, "accepted");
            long ts = (long) extractNumber(line, "ts");
            List<String> mods = extractStringArray(line, "modulators");
            return new Entry(id, input, reply, conf, acc, mods, ts);
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
        private static String extractString(String line, String key) {
            String marker = "\"" + key + "\":\"";
            int i = line.indexOf(marker);
            if (i < 0) return "";
            int s = i + marker.length();
            // Read until un-escaped closing quote.
            StringBuilder sb = new StringBuilder();
            while (s < line.length()) {
                char c = line.charAt(s);
                if (c == '\\' && s + 1 < line.length()) {
                    char n = line.charAt(s + 1);
                    if (n == 'n') sb.append('\n');
                    else if (n == 'r') sb.append('\r');
                    else sb.append(n);
                    s += 2;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                    s++;
                }
            }
            return sb.toString();
        }
        private static double extractNumber(String line, String key) {
            String marker = "\"" + key + "\":";
            int i = line.indexOf(marker);
            if (i < 0) return 0.0;
            int s = i + marker.length();
            int e = s;
            while (e < line.length()) {
                char c = line.charAt(e);
                if (c == ',' || c == '}' || c == ' ') break;
                e++;
            }
            try { return Double.parseDouble(line.substring(s, e)); }
            catch (NumberFormatException ex) { return 0.0; }
        }
        private static boolean extractBool(String line, String key) {
            String marker = "\"" + key + "\":";
            int i = line.indexOf(marker);
            if (i < 0) return false;
            return line.indexOf("true", i + marker.length()) >= 0
                && line.indexOf("true", i + marker.length()) < i + marker.length() + 6;
        }
        @SuppressWarnings("unchecked")
        private static List<String> extractStringArray(String line, String key) {
            String marker = "\"" + key + "\":[";
            int i = line.indexOf(marker);
            if (i < 0) return List.of();
            int s = i + marker.length();
            int e = line.indexOf(']', s);
            String body = line.substring(s, e);
            List<String> out = new ArrayList<>();
            if (body.isBlank()) return out;
            // Split on commas not inside quotes — sufficient for our schema.
            int p = 0;
            boolean inQ = false;
            StringBuilder cur = new StringBuilder();
            while (p < body.length()) {
                char c = body.charAt(p);
                if (c == '"') {
                    inQ = !inQ;
                } else if (c == ',' && !inQ) {
                    String tok = cur.toString().trim();
                    if (tok.length() >= 2 && tok.startsWith("\"") && tok.endsWith("\"")) {
                        out.add(tok.substring(1, tok.length() - 1));
                    }
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
                p++;
            }
            String tok = cur.toString().trim();
            if (tok.length() >= 2 && tok.startsWith("\"") && tok.endsWith("\"")) {
                out.add(tok.substring(1, tok.length() - 1));
            }
            return out;
        }
    }

    private final Path logPath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private long appendCount = 0;

    public EpisodicLog(Path logPath) {
        this.logPath = logPath;
    }

    /** Append one entry. Persists atomically (single line write). */
    public void append(Entry e) {
        if (e == null) return;
        lock.writeLock().lock();
        try {
            Files.createDirectories(logPath.getParent() == null
                ? Path.of(".") : logPath.getParent());
            String line = e.toJson() + "\n";
            Files.writeString(logPath, line,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
            appendCount++;
        } catch (IOException ex) {
            throw new RuntimeException("EpisodicLog append failed: " + logPath, ex);
        } finally {
            lock.writeLock().unlock(); }
    }

    /** Convenience: build an Entry with auto-id and current timestamp, then append. */
    public void append(String input, String reply, double confidence, boolean accepted,
                      List<String> modulators) {
        long ts = System.currentTimeMillis();
        String id = "ep-" + ts + "-" + Long.toHexString(fnv1a64(input + "|" + reply));
        append(new Entry(id, input, reply, confidence, accepted, modulators, ts));
    }

    /** Read all entries. */
    public List<Entry> readAll() {
        lock.readLock().lock();
        try {
            if (!Files.exists(logPath)) return List.of();
            List<String> lines = Files.readAllLines(logPath);
            List<Entry> out = new ArrayList<>();
            for (String l : lines) {
                if (l.isBlank()) continue;
                try { out.add(Entry.fromJson(l)); }
                catch (RuntimeException ignored) { /* skip malformed */ }
            }
            return out;
        } catch (IOException ex) {
            throw new RuntimeException("EpisodicLog read failed: " + logPath, ex);
        } finally {
            lock.readLock().unlock(); }
    }

    public int size() {
        lock.readLock().lock();
        try { return (int) appendCount; }
        finally { lock.readLock().unlock(); }
    }

    public Path path() {
        return logPath;
    }

    private static long fnv1a64(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }
}
