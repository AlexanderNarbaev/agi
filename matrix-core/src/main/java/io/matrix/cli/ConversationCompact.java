package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * W414 — Conversation Compact CLI.
 * 
 * Produces a compact one-line summary of each turn for quick review.
 * Useful for long sessions where you want to see what happened at a glance.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationCompact <session-id> [max-chars]
 *   Default max-chars: 80
 */
public final class ConversationCompact {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationCompact <session-id> [max-chars]");
            System.exit(1);
        }
        
        String sessionId = args[0];
        int maxChars = args.length > 1 ? Integer.parseInt(args[1]) : 80;
        
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        System.out.println("Compact view: " + sessionId);
        System.out.println("=" .repeat(60));
        
        int turn = 0;
        try (Stream<String> lines = Files.lines(sessionFile)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) continue;
                String role = extractField(line, "role");
                String content = extractField(line, "content");
                if (role != null && content != null) {
                    turn++;
                    String truncated = content.length() > maxChars 
                        ? content.substring(0, maxChars) + "..." 
                        : content;
                    // Replace newlines with spaces for compact view
                    truncated = truncated.replace("\n", " ").replace("\r", "");
                    System.out.printf("%3d  [%-9s] %s%n", turn, role.toUpperCase(), truncated);
                }
            }
        }
        
        System.out.println("=" .repeat(60));
        System.out.println("Total: " + turn + " turns");
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
