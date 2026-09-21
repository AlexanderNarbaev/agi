package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * W449 — Conversation Cleanup CLI.
 * 
 * Archives old sessions to a daily subdirectory.
 * Sessions older than [days] days are moved to data/conversations/archive/YYYY-MM-DD/.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationCleanup [days] [--dry-run]
 *   Default: 30 days
 */
public final class ConversationCleanup {
    
    public static void main(String[] args) throws IOException {
        int days = 30;
        boolean dryRun = false;
        
        for (String arg : args) {
            if (arg.equals("--dry-run")) {
                dryRun = true;
            } else {
                try {
                    days = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid number: " + arg);
                    System.exit(1);
                }
            }
        }
        
        Path dataDir = Paths.get("data/conversations");
        if (!Files.exists(dataDir)) {
            System.err.println("Data dir not found: " + dataDir);
            System.exit(1);
        }
        
        long cutoff = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
        String archiveDate = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE);
        Path archiveDir = dataDir.resolve("archive").resolve(archiveDate);
        
        System.out.println("=".repeat(60));
        System.out.println("CONVERSATION CLEANUP");
        System.out.println("Cutoff: " + days + " days");
        System.out.println("Archive: " + archiveDir);
        System.out.println("Dry run: " + dryRun);
        System.out.println("=".repeat(60));
        
        List<Path> toArchive = new ArrayList<>();
        try (Stream<Path> files = Files.list(dataDir)) {
            files
                .filter(p -> p.toString().endsWith(".ndjson"))
                .filter(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toMillis() < cutoff;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(toArchive::add);
        }
        
        if (toArchive.isEmpty()) {
            System.out.println("No sessions to archive.");
            return;
        }
        
        System.out.println("Found " + toArchive.size() + " sessions to archive:");
        for (Path p : toArchive) {
            try {
                long lastModified = Files.getLastModifiedTime(p).toMillis();
                String date = Instant.ofEpochMilli(lastModified).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_DATE);
                System.out.println("  " + p.getFileName() + " (last modified: " + date + ")");
            } catch (IOException e) {
                // ignore
            }
        }
        
        if (dryRun) {
            System.out.println("\n[DRY RUN] Would archive to: " + archiveDir);
            return;
        }
        
        Files.createDirectories(archiveDir);
        int moved = 0;
        for (Path p : toArchive) {
            try {
                Path target = archiveDir.resolve(p.getFileName());
                Files.move(p, target);
                moved++;
            } catch (IOException e) {
                System.err.println("Failed to move " + p + ": " + e.getMessage());
            }
        }
        
        System.out.println();
        System.out.println("Moved " + moved + " sessions to " + archiveDir);
    }
}
