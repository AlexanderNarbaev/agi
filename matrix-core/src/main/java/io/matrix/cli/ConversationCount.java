package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * W419 — Conversation Count CLI.
 * 
 * Quick line/turn count for a session (like wc -l).
 * 
 * Usage:
 *   java io.matrix.cli.ConversationCount <session-id>
 */
public final class ConversationCount {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationCount <session-id>");
            System.exit(1);
        }
        
        String sessionId = args[0];
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        int lines = 0;
        int turns = 0;
        int userTurns = 0;
        int assistantTurns = 0;
        long bytes = Files.size(sessionFile);
        
        try (Stream<String> stream = Files.lines(sessionFile)) {
            for (String line : (Iterable<String>) stream::iterator) {
                if (line.isBlank()) continue;
                lines++;
                String role = extractField(line, "role");
                if (role != null) {
                    turns++;
                    if ("user".equals(role)) userTurns++;
                    else if ("assistant".equals(role)) assistantTurns++;
                }
            }
        }
        
        System.out.println("Session: " + sessionId);
        System.out.println("  Lines:  " + lines);
        System.out.println("  Turns:  " + turns + " (user: " + userTurns + ", assistant: " + assistantTurns + ")");
        System.out.println("  Bytes:  " + bytes);
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
