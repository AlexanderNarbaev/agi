package io.matrix.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Corpus schema migration scaffolding (RUN 25).
 *
 * <p>Reads {@code qa_pairs.json} (an array of {question, answer,
 * category, source} objects) and writes a versioned corpus with
 * a top-level {@code {version, migratedAt, pairs[]}} envelope.
 *
 * <p>This is a forward-only migration: each input record gets
 * immutable IDs, optional metadata, and a stable schema. New
 * fields can be added without breaking older readers (extra fields
 * are ignored).
 *
 * <p>Migration log: append-only text file at
 * {@code data/migrations.log} with one JSON line per migration:
 * <pre>
 *   {"from":1,"to":2,"records":6607,"at":"2026-09-05T14:00:00Z","ok":true}
 * </pre>
 *
 * <p>For now, only ONE migration is implemented:
 * <ul>
 *   <li>v1 → v2: wrap bare array into envelope, assign stable IDs,
 *       add {@code migratedAt} timestamp.</li>
 * </ul>
 * Future migrations register as new methods.
 */
public class CorpusMigration {

    /** Schema versions. */
    public static final int CURRENT_VERSION = 2;
    public static final int LEGACY_VERSION = 1;

    private static final Pattern LOG_LINE =
            Pattern.compile("\"from\":(\\d+),\"to\":(\\d+),\"records\":(\\d+)");

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path migrationsLog;
    private final AtomicLong migrationsApplied = new AtomicLong();
    private final List<String> errors = new ArrayList<>();

    public CorpusMigration() {
        this(Path.of("data/migrations.log"));
    }

    public CorpusMigration(Path migrationsLog) {
        this.migrationsLog = migrationsLog;
        try {
            Files.createDirectories(migrationsLog.getParent());
        } catch (IOException e) {
            errors.add("could not create migrations dir: " + e.getMessage());
        }
    }

    /**
     * Migrate a bare-array corpus to the versioned envelope format.
     *
     * @param input  path to the bare-array qa_pairs.json
     * @param output path where the versioned envelope is written
     * @return migration result with record counts
     */
    public MigrationResult migrate(Path input, Path output) throws IOException {
        JsonNode root = mapper.readTree(input.toFile());
        if (!root.isArray()) {
            throw new IOException("expected bare JSON array at root, got " + root.getNodeType());
        }

        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("version", CURRENT_VERSION);
        envelope.put("migratedAt", Instant.now().toString());
        envelope.put("sourceCount", root.size());
        var pairs = envelope.putArray("pairs");

        int migrated = 0;
        int skipped = 0;
        long nextId = 1;
        for (JsonNode n : root) {
            String q = textOrNull(n, "question");
            String a = textOrNull(n, "answer");
            if (q == null || a == null || q.isBlank() || a.isBlank()) {
                skipped++;
                continue;
            }
            ObjectNode out = mapper.createObjectNode();
            out.put("id", nextId++);
            out.put("question", q.trim());
            out.put("answer", a.trim());
            if (n.has("category")) out.put("category", n.get("category").asText());
            else out.put("category", "general");
            if (n.has("source")) out.put("source", n.get("source").asText());
            else out.put("source", "");
            pairs.add(out);
            migrated++;
        }
        envelope.put("migratedCount", migrated);
        envelope.put("skippedCount", skipped);

        Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), envelope);

        long applied = migrationsApplied.incrementAndGet();
        appendLog(LEGACY_VERSION, CURRENT_VERSION, migrated);

        return new MigrationResult(LEGACY_VERSION, CURRENT_VERSION,
                root.size(), migrated, skipped, applied);
    }

    /**
     * Verify a file's schema version. Returns the version (positive integer)
     * or -1 if the file is missing / unreadable / unknown format.
     */
    public int detectVersion(Path input) {
        try {
            JsonNode root = mapper.readTree(input.toFile());
            if (root.has("version") && root.get("version").isInt()) {
                return root.get("version").asInt();
            }
            if (root.isArray()) return LEGACY_VERSION;
            return -1;
        } catch (IOException e) {
            return -1;
        }
    }

    /** Count migrations logged in the migration log. */
    public long migrationCount() {
        try {
            if (!Files.exists(migrationsLog)) return 0;
            long count = 0;
            for (String line : Files.readAllLines(migrationsLog)) {
                if (LOG_LINE.matcher(line).find()) count++;
            }
            return count;
        } catch (IOException e) {
            return -1;
        }
    }

    private void appendLog(int from, int to, int records) {
        String line = String.format(
                "{\"from\":%d,\"to\":%d,\"records\":%d,\"at\":\"%s\",\"ok\":true}",
                from, to, records, Instant.now());
        try {
            Files.writeString(migrationsLog,
                    line + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            errors.add("could not write log: " + e.getMessage());
        }
    }

    public List<String> errors() { return List.copyOf(errors); }

    private static String textOrNull(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return null;
        return n.get(field).asText();
    }

    /** Result of a migration run. */
    public record MigrationResult(int fromVersion, int toVersion,
                                  int sourceCount, int migratedCount,
                                  int skippedCount, long migrationsApplied) {
        public boolean isComplete() {
            return migratedCount + skippedCount == sourceCount;
        }
    }
}
