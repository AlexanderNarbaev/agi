package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * W468 — Conversation Tag CLI.
 * 
 * Adds free-form tags to sessions (separate from names).
 * Tags are stored as # META tags: <tag1>,<tag2>,<tag3> lines.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationTag <session> <tag> [tag2 ...]
 *   java io.matrix.cli.ConversationTag <session> --list
 *   java io.matrix.cli.ConversationTag <session> --remove <tag>
 *   java io.matrix.cli.ConversationTag --search <tag>
 */
public final class ConversationTag {
    
    private static final String META_PREFIX = "# META tags: ";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            printUsage();
            return;
        }
        
        if (args[0].equals("--search")) {
            if (args.length < 2) {
                System.err.println("Usage: ConversationTag --search <tag>");
                System.exit(1);
            }
            searchTag(args[1]);
            return;
        }
        
        String sessionId = args[0];
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        if (args.length > 1 && args[1].equals("--list")) {
            listTags(sessionFile);
        } else if (args.length > 2 && args[1].equals("--remove")) {
            removeTag(sessionFile, args[2]);
        } else {
            String[] tags = new String[args.length - 1];
            System.arraycopy(args, 1, tags, 0, args.length - 1);
            addTags(sessionFile, String.join(",", tags));
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  ConversationTag <session> <tag> [tag2...]   # Add tags");
        System.out.println("  ConversationTag <session> --list          # List tags");
        System.out.println("  ConversationTag <session> --remove <tag>  # Remove tag");
        System.out.println("  ConversationTag --search <tag>            # Find sessions with tag");
    }
    
    private static List<String> readTags(Path sessionFile) throws IOException {
        List<String> tags = new ArrayList<>();
        try (Stream<String> lines = Files.lines(sessionFile)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.startsWith(META_PREFIX)) {
                    String content = line.substring(META_PREFIX.length()).trim();
                    for (String t : content.split(",")) {
                        t = t.trim();
                        if (!t.isEmpty()) tags.add(t);
                    }
                }
            }
        }
        return tags;
    }
    
    private static void writeTags(Path sessionFile, List<String> tags) throws IOException {
        String content = Files.readString(sessionFile);
        StringBuilder sb = new StringBuilder();
        boolean added = false;
        for (String line : content.split("\n")) {
            if (line.startsWith(META_PREFIX)) continue;  // Remove old tags line
            sb.append(line).append("\n");
        }
        if (!tags.isEmpty()) {
            sb.append(META_PREFIX).append(String.join(",", tags)).append("\n");
        }
        Files.writeString(sessionFile, sb.toString());
    }
    
    private static void addTags(Path sessionFile, String newTags) throws IOException {
        List<String> tags = readTags(sessionFile);
        for (String t : newTags.split(",")) {
            t = t.trim();
            if (!t.isEmpty() && !tags.contains(t)) {
                tags.add(t);
            }
        }
        writeTags(sessionFile, tags);
        System.out.println("Tags for " + sessionFile.getFileName() + ": " + tags);
    }
    
    private static void listTags(Path sessionFile) throws IOException {
        List<String> tags = readTags(sessionFile);
        if (tags.isEmpty()) {
            System.out.println("(no tags)");
        } else {
            System.out.println("Tags: " + String.join(", ", tags));
        }
    }
    
    private static void removeTag(Path sessionFile, String tag) throws IOException {
        List<String> tags = readTags(sessionFile);
        if (tags.remove(tag)) {
            writeTags(sessionFile, tags);
            System.out.println("Removed tag: " + tag);
        } else {
            System.out.println("Tag not found: " + tag);
        }
    }
    
    private static void searchTag(String tag) throws IOException {
        Path dataDir = Paths.get("data/conversations");
        if (!Files.exists(dataDir)) {
            System.err.println("Data dir not found: " + dataDir);
            System.exit(1);
        }
        
        System.out.println("Sessions with tag '" + tag + "':");
        int count = 0;
        try (Stream<Path> files = Files.list(dataDir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (!p.toString().endsWith(".ndjson")) continue;
                if (p.getFileName().toString().startsWith(".")) continue;
                List<String> tags = readTags(p);
                if (tags.contains(tag)) {
                    System.out.println("  " + p.getFileName());
                    count++;
                }
            }
        }
        System.out.println();
        System.out.println("Total: " + count + " sessions");
    }
}
