package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * W405 — Conversation Search CLI.
 * 
 * Searches all NDJSON files for conversations containing a keyword.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationSearch <query> [data-dir]
 */
public final class ConversationSearch {
    
    private static final String DEFAULT_DATA_DIR = "data/conversations";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationSearch <query> [data-dir]");
            System.exit(1);
        }
        
        String query = args[0].toLowerCase();
        Path dataDir = Paths.get(args.length > 1 ? args[1] : DEFAULT_DATA_DIR);
        
        if (!Files.exists(dataDir)) {
            System.err.println("Data dir not found: " + dataDir);
            System.exit(1);
        }
        
        List<Match> matches = new ArrayList<>();
        
        try (Stream<Path> files = Files.list(dataDir)) {
            files
                .filter(p -> p.toString().endsWith(".ndjson"))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .forEach(p -> {
                    try {
                        searchFile(p, query, matches);
                    } catch (IOException e) {
                        System.err.println("Failed to read " + p + ": " + e.getMessage());
                    }
                });
        }
        
        System.out.println("Found " + matches.size() + " matches for query: " + args[0]);
        for (Match m : matches) {
            System.out.println();
            System.out.println("[" + m.sessionId + "] " + m.role + " (" + m.file.getFileName() + "):");
            System.out.println("  " + m.content);
        }
    }
    
    static class Match {
        Path file;
        String sessionId;
        String role;
        String content;
        Match(Path f, String s, String r, String c) {
            file = f; sessionId = s; role = r; content = c;
        }
    }
    
    private static void searchFile(Path ndjson, String query, List<Match> matches) throws IOException {
        String sessionId = ndjson.getFileName().toString().replace(".ndjson", "");
        try (Stream<String> lines = Files.lines(ndjson)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) continue;
                String role = extractField(line, "role");
                String content = extractField(line, "content");
                if (role != null && content != null && content.toLowerCase().contains(query)) {
                    matches.add(new Match(ndjson, sessionId, role, content));
                }
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
