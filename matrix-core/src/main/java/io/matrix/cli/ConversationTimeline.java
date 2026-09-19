package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * W463 — Conversation Timeline CLI.
 * 
 * Shows all sessions as a chronological timeline.
 * Visualizes when each conversation happened.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationTimeline [days]
 *   Default: 30 days
 */
public final class ConversationTimeline {
    
    static class Entry {
        String sessionId;
        String name;
        String firstTs;
        long lastModified;
        Entry(String s, String n, String t, long l) { sessionId = s; name = n; firstTs = t; lastModified = l; }
    }
    
    public static void main(String[] args) throws IOException {
        int days = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        long cutoff = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
        
        Path dataDir = Paths.get("data/conversations");
        if (!Files.exists(dataDir)) {
            System.err.println("Data dir not found: " + dataDir);
            System.exit(1);
        }
        
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> files = Files.list(dataDir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (!p.toString().endsWith(".ndjson")) continue;
                if (p.getFileName().toString().startsWith(".")) continue;
                
                long lastModified;
                try {
                    lastModified = Files.getLastModifiedTime(p).toMillis();
                } catch (IOException e) {
                    continue;
                }
                
                if (lastModified < cutoff) continue;
                
                String firstTs = null;
                String name = null;
                try (Stream<String> ls = Files.lines(p)) {
                    for (String line : (Iterable<String>) ls::iterator) {
                        if (line.startsWith("# META name: ")) {
                            name = line.substring("# META name: ".length()).trim();
                        }
                        if (line.contains("\"timestamp\":\"")) {
                            int idx = line.indexOf("\"timestamp\":\"") + 13;
                            int end = line.indexOf("\"", idx);
                            if (end > idx) {
                                firstTs = line.substring(idx, end);
                                break;
                            }
                        }
                    }
                }
                
                String sessionId = p.getFileName().toString().replace(".ndjson", "");
                entries.add(new Entry(sessionId, name, firstTs, lastModified));
            }
        }
        
        entries.sort((a, b) -> Long.compare(a.lastModified, b.lastModified));
        
        System.out.println("=".repeat(70));
        System.out.println("CONVERSATION TIMELINE (last " + days + " days)");
        System.out.println("Sessions: " + entries.size());
        System.out.println("=".repeat(70));
        
        if (entries.isEmpty()) {
            System.out.println("No sessions in the last " + days + " days");
            return;
        }
        
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm");
        
        String currentDay = "";
        for (Entry e : entries) {
            String day = Instant.ofEpochMilli(e.lastModified)
                .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            if (!day.equals(currentDay)) {
                System.out.println();
                System.out.println("--- " + day + " ---");
                currentDay = day;
            }
            
            String time = Instant.ofEpochMilli(e.lastModified)
                .atZone(ZoneId.systemDefault()).format(fmt);
            String display = e.name != null ? e.name : e.sessionId;
            System.out.println("  " + time + "  " + display);
        }
        
        System.out.println();
        System.out.println("=".repeat(70));
    }
}
