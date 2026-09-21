package io.matrix.cli;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * W421 — Conversation Name CLI.
 * 
 * Add, read, or remove a custom name for a conversation session.
 * The name is stored in the first line of the NDJSON as a meta comment
 * (lines starting with # are ignored by conversation readers).
 * 
 * Usage:
 *   java io.matrix.cli.ConversationName <session-id> <new-name>
 *   java io.matrix.cli.ConversationName --read <session-id>
 *   java io.matrix.cli.ConversationName --remove <session-id>
 */
public final class ConversationName {
    
    private static final String META_PREFIX = "# META name: ";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }
        
        if (args[0].equals("--read")) {
            if (args.length < 2) {
                printUsage();
                System.exit(1);
            }
            String name = readName(args[1]);
            System.out.println(name != null ? name : "(no name set)");
        } else if (args[0].equals("--remove")) {
            if (args.length < 2) {
                printUsage();
                System.exit(1);
            }
            removeName(args[1]);
            System.out.println("Removed name from: " + args[1]);
        } else {
            String sessionId = args[0];
            String newName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            writeName(sessionId, newName);
            System.out.println("Set name for " + sessionId + ": " + newName);
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  ConversationName <session> <new-name>  # Set name");
        System.out.println("  ConversationName --read <session>      # Read name");
        System.out.println("  ConversationName --remove <session>    # Remove name");
    }
    
    private static void writeName(String sessionId, String name) throws IOException {
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        // Read existing content
        String content = Files.readString(sessionFile);
        
        // Remove any existing META name line
        String[] lines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        sb.append(META_PREFIX).append(name).append("\n");
        for (String line : lines) {
            if (line.startsWith(META_PREFIX.trim())) continue;
            if (!line.trim().isEmpty()) sb.append(line).append("\n");
        }
        
        Files.writeString(sessionFile, sb.toString());
    }
    
    private static String readName(String sessionId) throws IOException {
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        try (var lines = Files.lines(sessionFile)) {
            return lines
                .filter(l -> l.startsWith(META_PREFIX))
                .findFirst()
                .map(l -> l.substring(META_PREFIX.length()).trim())
                .orElse(null);
        }
    }
    
    private static void removeName(String sessionId) throws IOException {
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        String content = Files.readString(sessionFile);
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n")) {
            if (line.startsWith(META_PREFIX)) continue;
            sb.append(line).append("\n");
        }
        Files.writeString(sessionFile, sb.toString());
    }
}
