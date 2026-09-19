package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * W430 — Conversation Report CLI.
 * 
 * Generates a comprehensive analysis report across ALL sessions.
 * Shows:
 * - Total session count
 * - Total turn count
 * - Average turns per session
 * - Average session duration
 * - Most active day
 * - Most common words (simple frequency)
 * - Sessions with names
 * - Per-role turn breakdown
 * 
 * Usage:
 *   java io.matrix.cli.ConversationReport [output-file]
 */
public final class ConversationReport {
    
    public static void main(String[] args) throws IOException {
        boolean toFile = args.length > 0;
        
        StringBuilder out = new StringBuilder();
        out.append("=".repeat(70)).append("\n");
        out.append("MATRIX CONVERSATION REPORT\n");
        out.append("=".repeat(70)).append("\n\n");
        
        Path dataDir = Paths.get("data/conversations");
        if (!Files.exists(dataDir)) {
            out.append("Data dir not found: ").append(dataDir).append("\n");
            writeOutput(out, toFile, args);
            return;
        }
        
        // Aggregate stats
        int totalSessions = 0;
        int totalTurns = 0;
        int totalUserTurns = 0;
        int totalAssistantTurns = 0;
        int totalChars = 0;
        int totalMetaLines = 0;
        long totalSize = 0;
        int namedSessions = 0;
        long earliestMs = Long.MAX_VALUE;
        long latestMs = 0L;
        Map<String, Integer> turnsPerDay = new HashMap<>();
        Map<String, Integer> words = new HashMap<>();
        
        try (Stream<Path> files = Files.list(dataDir)) {
            var fileList = files
                .filter(p -> p.toString().endsWith(".ndjson"))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .toList();
            
            for (Path file : fileList) {
                totalSessions++;
                totalSize += Files.size(file);
                
                try (Stream<String> lines = Files.lines(file)) {
                    for (String line : (Iterable<String>) lines::iterator) {
                        if (line.isBlank()) continue;
                        if (line.startsWith("# META name:")) {
                            namedSessions++;
                            continue;
                        }
                        if (line.startsWith("#")) {
                            totalMetaLines++;
                            continue;
                        }
                        String role = extractField(line, "role");
                        String content = extractField(line, "content");
                        String timestamp = extractField(line, "timestamp");
                        
                        if (role == null || content == null) continue;
                        
                        totalTurns++;
                        if ("user".equals(role)) totalUserTurns++;
                        else if ("assistant".equals(role)) totalAssistantTurns++;
                        totalChars += content.length();
                        
                        // Count words
                        for (String word : content.toLowerCase().split("[^a-z0-9]+")) {
                            if (word.length() > 3) {  // skip short/stop words
                                words.merge(word, 1, Integer::sum);
                            }
                        }
                        
                        // Track timestamps
                        if (timestamp != null && timestamp.length() > 10) {
                            String day = timestamp.substring(0, 10);
                            turnsPerDay.merge(day, 1, Integer::sum);
                            try {
                                long ms = java.time.Instant.parse(timestamp).toEpochMilli();
                                earliestMs = Math.min(earliestMs, ms);
                                latestMs = Math.max(latestMs, ms);
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        
        // Summary
        out.append(String.format("Sessions:          %d (named: %d, %d%%)%n",
            totalSessions, namedSessions, totalSessions > 0 ? (namedSessions * 100 / totalSessions) : 0));
        out.append(String.format("Total turns:       %d (user: %d, assistant: %d)%n",
            totalTurns, totalUserTurns, totalAssistantTurns));
        out.append(String.format("Total characters:  %d%n", totalChars));
        out.append(String.format("Total size:        %d bytes%n", totalSize));
        out.append(String.format("Meta lines:        %d%n", totalMetaLines));
        out.append("\n");
        
        // Averages
        out.append("AVERAGES:\n");
        if (totalSessions > 0) {
            out.append(String.format("  Turns/session:   %.1f%n", (double) totalTurns / totalSessions));
            out.append(String.format("  Chars/turn:      %.1f%n", (double) totalChars / totalTurns));
            out.append(String.format("  Chars/session:   %.1f%n", (double) totalChars / totalSessions));
        }
        out.append("\n");
        
        // Time range
        if (earliestMs != Long.MAX_VALUE && latestMs > 0) {
            long durationMs = latestMs - earliestMs;
            long days = durationMs / (1000 * 60 * 60 * 24);
            out.append(String.format("TIME RANGE:%n"));
            out.append(String.format("  Earliest:  %s%n",
                java.time.Instant.ofEpochMilli(earliestMs).toString()));
            out.append(String.format("  Latest:    %s%n",
                java.time.Instant.ofEpochMilli(latestMs).toString()));
            out.append(String.format("  Span:      %d days%n", days));
            out.append("\n");
        }
        
        // Daily activity
        if (!turnsPerDay.isEmpty()) {
            out.append("DAILY ACTIVITY:\n");
            turnsPerDay.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByKey())
                .forEach(e -> out.append(String.format("  %s  %d turns%n", e.getKey(), e.getValue())));
            out.append("\n");
        }
        
        // Top words
        if (!words.isEmpty()) {
            out.append("TOP 20 WORDS (>3 chars):\n");
            words.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .forEach(e -> out.append(String.format("  %-15s  %d%n", e.getKey(), e.getValue())));
            out.append("\n");
        }
        
        out.append("=".repeat(70)).append("\n");
        out.append("END OF REPORT\n");
        out.append("=".repeat(70)).append("\n");
        
        writeOutput(out, toFile, args);
    }
    
    private static void writeOutput(StringBuilder out, boolean toFile, String[] args) {
        if (toFile) {
            try {
                Path outFile = Paths.get(args[0]);
                Files.createDirectories(outFile.getParent() != null ? outFile.getParent() : Paths.get("."));
                Files.writeString(outFile, out.toString());
                System.out.println("Report written to: " + outFile);
            } catch (IOException e) {
                System.err.println("Failed to write: " + e.getMessage());
                System.out.print(out);
            }
        } else {
            System.out.print(out);
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
