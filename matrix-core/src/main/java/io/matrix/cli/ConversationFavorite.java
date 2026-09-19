package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * W455 — Conversation Favorite CLI.
 * 
 * Marks/unmarks sessions as favorites.
 * Favorites are stored in data/favorites.txt (one session ID per line).
 * 
 * Usage:
 *   java io.matrix.cli.ConversationFavorite <session-id> [add|remove|list]
 *   java io.matrix.cli.ConversationFavorite --list
 *   java io.matrix.cli.ConversationFavorite --clear
 */
public final class ConversationFavorite {
    
    private static final Path FAV_FILE = Paths.get("data/favorites.txt");
    
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            printUsage();
            return;
        }
        
        if (args[0].equals("--list")) {
            listFavorites();
            return;
        }
        if (args[0].equals("--clear")) {
            Files.deleteIfExists(FAV_FILE);
            System.out.println("Cleared favorites");
            return;
        }
        
        String sessionId = args[0];
        String action = args.length > 1 ? args[1] : "add";
        
        if (action.equals("add")) {
            addFavorite(sessionId);
        } else if (action.equals("remove")) {
            removeFavorite(sessionId);
        } else {
            System.err.println("Unknown action: " + action);
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  ConversationFavorite <session-id> [add|remove]   # Toggle");
        System.out.println("  ConversationFavorite --list                       # Show");
        System.out.println("  ConversationFavorite --clear                      # Remove all");
    }
    
    private static List<String> loadFavorites() throws IOException {
        List<String> favs = new ArrayList<>();
        if (!Files.exists(FAV_FILE)) return favs;
        
        try (Stream<String> lines = Files.lines(FAV_FILE)) {
            for (String line : (Iterable<String>) lines::iterator) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    favs.add(line);
                }
            }
        }
        return favs;
    }
    
    private static void saveFavorites(List<String> favs) throws IOException {
        Files.createDirectories(FAV_FILE.getParent());
        Files.writeString(FAV_FILE, String.join("\n", favs) + "\n");
    }
    
    private static void addFavorite(String sessionId) throws IOException {
        List<String> favs = loadFavorites();
        if (favs.contains(sessionId)) {
            System.out.println("Already in favorites: " + sessionId);
            return;
        }
        favs.add(sessionId);
        saveFavorites(favs);
        System.out.println("Added to favorites: " + sessionId);
    }
    
    private static void removeFavorite(String sessionId) throws IOException {
        List<String> favs = loadFavorites();
        if (!favs.remove(sessionId)) {
            System.out.println("Not in favorites: " + sessionId);
            return;
        }
        saveFavorites(favs);
        System.out.println("Removed from favorites: " + sessionId);
    }
    
    private static void listFavorites() throws IOException {
        List<String> favs = loadFavorites();
        if (favs.isEmpty()) {
            System.out.println("(no favorites)");
            return;
        }
        System.out.println("Favorites (" + favs.size() + "):");
        for (String f : favs) {
            System.out.println("  " + f);
        }
    }
}
