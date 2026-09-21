package io.matrix.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RUN 163 — PersistentMemory (JSONL backend).
 *
 * <p>A simple deterministic JSON-lines persistence layer for
 * M2 memory. Used in Phase γ EXP. Each line is a JSON object
 * with {@code key}, {@code payload} (base64), and {@code accessCount}.
 *
 * <p>Not a full implementation — just enough to back the
 * consolidation EXP. Production would use SQLite/Avro.
 */
public final class PersistentMemory {

    private final Path file;
    private final List<ConsolidationCycle.MemoryEntry> entries = new ArrayList<>();

    public PersistentMemory(Path file) {
        this.file = file;
    }

    /** Load from disk on construction. */
    public void load() throws IOException {
        entries.clear();
        if (!Files.exists(file)) return;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            line = line.trim();
            if (!line.startsWith("{")) continue;
            String key = jsonRead(line, "key");
            String payloadB64 = jsonRead(line, "payload");
            int accessCount = Integer.parseInt(jsonRead(line, "accessCount"));
            byte[] payload = java.util.Base64.getDecoder().decode(payloadB64);
            entries.add(new ConsolidationCycle.MemoryEntry(
                    key, payload, MemoryHierarchyTier.M2, accessCount));
        }
    }

    /** Persist current entries to disk. */
    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        java.util.List<String> lines = new ArrayList<>();
        for (var e : entries) {
            String b64 = java.util.Base64.getEncoder()
                    .encodeToString(e.payload());
            lines.add("{\"key\":\"" + jsonEscape(e.key())
                    + "\",\"payload\":\"" + b64
                    + "\",\"accessCount\":" + e.accessCount() + "}");
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    public void store(String key, byte[] payload) {
        entries.removeIf(e -> e.key().equals(key));
        entries.add(new ConsolidationCycle.MemoryEntry(
                key, payload, MemoryHierarchyTier.M2, 0));
    }

    public List<ConsolidationCycle.MemoryEntry> entries() {
        return new ArrayList<>(entries);
    }

    public int size() { return entries.size(); }

    private static String jsonRead(String json, String field) {
        // Naive scan for "field":"value" or "field":number
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return "";
        int colon = json.indexOf(":", idx + field.length() + 2);
        if (colon < 0) return "";
        int start = colon + 1;
        // Skip whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return "";
        if (json.charAt(start) == '"') {
            // String value: read until next unescaped quote
            int end = start + 1;
            while (end < json.length()) {
                if (json.charAt(end) == '\\') { end += 2; continue; }
                if (json.charAt(end) == '"') break;
                end++;
            }
            return json.substring(start + 1, end);
        } else {
            // Numeric value
            int end = start;
            while (end < json.length() && "0123456789-".indexOf(json.charAt(end)) >= 0) end++;
            return json.substring(start, end);
        }
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
