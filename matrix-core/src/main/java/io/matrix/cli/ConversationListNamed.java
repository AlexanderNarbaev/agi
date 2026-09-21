package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * W422 — Conversation List Named CLI.
 * 
 * Lists all sessions with their custom names (if set).
 * 
 * Usage:
 *   java io.matrix.cli.ConversationListNamed
 */
public final class ConversationListNamed {
    
    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get("data/conversations");
        if (!Files.exists(dataDir)) {
            System.err.println("Data dir not found: " + dataDir);
            System.exit(1);
        }
        
        List<Entry> entries = new ArrayList<>();
        
        try (Stream<Path> files = Files.list(dataDir)) {
            files
                .filter(p -> p.toString().endsWith(".ndjson"))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .forEach(p -> {
                    String sessionId = p.getFileName().toString().replace(".ndjson", "");
                    String name = readName(p);
                    long size = 0;
                    try {
                        size = Files.size(p);
                    } catch (IOException e) {
                        // ignore
                    }
                    entries.add(new Entry(sessionId, name, size));
                });
        }
        
        // Sort by name if available, else by session id
        entries.sort((a, b) -> {
            if (a.name != null && b.name != null) return a.name.compareTo(b.name);
            if (a.name != null) return -1;
            if (b.name != null) return 1;
            return a.sessionId.compareTo(b.sessionId);
        });
        
        System.out.println("Sessions (" + entries.size() + "):");
        System.out.println("=" .repeat(70));
        for (Entry e : entries) {
            String name = e.name != null ? "\"" + e.name + "\"" : "(no name)";
            System.out.printf("  %-30s  %s  %d bytes%n", e.sessionId, name, e.size);
        }
    }
    
    static class Entry {
        String sessionId;
        String name;
        long size;
        Entry(String s, String n, long sz) { sessionId = s; name = n; size = sz; }
    }
    
    private static String readName(Path ndjson) {
        try (Stream<String> lines = Files.lines(ndjson)) {
            return lines
                .filter(l -> l.startsWith("# META name: "))
                .findFirst()
                .map(l -> l.substring("# META name: ".length()).trim())
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
