package io.matrix.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * W466 — Conversation Archive CLI.
 * 
 * Compresses old sessions into daily zip archives.
 * Each day's sessions are bundled into a single zip file.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationArchive [days]
 *   Default: 7 days
 * 
 * Archive: data/conversations/_archive/YYYY-MM-DD.zip
 */
public final class ConversationArchive {
    
    public static void main(String[] args) throws IOException {
        int days = args.length > 0 ? Integer.parseInt(args[0]) : 7;
        long cutoff = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
        
        Path dataDir = Paths.get("data/conversations");
        if (!Files.exists(dataDir)) {
            System.err.println("Data dir not found: " + dataDir);
            System.exit(1);
        }
        
        Path archiveDir = dataDir.resolve("_archive");
        Files.createDirectories(archiveDir);
        
        // Group old sessions by day
        List<Path> oldSessions = new ArrayList<>();
        try (Stream<Path> files = Files.list(dataDir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (!p.toString().endsWith(".ndjson")) continue;
                if (p.getFileName().toString().startsWith(".")) continue;
                if (p.toString().contains("/_archive/")) continue;
                if (p.toString().contains("/archive/")) continue;
                
                long lastModified;
                try {
                    lastModified = Files.getLastModifiedTime(p).toMillis();
                } catch (IOException e) {
                    continue;
                }
                
                if (lastModified < cutoff) {
                    oldSessions.add(p);
                }
            }
        }
        
        if (oldSessions.isEmpty()) {
            System.out.println("No sessions older than " + days + " days");
            return;
        }
        
        System.out.println("Found " + oldSessions.size() + " sessions to archive");
        System.out.println("Cutoff: " + days + " days");
        System.out.println();
        
        // Group by day
        java.util.Map<String, List<Path>> byDay = new java.util.LinkedHashMap<>();
        for (Path p : oldSessions) {
            String date = LocalDate.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_DATE);  // Use today as default
            // Try to use file's modification date
            try {
                long ms = Files.getLastModifiedTime(p).toMillis();
                date = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_DATE);
            } catch (IOException e) {
                // ignore
            }
            byDay.computeIfAbsent(date, k -> new ArrayList<>()).add(p);
        }
        
        int totalArchived = 0;
        for (java.util.Map.Entry<String, List<Path>> entry : byDay.entrySet()) {
            String date = entry.getKey();
            List<Path> dayFiles = entry.getValue();
            Path zipFile = archiveDir.resolve(date + ".zip");
            
            System.out.println(date + ": " + dayFiles.size() + " sessions -> " + zipFile);
            try (OutputStream os = Files.newOutputStream(zipFile);
                 ZipOutputStream zos = new ZipOutputStream(os)) {
                for (Path p : dayFiles) {
                    ZipEntry ze = new ZipEntry(p.getFileName().toString());
                    zos.putNextEntry(ze);
                    Files.copy(p, zos);
                    zos.closeEntry();
                }
            }
            totalArchived += dayFiles.size();
        }
        
        System.out.println();
        System.out.println("Archived " + totalArchived + " sessions into " + byDay.size() + " zip files");
        System.out.println("Note: Original NDJSON files were NOT deleted.");
        System.out.println("      Run ConversationDelete to remove after verifying archives.");
    }
}
