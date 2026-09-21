package io.matrix.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * W438 — Conversation Restore CLI.
 * 
 * Restores NDJSON sessions from a zip backup.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationRestore <input-zip>
 */
public final class ConversationRestore {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationRestore <input-zip>");
            System.exit(1);
        }
        
        Path zipFile = Paths.get(args[0]);
        if (!Files.exists(zipFile)) {
            System.err.println("Zip file not found: " + zipFile);
            System.exit(1);
        }
        
        Path dataDir = Paths.get("data/conversations");
        Files.createDirectories(dataDir);
        
        int count = 0;
        int skipped = 0;
        try (InputStream is = Files.newInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = dataDir.resolve(entry.getName());
                if (Files.exists(target)) {
                    System.err.println("Skipping existing: " + entry.getName());
                    skipped++;
                    continue;
                }
                Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        
        System.out.println("Restored " + count + " sessions from " + zipFile);
        if (skipped > 0) {
            System.out.println("Skipped " + skipped + " existing sessions");
        }
    }
}
