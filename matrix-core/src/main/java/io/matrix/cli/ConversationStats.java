package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * W401 — Conversation Statistics CLI.
 * 
 * Shows aggregate statistics across all recorded conversations.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationStats [data-dir]
 * 
 * Default data dir: data/conversations
 */
public final class ConversationStats {
    
    private static final String DEFAULT_DATA_DIR = "data/conversations";
    
    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get(args.length > 0 ? args[0] : DEFAULT_DATA_DIR);
        
        if (!Files.exists(dataDir)) {
            System.err.println("Data dir not found: " + dataDir);
            System.exit(1);
        }
        
        Stats stats = new Stats();
        
        try (Stream<Path> files = Files.list(dataDir)) {
            files
                .filter(p -> p.toString().endsWith(".ndjson"))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .forEach(p -> {
                    try {
                        stats.addFile(p);
                    } catch (IOException e) {
                        System.err.println("Failed to read " + p + ": " + e.getMessage());
                    }
                });
        }
        
        stats.printReport();
    }
    
    static class Stats {
        int totalSessions = 0;
        int totalTurns = 0;
        int userTurns = 0;
        int assistantTurns = 0;
        int totalChars = 0;
        Map<String, Integer> sessionsByDay = new HashMap<>();
        long earliestTimestamp = Long.MAX_VALUE;
        long latestTimestamp = 0L;
        
        void addFile(Path ndjson) throws IOException {
            totalSessions++;
            try (Stream<String> lines = Files.lines(ndjson)) {
                for (String line : (Iterable<String>) lines::iterator) {
                    if (line.isBlank()) continue;
                    String role = extractField(line, "role");
                    String content = extractField(line, "content");
                    String timestamp = extractField(line, "timestamp");
                    
                    if (role != null && content != null) {
                        totalTurns++;
                        totalChars += content.length();
                        if ("user".equals(role)) userTurns++;
                        else if ("assistant".equals(role)) assistantTurns++;
                    }
                    
                    if (timestamp != null && timestamp.length() > 10) {
                        String day = timestamp.substring(0, 10);  // YYYY-MM-DD
                        sessionsByDay.merge(day, 1, Integer::sum);
                    }
                }
            }
        }
        
        void printReport() {
            System.out.println("=== MATRIX Conversation Statistics ===");
            System.out.println("Data dir: " + DEFAULT_DATA_DIR);
            System.out.println();
            System.out.println("Sessions:      " + totalSessions);
            System.out.println("Total turns:   " + totalTurns);
            System.out.println("  user:        " + userTurns);
            System.out.println("  assistant:   " + assistantTurns);
            System.out.println("Total chars:   " + totalChars);
            if (totalTurns > 0) {
                System.out.println("Avg chars/turn: " + (totalChars / totalTurns));
            }
            System.out.println();
            System.out.println("Days with activity:");
            sessionsByDay.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByKey())
                .forEach(e -> System.out.println("  " + e.getKey() + " - " + e.getValue() + " sessions"));
        }
    }
    
    /**
     * Extract a string field using simple scanning.
     */
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
