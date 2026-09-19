package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * W395 — Real Conversation Replay CLI.
 * 
 * Replays a recorded conversation from NDJSON for review/analysis.
 * 
 * Usage:
 *   java io.matrix.cli.RealConversationReplay <session-id>
 *   java io.matrix.cli.RealConversationReplay --list
 */
public final class RealConversationReplay {
    
    private static final String DEFAULT_DATA_DIR = "data/conversations";
    
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("  RealConversationReplay --list");
            System.out.println("  RealConversationReplay <session-id>");
            System.exit(1);
        }
        
        if (args[0].equals("--list")) {
            System.out.println("Available sessions:");
            for (String s : RealConversationCli.listRecentSessions(50)) {
                System.out.println("  " + s);
            }
            return;
        }
        
        String sessionId = args[0];
        Path sessionFile = Paths.get(DEFAULT_DATA_DIR, sessionId + ".ndjson");
        
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.err.println("Run with --list to see available sessions.");
            System.exit(1);
        }
        
        System.out.println("=== Session: " + sessionId + " ===");
        System.out.println("File: " + sessionFile);
        System.out.println();
        
        int turns = 0;
        try (Stream<String> lines = Files.lines(sessionFile)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) continue;
                String role = RealConversationCli.class.getName() != null ? 
                    extractField(line, "role") : "?";
                String content = RealConversationCli.class.getName() != null ?
                    extractField(line, "content") : line;
                String prefix;
                switch (role != null ? role : "?") {
                    case "user": prefix = "[USER]"; break;
                    case "assistant": prefix = "[MATRIX]"; break;
                    case "system": prefix = "[SYSTEM]"; break;
                    default: prefix = "[" + role + "]";
                }
                System.out.println(prefix + " " + content);
                System.out.println();
                turns++;
            }
        }
        
        System.out.println("---");
        System.out.println("Total turns: " + turns);
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
