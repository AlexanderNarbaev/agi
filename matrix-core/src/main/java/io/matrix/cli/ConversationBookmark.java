package io.matrix.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * W446 — Conversation Bookmark CLI.
 * 
 * Adds bookmarks to specific turns in a session.
 * Bookmarks stored as # META bookmark: <text> lines in NDJSON.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationBookmark <session> <turn> <note>
 *   java io.matrix.cli.ConversationBookmark <session> --list
 *   java io.matrix.cli.ConversationBookmark <session> --remove <turn>
 */
public final class ConversationBookmark {
    
    private static final String META_PREFIX = "# META bookmark turn=";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage:");
            System.out.println("  ConversationBookmark <session> <turn> <note>     # Add");
            System.out.println("  ConversationBookmark <session> --list          # List");
            System.out.println("  ConversationBookmark <session> --remove <turn>  # Remove");
            System.exit(1);
        }
        
        String sessionId = args[0];
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        if (args[1].equals("--list")) {
            listBookmarks(sessionFile);
        } else if (args[1].equals("--remove")) {
            if (args.length < 3) {
                System.err.println("Usage: ConversationBookmark <session> --remove <turn>");
                System.exit(1);
            }
            removeBookmark(sessionFile, Integer.parseInt(args[2]));
        } else {
            int turn = Integer.parseInt(args[1]);
            String note = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            addBookmark(sessionFile, turn, note);
        }
    }
    
    private static void addBookmark(Path sessionFile, int turn, String note) throws IOException {
        String content = Files.readString(sessionFile);
        StringBuilder sb = new StringBuilder();
        boolean added = false;
        for (String line : content.split("\n")) {
            if (line.startsWith(META_PREFIX) && line.contains("turn=" + turn + " ")) {
                // Skip existing bookmark for this turn
                continue;
            }
            sb.append(line).append("\n");
            if (!added && line.contains("\"role\":") && !line.isEmpty()) {
                sb.append(META_PREFIX).append(turn).append(" note=\"").append(note).append("\"\n");
                added = true;
            }
        }
        if (added) {
            Files.writeString(sessionFile, sb.toString());
            System.out.println("Bookmarked turn " + turn + " in " + sessionFile.getFileName() + ": " + note);
        } else {
            System.err.println("Could not find turn " + turn);
        }
    }
    
    private static void listBookmarks(Path sessionFile) throws IOException {
        try (Stream<String> lines = Files.lines(sessionFile)) {
            boolean any = false;
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.startsWith(META_PREFIX)) {
                    System.out.println(line.substring("# META ".length()));
                    any = true;
                }
            }
            if (!any) {
                System.out.println("(no bookmarks)");
            }
        }
    }
    
    private static void removeBookmark(Path sessionFile, int turn) throws IOException {
        String content = Files.readString(sessionFile);
        StringBuilder sb = new StringBuilder();
        boolean removed = false;
        for (String line : content.split("\n")) {
            if (line.startsWith(META_PREFIX) && line.contains("turn=" + turn + " ")) {
                removed = true;
                continue;
            }
            sb.append(line).append("\n");
        }
        Files.writeString(sessionFile, sb.toString());
        if (removed) {
            System.out.println("Removed bookmark for turn " + turn);
        } else {
            System.out.println("No bookmark found for turn " + turn);
        }
    }
}
