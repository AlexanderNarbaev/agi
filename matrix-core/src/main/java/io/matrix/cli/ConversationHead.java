package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * W412 — Conversation Head CLI.
 * 
 * Shows the first N turns of a session (like `head -n` for NDJSON).
 * 
 * Usage:
 *   java io.matrix.cli.ConversationHead <session-id> [n]
 *   Default n: 5
 */
public final class ConversationHead {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationHead <session-id> [n]");
            System.exit(1);
        }
        
        String sessionId = args[0];
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        System.out.println("First " + n + " turns:");
        System.out.println("=== " + sessionId + " ===");
        System.out.println();
        
        int[] shown = {0};
        try (Stream<String> lines = Files.lines(sessionFile)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (shown[0] >= n) break;
                if (line.isBlank()) continue;
                String role = extractField(line, "role");
                String content = extractField(line, "content");
                if (role != null && content != null) {
                    System.out.println("[" + role.toUpperCase() + "] " + content);
                    System.out.println();
                    shown[0]++;
                }
            }
        }
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
