package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * W465 — Conversation Pattern Analysis CLI.
 * 
 * Analyzes patterns across all sessions:
 * - First word of user questions (most common)
 * - Turn length distribution
 * - Time-of-day patterns
 * - Question vs statement ratio
 * 
 * Usage:
 *   java io.matrix.cli.ConversationPatterns
 */
public final class ConversationPatterns {
    
    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get("data/conversations");
        if (!Files.exists(dataDir)) {
            System.err.println("Data dir not found: " + dataDir);
            System.exit(1);
        }
        
        Map<String, Integer> firstWords = new HashMap<>();
        Map<Integer, Integer> lengthBuckets = new HashMap<>();  // 0-50, 51-100, etc.
        Map<Integer, Integer> hourBuckets = new HashMap<>();  // hour of day 0-23
        int userTurns = 0;
        int assistantTurns = 0;
        int questionTurns = 0;
        int statementTurns = 0;
        int totalLength = 0;
        int totalTurns = 0;
        
        try (Stream<Path> files = Files.list(dataDir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (!p.toString().endsWith(".ndjson")) continue;
                if (p.getFileName().toString().startsWith(".")) continue;
                
                try (Stream<String> lines = Files.lines(p)) {
                    for (String line : (Iterable<String>) lines::iterator) {
                        if (line.isBlank() || line.startsWith("#")) continue;
                        String role = extractField(line, "role");
                        String content = extractField(line, "content");
                        String ts = extractField(line, "timestamp");
                        
                        if (role == null || content == null) continue;
                        totalTurns++;
                        totalLength += content.length();
                        
                        if ("user".equals(role)) {
                            userTurns++;
                            String firstWord = content.trim().split("\\s+")[0].toLowerCase();
                            firstWord = firstWord.replaceAll("[^a-z]", "");
                            if (!firstWord.isEmpty() && firstWord.length() > 2) {
                                firstWords.merge(firstWord, 1, Integer::sum);
                            }
                            if (content.trim().endsWith("?")) questionTurns++;
                            else statementTurns++;
                        } else if ("assistant".equals(role)) {
                            assistantTurns++;
                        }
                        
                        // Length bucket
                        int bucket = (content.length() / 50) * 50;
                        lengthBuckets.merge(bucket, 1, Integer::sum);
                        
                        // Hour bucket
                        if (ts != null && ts.length() > 13) {
                            try {
                                int hour = Integer.parseInt(ts.substring(11, 13));
                                hourBuckets.merge(hour, 1, Integer::sum);
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }
                }
            }
        }
        
        System.out.println("=".repeat(60));
        System.out.println("CONVERSATION PATTERN ANALYSIS (W465)");
        System.out.println("=".repeat(60));
        System.out.println();
        
        System.out.println("TOTALS:");
        System.out.println("  Total turns:    " + totalTurns);
        System.out.println("  User turns:     " + userTurns);
        System.out.println("  Assistant turns:" + assistantTurns);
        System.out.println("  Questions:      " + questionTurns);
        System.out.println("  Statements:     " + statementTurns);
        if (totalTurns > 0) {
            System.out.println("  Avg turn len:   " + (totalLength / totalTurns) + " chars");
        }
        System.out.println();
        
        System.out.println("TOP 10 FIRST WORDS (user inputs):");
        firstWords.entrySet().stream()
            .filter(e -> e.getValue() >= 2)
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .forEach(e -> System.out.println("  " + String.format("%-12s", e.getKey()) + " " + e.getValue()));
        System.out.println();
        
        System.out.println("LENGTH DISTRIBUTION:");
        lengthBuckets.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByKey())
            .forEach(e -> {
                int bar = Math.min(50, e.getValue());
                System.out.println("  " + String.format("%4d-%4d", e.getKey(), e.getKey() + 49) + ": " +
                    "#".repeat(bar) + " (" + e.getValue() + ")");
            });
        System.out.println();
        
        System.out.println("HOUR-OF-DAY ACTIVITY (most active hours):");
        hourBuckets.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
            .limit(5)
            .forEach(e -> System.out.println("  " + String.format("%02d:00", e.getKey()) + " - " + e.getValue() + " turns"));
        System.out.println();
        
        System.out.println("=".repeat(60));
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
