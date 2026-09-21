package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * W445 — Live Conversation Monitor CLI.
 * 
 * Monitors live conversation activity by polling the data dir.
 * Shows new turns as they arrive.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationMonitor [interval-seconds]
 *   Default: 2 seconds
 * 
 * Press Ctrl+C to stop.
 */
public final class ConversationMonitor {
    
    public static void main(String[] args) throws IOException, InterruptedException {
        int interval = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        Path dataDir = Paths.get("data/conversations");
        if (!Files.exists(dataDir)) {
            System.err.println("Data dir not found: " + dataDir);
            System.exit(1);
        }
        
        System.out.println("=".repeat(60));
        System.out.println("MATRIX CONVERSATION MONITOR (W445)");
        System.out.println("Watching: " + dataDir);
        System.out.println("Interval: " + interval + "s");
        System.out.println("Press Ctrl+C to stop");
        System.out.println("=".repeat(60));
        
        // Show existing sessions count
        long initialCount = Files.list(dataDir)
            .filter(p -> p.toString().endsWith(".ndjson"))
            .count();
        long initialBytes = Files.list(dataDir)
            .filter(p -> p.toString().endsWith(".ndjson"))
            .mapToLong(p -> {
                try { return Files.size(p); } catch (Exception e) { return 0; }
            }).sum();
        
        System.out.println("Initial: " + initialCount + " sessions, " + initialBytes + " bytes");
        System.out.println();
        
        long lastTotalBytes = initialBytes;
        
        while (true) {
            Thread.sleep(interval * 1000L);
            
            long count = Files.list(dataDir)
                .filter(p -> p.toString().endsWith(".ndjson"))
                .count();
            long bytes = Files.list(dataDir)
                .filter(p -> p.toString().endsWith(".ndjson"))
                .mapToLong(p -> {
                    try { return Files.size(p); } catch (Exception e) { return 0; }
                }).sum();
            
            long delta = bytes - lastTotalBytes;
            
            if (delta != 0 || count != initialCount) {
                String sign = delta > 0 ? "+" : "";
                System.out.println("[" + java.time.LocalTime.now().withNano(0) + "] " +
                    "sessions=" + count + ", bytes=" + bytes + " (" + sign + delta + ")");
                lastTotalBytes = bytes;
                initialCount = count;
            }
        }
    }
}
