package io.matrix.cli;

import io.matrix.knowledge.SimpleKnowledgeBase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * W539 — Conversation Knowledge Adder.
 * 
 * Adds documents to the knowledge base from files or inline.
 * Usage:
 *   java io.matrix.cli.ConversationKnowledgeAdder <file.md>
 *   java io.matrix.cli.ConversationKnowledgeAdder <key> <fact>
 */
public final class ConversationKnowledgeAdder {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage:");
            System.out.println("  ConversationKnowledgeAdder <file.md>  # Add from file");
            System.out.println("  ConversationKnowledgeAdder <key> <fact>  # Add inline fact");
            System.exit(1);
        }
        
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        
        if (args.length == 1) {
            // Add from file
            Path file = Paths.get(args[0]);
            if (!Files.exists(file)) {
                System.err.println("File not found: " + file);
                System.exit(1);
            }
            String content = Files.readString(file);
            String key = file.getFileName().toString().replace(".md", "");
            kb.addDocument(key, key, content);
            System.out.println("Added: " + key + " (" + content.length() + " chars)");
        } else {
            // Add inline
            String key = args[0];
            String fact = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            kb.addDocument(key, key, fact);
            System.out.println("Added: " + key);
        }
        
        System.out.println("KB size: " + kb.size());
    }
}
