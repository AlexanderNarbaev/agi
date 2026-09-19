package io.matrix.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * W407 — Conversation Export CLI.
 * 
 * Exports NDJSON conversations to alternative formats:
 * - json: Pretty-printed JSON (full conversation)
 * - csv:  CSV (one row per turn: session, role, content, timestamp)
 * - txt:  Plain text transcript
 * 
 * Usage:
 *   java io.matrix.cli.ConversationExport <session-id> <format> [output-file]
 *   java io.matrix.cli.ConversationExport --all <format> [output-file]
 */
public final class ConversationExport {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage:");
            System.out.println("  ConversationExport <session-id> <format> [output-file]");
            System.out.println("  ConversationExport --all <format> [output-file]");
            System.out.println("Formats: json, csv, txt");
            System.exit(1);
        }
        
        String format = args[args.length - (args.length > 2 ? 2 : 1)];
        if (args[0].equals("--all")) {
            // Export all sessions
            Path outputFile = args.length > 2 ? Paths.get(args[args.length - 1]) : 
                Paths.get("data/exports/all-" + format + "." + format);
            exportAll(format, outputFile);
        } else {
            // Export single session
            String sessionId = args[0];
            Path outputFile = args.length > 2 ? Paths.get(args[2]) :
                Paths.get("data/exports/" + sessionId + "." + format);
            exportSession(sessionId, format, outputFile);
        }
    }
    
    private static void exportSession(String sessionId, String format, Path outputFile) throws IOException {
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        List<Record> records = readRecords(sessionFile);
        Files.createDirectories(outputFile.getParent());
        
        try (BufferedWriter w = Files.newBufferedWriter(outputFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            switch (format) {
                case "json": writeJson(w, sessionId, records); break;
                case "csv": writeCsv(w, records); break;
                case "txt": writeTxt(w, sessionId, records); break;
                default:
                    System.err.println("Unknown format: " + format);
                    System.exit(1);
            }
        }
        System.out.println("Exported " + records.size() + " turns to " + outputFile);
    }
    
    private static void exportAll(String format, Path outputFile) throws IOException {
        Path dataDir = Paths.get("data/conversations");
        List<Record> allRecords = new ArrayList<>();
        List<String> sessions = new ArrayList<>();
        
        try (Stream<Path> files = Files.list(dataDir)) {
            files
                .filter(p -> p.toString().endsWith(".ndjson"))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .forEach(p -> {
                    try {
                        String sessionId = p.getFileName().toString().replace(".ndjson", "");
                        sessions.add(sessionId);
                        allRecords.addAll(readRecords(p));
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
        
        Files.createDirectories(outputFile.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(outputFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            switch (format) {
                case "json": writeJson(w, "all-sessions", allRecords); break;
                case "csv": writeCsv(w, allRecords); break;
                case "txt": writeTxt(w, "all-sessions", allRecords); break;
                default:
                    System.err.println("Unknown format: " + format);
                    System.exit(1);
            }
        }
        System.out.println("Exported " + allRecords.size() + " turns from " + sessions.size() + " sessions to " + outputFile);
    }
    
    static class Record {
        String sessionId;
        String role;
        String content;
        String timestamp;
        Record(String s, String r, String c, String t) {
            sessionId = s; role = r; content = c; timestamp = t;
        }
    }
    
    private static List<Record> readRecords(Path ndjson) throws IOException {
        List<Record> records = new ArrayList<>();
        String sessionId = ndjson.getFileName().toString().replace(".ndjson", "");
        try (Stream<String> lines = Files.lines(ndjson)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) continue;
                String role = extractField(line, "role");
                String content = extractField(line, "content");
                String timestamp = extractField(line, "timestamp");
                if (role != null && content != null) {
                    records.add(new Record(sessionId, role, content, timestamp != null ? timestamp : ""));
                }
            }
        }
        return records;
    }
    
    private static void writeJson(BufferedWriter w, String sessionId, List<Record> records) throws IOException {
        w.write("{\n");
        w.write("  \"sessionId\": \"" + sessionId + "\",\n");
        w.write("  \"turns\": [\n");
        for (int i = 0; i < records.size(); i++) {
            Record r = records.get(i);
            w.write("    {\"role\": \"" + r.role + "\", \"content\": \"" + escape(r.content) + "\"");
            if (!r.timestamp.isEmpty()) {
                w.write(", \"timestamp\": \"" + r.timestamp + "\"");
            }
            w.write("}");
            if (i < records.size() - 1) w.write(",");
            w.write("\n");
        }
        w.write("  ]\n");
        w.write("}\n");
    }
    
    private static void writeCsv(BufferedWriter w, List<Record> records) throws IOException {
        w.write("session_id,role,content,timestamp\n");
        for (Record r : records) {
            w.write(csvField(r.sessionId) + ",");
            w.write(csvField(r.role) + ",");
            w.write(csvField(r.content) + ",");
            w.write(csvField(r.timestamp) + "\n");
        }
    }
    
    private static void writeTxt(BufferedWriter w, String sessionId, List<Record> records) throws IOException {
        w.write("=== " + sessionId + " ===\n\n");
        for (Record r : records) {
            w.write("[" + r.role.toUpperCase() + "] ");
            w.write(r.content);
            w.write("\n\n");
        }
    }
    
    private static String csvField(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
    
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
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
                if (c == 'n') sb.append('\n');
                else if (c == 'r') sb.append('\r');
                else if (c == 't') sb.append('\t');
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
