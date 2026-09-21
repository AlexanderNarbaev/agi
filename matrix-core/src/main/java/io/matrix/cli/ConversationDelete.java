package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * W406 — Conversation Delete CLI.
 * 
 * Removes a session's NDJSON file.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationDelete <session-id> [--force]
 */
public final class ConversationDelete {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationDelete <session-id> [--force]");
            System.exit(1);
        }
        
        String sessionId = args[0];
        boolean force = args.length > 1 && args[1].equals("--force");
        
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        long size = Files.size(sessionFile);
        
        if (!force) {
            System.out.println("Will delete: " + sessionFile);
            System.out.println("  Size: " + size + " bytes");
            System.out.print("Confirm? (y/N): ");
            int c = System.in.read();
            if (c != 'y' && c != 'Y') {
                System.out.println("Aborted.");
                System.exit(0);
            }
        }
        
        Files.delete(sessionFile);
        System.out.println("Deleted: " + sessionFile);
    }
}
