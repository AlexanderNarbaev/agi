package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * W427 — Conversation Summary CLI.
 * 
 * Shows key info about a session:
 * - Name (if set)
 * - Turn count (user/assistant)
 * - First/last turn timestamp
 * - Total duration
 * - Average turn length
 * 
 * Usage:
 *   java io.matrix.cli.ConversationSummary <session-id>
 */
public final class ConversationSummary {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationSummary <session-id>");
            System.exit(1);
        }
        
        String sessionId = args[0];
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        String name = null;
        int userTurns = 0;
        int assistantTurns = 0;
        int totalChars = 0;
        String firstTimestamp = null;
        String lastTimestamp = null;
        int totalTurns = 0;
        
        try (Stream<String> lines = Files.lines(sessionFile)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) continue;
                if (line.startsWith("# META name: ")) {
                    name = line.substring("# META name: ".length()).trim();
                    continue;
                }
                String role = extractField(line, "role");
                String content = extractField(line, "content");
                String timestamp = extractField(line, "timestamp");
                
                if (role == null || content == null) continue;
                
                totalTurns++;
                if ("user".equals(role)) userTurns++;
                else if ("assistant".equals(role)) assistantTurns++;
                totalChars += content.length();
                
                if (timestamp != null) {
                    if (firstTimestamp == null) firstTimestamp = timestamp;
                    lastTimestamp = timestamp;
                }
            }
        }
        
        // Calculate duration
        String duration = "N/A";
        if (firstTimestamp != null && lastTimestamp != null) {
            try {
                Instant first = Instant.parse(firstTimestamp);
                Instant last = Instant.parse(lastTimestamp);
                long millis = last.toEpochMilli() - first.toEpochMilli();
                long seconds = millis / 1000;
                if (seconds < 60) duration = seconds + "s";
                else if (seconds < 3600) duration = (seconds/60) + "m " + (seconds%60) + "s";
                else duration = (seconds/3600) + "h " + ((seconds%3600)/60) + "m";
            } catch (Exception e) {
                duration = "parse error";
            }
        }
        
        // Display
        System.out.println("=== " + sessionId + " ===");
        if (name != null) {
            System.out.println("Name:      \"" + name + "\"");
        }
        System.out.println("Turns:     " + totalTurns + " (user: " + userTurns + ", assistant: " + assistantTurns + ")");
        System.out.println("Chars:     " + totalChars + " (avg: " + (totalTurns > 0 ? totalChars / totalTurns : 0) + "/turn)");
        System.out.println("Duration:  " + duration);
        if (firstTimestamp != null) {
            System.out.println("First:     " + firstTimestamp);
        }
        if (lastTimestamp != null) {
            System.out.println("Last:      " + lastTimestamp);
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
