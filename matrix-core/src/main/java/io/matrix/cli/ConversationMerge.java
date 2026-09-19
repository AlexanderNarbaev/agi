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
 * W409 — Conversation Merge CLI.
 * 
 * Combines multiple conversation sessions into a single new session.
 * Useful for aggregating training data from related sessions.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationMerge <output-session-id> <input1> <input2> [input3...]
 */
public final class ConversationMerge {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: ConversationMerge <output-session-id> <input1> <input2> [input3...]");
            System.exit(1);
        }
        
        String outputId = args[0];
        Path outputFile = Paths.get("data/conversations", outputId + ".ndjson");
        
        if (Files.exists(outputFile)) {
            System.err.println("Output session already exists: " + outputFile);
            System.err.println("Use a different name or delete it first.");
            System.exit(1);
        }
        
        List<Path> inputs = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Path inputFile = Paths.get("data/conversations", args[i] + ".ndjson");
            if (!Files.exists(inputFile)) {
                System.err.println("Input session not found: " + args[i]);
                System.exit(1);
            }
            inputs.add(inputFile);
        }
        
        Files.createDirectories(outputFile.getParent());
        int totalTurns = 0;
        
        try (BufferedWriter w = Files.newBufferedWriter(outputFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Path input : inputs) {
                System.out.println("Reading: " + input.getFileName());
                int turns = copyTurns(input, w);
                totalTurns += turns;
                System.out.println("  " + turns + " turns");
            }
            w.flush();
        }
        
        System.out.println();
        System.out.println("Created merged session: " + outputId);
        System.out.println("  Total turns: " + totalTurns);
        System.out.println("  From sessions: " + (args.length - 1));
    }
    
    private static int copyTurns(Path input, BufferedWriter out) throws IOException {
        int count = 0;
        try (Stream<String> lines = Files.lines(input)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) continue;
                out.write(line);
                out.write("\n");
                count++;
            }
        }
        return count;
    }
}
