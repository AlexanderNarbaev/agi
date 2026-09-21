package io.matrix.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * W437 — Conversation Backup CLI.
 * 
 * Archives NDJSON sessions to a zip file for backup.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationBackup <output-zip>
 */
public final class ConversationBackup {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationBackup <output-zip>");
            System.exit(1);
        }
        
        Path zipFile = Paths.get(args[0]);
        Path dataDir = Paths.get("data/conversations");
        if (!Files.exists(dataDir)) {
            System.err.println("Data dir not found: " + dataDir);
            System.exit(1);
        }
        
        final int[] count = {0};
        try (OutputStream os = Files.newOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            Files.list(dataDir)
                .filter(p -> p.toString().endsWith(".ndjson"))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .forEach(p -> {
                    try {
                        ZipEntry entry = new ZipEntry(p.getFileName().toString());
                        zos.putNextEntry(entry);
                        Files.copy(p, zos);
                        zos.closeEntry();
                        count[0]++;
                    } catch (IOException e) {
                        System.err.println("Failed to add " + p + ": " + e.getMessage());
                    }
                });
        }
        
        long size = Files.size(zipFile);
        System.out.println("Backed up " + count[0] + " sessions to " + zipFile);
        System.out.println("Size: " + size + " bytes");
    }
}
