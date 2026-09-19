package io.matrix.cli;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * W454 — Conversation to SQL CLI.
 * 
 * Converts NDJSON sessions to SQL INSERT statements.
 * Useful for migrating to a relational database.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationToSql [output-file]
 *   Default: data/exports/conversations.sql
 * 
 * Generates:
 *   - CREATE TABLE conversations (...)
 *   - CREATE TABLE turns (...)
 *   - INSERT INTO conversations VALUES (...)
 *   - INSERT INTO turns VALUES (...)
 */
public final class ConversationToSql {
    
    public static void main(String[] args) throws IOException {
        Path outFile = Paths.get(args.length > 0 ? args[0] : "data/exports/conversations.sql");
        Files.createDirectories(outFile.getParent());
        
        Path dataDir = Paths.get("data/conversations");
        if (!Files.exists(dataDir)) {
            System.err.println("Data dir not found: " + dataDir);
            System.exit(1);
        }
        
        int sessionCount = 0;
        int turnCount = 0;
        
        try (OutputStreamWriter w = new OutputStreamWriter(
                Files.newOutputStream(outFile), StandardCharsets.UTF_8)) {
            // Write schema
            w.write("-- MATRIX Conversation SQL Export (W454)\n\n");
            w.write("CREATE TABLE IF NOT EXISTS conversations (\n");
            w.write("  session_id VARCHAR(128) PRIMARY KEY,\n");
            w.write("  name VARCHAR(256),\n");
            w.write("  first_timestamp TIMESTAMP,\n");
            w.write("  last_timestamp TIMESTAMP,\n");
            w.write("  turn_count INT\n");
            w.write(");\n\n");
            w.write("CREATE TABLE IF NOT EXISTS turns (\n");
            w.write("  turn_id BIGSERIAL PRIMARY KEY,\n");
            w.write("  session_id VARCHAR(128) NOT NULL,\n");
            w.write("  turn_number INT NOT NULL,\n");
            w.write("  role VARCHAR(16) NOT NULL,\n");
            w.write("  content TEXT,\n");
            w.write("  timestamp TIMESTAMP,\n");
            w.write("  FOREIGN KEY (session_id) REFERENCES conversations(session_id)\n");
            w.write(");\n\n");
            
            // Write data
            try (Stream<Path> files = Files.list(dataDir)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (!file.toString().endsWith(".ndjson")) continue;
                    if (file.getFileName().toString().startsWith(".")) continue;
                    String sessionId = file.getFileName().toString().replace(".ndjson", "");
                    if (sessionId.contains("/archive/")) continue;  // Skip archived
                    
                    String name = null;
                    String firstTs = null;
                    String lastTs = null;
                    int count = 0;
                    int turnNum = 0;
                    
                    try (Stream<String> lines = Files.lines(file)) {
                        for (String line : (Iterable<String>) lines::iterator) {
                            if (line.isBlank()) continue;
                            if (line.startsWith("# META name: ")) {
                                name = escape(line.substring("# META name: ".length()).trim());
                                continue;
                            }
                            if (line.startsWith("#")) continue;
                            
                            String role = extractField(line, "role");
                            String content = extractField(line, "content");
                            String ts = extractField(line, "timestamp");
                            
                            if (role == null || content == null) continue;
                            
                            turnNum++;
                            if (firstTs == null) firstTs = ts;
                            lastTs = ts;
                            count++;
                            
                            w.write("INSERT INTO turns (session_id, turn_number, role, content, timestamp) VALUES (");
                            w.write("'" + escape(sessionId) + "', ");
                            w.write(turnNum + ", ");
                            w.write("'" + role + "', ");
                            w.write("'" + escape(content) + "', ");
                            w.write(ts != null ? "'" + ts + "'" : "NULL");
                            w.write(");\n");
                            turnCount++;
                        }
                    }
                    
                    // Write conversation summary
                    w.write("INSERT INTO conversations (session_id, name, first_timestamp, last_timestamp, turn_count) VALUES (");
                    w.write("'" + escape(sessionId) + "', ");
                    w.write(name != null ? "'" + name + "'" : "NULL");
                    w.write(", ");
                    w.write(firstTs != null ? "'" + firstTs + "'" : "NULL");
                    w.write(", ");
                    w.write(lastTs != null ? "'" + lastTs + "'" : "NULL");
                    w.write(", " + count);
                    w.write(");\n\n");
                    
                    sessionCount++;
                }
            }
        }
        
        System.out.println("Exported " + sessionCount + " sessions, " + turnCount + " turns to " + outFile);
    }
    
    private static String escape(String s) {
        return s.replace("'", "''");
    }
    
    private static String extractField(String json, String fieldName) {
        String needle = "\"" + fieldName + "\":\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int start = idx + needle.length();
        boolean escaped = false;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == 'n') sb.append(' ');
                else if (c == 'r') sb.append(' ');
                else if (c == 't') sb.append(' ');
                else if (c == '"') sb.append('"');
                else if (c == '\\') sb.append('\\');
                else sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
