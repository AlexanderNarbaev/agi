package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * W423 — Conversation Validate CLI.
 * 
 * Validates NDJSON file structure and reports any issues.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationValidate [session-id|all]
 */
public final class ConversationValidate {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationValidate <session-id|all>");
            System.exit(1);
        }
        
        String target = args[0];
        Path dataDir = Paths.get("data/conversations");
        
        if (target.equals("all")) {
            try (Stream<Path> files = Files.list(dataDir)) {
                files
                    .filter(p -> p.toString().endsWith(".ndjson"))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(ConversationValidate::validateFile);
            }
        } else {
            Path sessionFile = dataDir.resolve(target + ".ndjson");
            if (!Files.exists(sessionFile)) {
                System.err.println("Session not found: " + target);
                System.exit(1);
            }
            validateFile(sessionFile);
        }
    }
    
    private static void validateFile(Path file) {
        String sessionId = file.getFileName().toString().replace(".ndjson", "");
        int lines = 0;
        int turns = 0;
        int userTurns = 0;
        int assistantTurns = 0;
        int errors = 0;
        int warnings = 0;
        int metaLines = 0;
        String lastRole = null;
        
        try (Stream<String> stream = Files.lines(file)) {
            for (String line : (Iterable<String>) stream::iterator) {
                lines++;
                if (line.isBlank()) continue;
                if (line.startsWith("#")) {
                    metaLines++;
                    continue;
                }
                if (line.startsWith("{") && line.endsWith("}")) {
                    String role = extractField(line, "role");
                    String content = extractField(line, "content");
                    if (role == null) {
                        System.err.println("  ERROR line " + lines + ": missing role");
                        errors++;
                        continue;
                    }
                    if (content == null) {
                        System.err.println("  ERROR line " + lines + ": missing content");
                        errors++;
                        continue;
                    }
                    turns++;
                    if ("user".equals(role)) userTurns++;
                    else if ("assistant".equals(role)) assistantTurns++;
                    
                    // Check turn pairing
                    if ("assistant".equals(role) && !"user".equals(lastRole)) {
                        System.err.println("  WARN line " + lines + ": assistant without prior user");
                        warnings++;
                    }
                    lastRole = role;
                } else {
                    System.err.println("  ERROR line " + lines + ": not valid JSON");
                    errors++;
                }
            }
        } catch (IOException e) {
            System.err.println("  ERROR: " + e.getMessage());
            errors++;
        }
        
        // Summary
        String status = errors == 0 ? "OK" : "ERRORS";
        System.out.println(sessionId + ": " + status + " - " + turns + " turns (" + 
            userTurns + " user, " + assistantTurns + " assistant), " + 
            metaLines + " meta lines, " + warnings + " warnings, " + errors + " errors");
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
