package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * W415 — Conversation Diff CLI.
 * 
 * Compares two sessions turn-by-turn and shows differences.
 * Useful for comparing two versions of a similar conversation.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationDiff <session1> <session2>
 */
public final class ConversationDiff {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: ConversationDiff <session1> <session2>");
            System.exit(1);
        }
        
        Path file1 = Paths.get("data/conversations", args[0] + ".ndjson");
        Path file2 = Paths.get("data/conversations", args[1] + ".ndjson");
        
        if (!Files.exists(file1)) {
            System.err.println("Session not found: " + args[0]);
            System.exit(1);
        }
        if (!Files.exists(file2)) {
            System.err.println("Session not found: " + args[1]);
            System.exit(1);
        }
        
        List<Record> records1 = readRecords(file1);
        List<Record> records2 = readRecords(file2);
        
        System.out.println("Diff: " + args[0] + " vs " + args[1]);
        System.out.println("=" .repeat(60));
        System.out.println(args[0] + ": " + records1.size() + " turns");
        System.out.println(args[1] + ": " + records2.size() + " turns");
        System.out.println();
        
        int maxLen = Math.max(records1.size(), records2.size());
        int diffs = 0;
        
        for (int i = 0; i < maxLen; i++) {
            Record r1 = i < records1.size() ? records1.get(i) : null;
            Record r2 = i < records2.size() ? records2.get(i) : null;
            
            String content1 = r1 != null ? r1.content : null;
            String content2 = r2 != null ? r2.content : null;
            String role1 = r1 != null ? r1.role : null;
            String role2 = r2 != null ? r2.role : null;
            
            if (content1 == null) {
                System.out.println("Turn " + (i+1) + ":  [only in " + args[1] + "] " + truncate(content2, 50));
                diffs++;
            } else if (content2 == null) {
                System.out.println("Turn " + (i+1) + ":  [only in " + args[0] + "] " + truncate(content1, 50));
                diffs++;
            } else if (!content1.equals(content2)) {
                System.out.println("Turn " + (i+1) + " [" + role1 + " vs " + role2 + "]:");
                System.out.println("  " + args[0] + ": " + truncate(content1, 50));
                System.out.println("  " + args[1] + ": " + truncate(content2, 50));
                diffs++;
            } else {
                System.out.println("Turn " + (i+1) + ":  [SAME] " + truncate(content1, 50));
            }
        }
        
        System.out.println("=" .repeat(60));
        System.out.println("Total turns compared: " + maxLen);
        System.out.println("Differences found: " + diffs);
    }
    
    private static String truncate(String s, int max) {
        if (s == null) return "<none>";
        s = s.replace("\n", " ").replace("\r", "");
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
    
    static class Record {
        String role;
        String content;
        Record(String r, String c) { role = r; content = c; }
    }
    
    private static List<Record> readRecords(Path ndjson) throws IOException {
        List<Record> records = new ArrayList<>();
        try (Stream<String> lines = Files.lines(ndjson)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) continue;
                String role = extractField(line, "role");
                String content = extractField(line, "content");
                if (role != null && content != null) {
                    records.add(new Record(role, content));
                }
            }
        }
        return records;
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
