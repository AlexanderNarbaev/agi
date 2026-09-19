package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * W411 — Conversation Tail CLI.
 * 
 * Shows the last N turns of a session (like `tail -n` for NDJSON).
 * 
 * Usage:
 *   java io.matrix.cli.ConversationTail <session-id> [n]
 *   Default n: 5
 */
public final class ConversationTail {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationTail <session-id> [n]");
            System.exit(1);
        }
        
        String sessionId = args[0];
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        // Read all lines, keep last n
        List<String> allLines = new ArrayList<>();
        try (Stream<String> lines = Files.lines(sessionFile)) {
            lines.forEach(allLines::add);
        }
        
        int start = Math.max(0, allLines.size() - n);
        System.out.println("Last " + (allLines.size() - start) + " of " + allLines.size() + " turns:");
        System.out.println("=== " + sessionId + " ===");
        System.out.println();
        
        for (int i = start; i < allLines.size(); i++) {
            String line = allLines.get(i);
            String role = extractField(line, "role");
            String content = extractField(line, "content");
            if (role != null && content != null) {
                System.out.println("[" + role.toUpperCase() + "] " + content);
                System.out.println();
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
