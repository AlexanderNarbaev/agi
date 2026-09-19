package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * W461 — Conversation Batch CLI.
 * 
 * Applies an action to multiple sessions in a batch.
 * Useful for bulk operations like bulk name, bulk delete, bulk export.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationBatch <action> [args...]
 * 
 * Actions:
 *   count       - Count lines/turns/bytes for each session
 *   validate    - Check NDJSON structure
 *   head 5      - Show first N turns of each session
 *   tail 5      - Show last N turns of each session
 *   compact 80  - One-line view of each session
 */
public final class ConversationBatch {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            printUsage();
            return;
        }
        
        String action = args[0];
        Path dataDir = Paths.get("data/conversations");
        if (!Files.exists(dataDir)) {
            System.err.println("Data dir not found: " + dataDir);
            System.exit(1);
        }
        
        List<Path> sessions = new ArrayList<>();
        try (Stream<Path> files = Files.list(dataDir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (p.toString().endsWith(".ndjson") && !p.getFileName().toString().startsWith(".")) {
                    sessions.add(p);
                }
            }
        }
        sessions.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        
        System.out.println("Batch action: " + action);
        System.out.println("Sessions: " + sessions.size());
        System.out.println("=".repeat(60));
        
        for (Path session : sessions) {
            System.out.println("--- " + session.getFileName() + " ---");
            runAction(action, session, args);
        }
    }
    
    private static void runAction(String action, Path session, String[] args) throws IOException {
        switch (action) {
            case "count":
                int lines = 0;
                int user = 0;
                int asst = 0;
                long bytes = Files.size(session);
                try (Stream<String> ls = Files.lines(session)) {
                    for (String line : (Iterable<String>) ls::iterator) {
                        if (line.isBlank()) continue;
                        lines++;
                        if (line.contains("\"role\":\"user\"")) user++;
                        else if (line.contains("\"role\":\"assistant\"")) asst++;
                    }
                }
                System.out.println("  Lines: " + lines + " (user: " + user + ", asst: " + asst + "), bytes: " + bytes);
                break;
            case "validate":
                int turns = 0;
                int issues = 0;
                try (Stream<String> ls = Files.lines(session)) {
                    for (String line : (Iterable<String>) ls::iterator) {
                        if (line.isBlank() || line.startsWith("#")) continue;
                        if (!line.startsWith("{")) issues++;
                        else if (line.contains("\"role\":") && line.contains("\"content\":")) turns++;
                    }
                }
                System.out.println("  Turns: " + turns + ", Issues: " + issues);
                break;
            case "head":
                int n = args.length > 1 ? Integer.parseInt(args[1]) : 5;
                int shown = 0;
                try (Stream<String> ls = Files.lines(session)) {
                    for (String line : (Iterable<String>) ls::iterator) {
                        if (shown >= n) break;
                        if (line.isBlank() || line.startsWith("#")) continue;
                        String role = extractField(line, "role");
                        String content = extractField(line, "content");
                        if (role != null && content != null) {
                            content = content.replace("\n", " ");
                            if (content.length() > 60) content = content.substring(0, 57) + "...";
                            System.out.println("  [" + role + "] " + content);
                            shown++;
                        }
                    }
                }
                break;
            case "tail":
                int n2 = args.length > 1 ? Integer.parseInt(args[1]) : 5;
                List<String> allLines = new ArrayList<>();
                try (Stream<String> ls = Files.lines(session)) {
                    ls.forEach(allLines::add);
                }
                int startIdx = Math.max(0, allLines.size() - n2);
                for (int i = startIdx; i < allLines.size(); i++) {
                    String line = allLines.get(i);
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String role = extractField(line, "role");
                    String content = extractField(line, "content");
                    if (role != null && content != null) {
                        content = content.replace("\n", " ");
                        if (content.length() > 60) content = content.substring(0, 57) + "...";
                        System.out.println("  [" + role + "] " + content);
                    }
                }
                break;
            default:
                System.err.println("Unknown action: " + action);
                System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage: ConversationBatch <action> [args...]");
        System.out.println();
        System.out.println("Actions:");
        System.out.println("  count                - Count turns/bytes for each");
        System.out.println("  validate             - Check NDJSON validity");
        System.out.println("  head [n=5]            - First N turns of each");
        System.out.println("  tail [n=5]            - Last N turns of each");
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
