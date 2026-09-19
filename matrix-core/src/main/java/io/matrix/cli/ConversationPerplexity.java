package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * W469 — ConversationPerplexity CLI.
 * 
 * Computes approximate perplexity from conversation text.
 * Uses simple Shannon entropy over word frequencies as approximation.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationPerplexity <session-id> [...]
 *   Per-session: shows estimated perplexity
 */
public final class ConversationPerplexity {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationPerplexity <session-id> [...]");
            System.exit(1);
        }
        
        Path dataDir = Paths.get("data/conversations");
        
        System.out.println("=".repeat(60));
        System.out.println("CONVERSATION PERPLEXITY (W469)");
        System.out.println("=".repeat(60));
        
        for (String sessionId : args) {
            Path sessionFile = dataDir.resolve(sessionId + ".ndjson");
            if (!Files.exists(sessionFile)) {
                System.err.println("Session not found: " + sessionId);
                continue;
            }
            computeForSession(sessionFile);
        }
    }
    
    private static void computeForSession(Path sessionFile) throws IOException {
        Map<String, Integer> wordFreq = new HashMap<>();
        int totalWords = 0;
        
        try (Stream<String> lines = Files.lines(sessionFile)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String content = extractField(line, "content");
                if (content == null) continue;
                for (String word : content.toLowerCase().split("[^a-z0-9]+")) {
                    if (word.length() > 1) {
                        wordFreq.merge(word, 1, Integer::sum);
                        totalWords++;
                    }
                }
            }
        }
        
        if (totalWords == 0 || wordFreq.isEmpty()) {
            System.out.println(sessionFile.getFileName() + ": (no content)");
            return;
        }
        
        // Compute Shannon entropy: H = -sum(p * log2(p))
        double entropy = 0;
        for (int count : wordFreq.values()) {
            double p = (double) count / totalWords;
            entropy -= p * Math.log(p) / Math.log(2);  // log2
        }
        
        // Perplexity = 2^H
        double perplexity = Math.pow(2, entropy);
        
        // Vocabulary diversity
        double ttr = (double) wordFreq.size() / totalWords;  // type-token ratio
        
        System.out.println(sessionFile.getFileName() + ":");
        System.out.println("  Words:       " + totalWords);
        System.out.println("  Vocabulary:  " + wordFreq.size() + " unique");
        System.out.println("  Entropy:     " + String.format("%.3f", entropy) + " bits/word");
        System.out.println("  Perplexity:  " + String.format("%.2f", perplexity));
        System.out.println("  TTR:         " + String.format("%.3f", ttr));
    }
    
    private static String extractField(String json, String fieldName) {
        String needle = "\"" + fieldName + "\":\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int start = idx + needle.length();
        boolean escaped = false;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == 'n') sb.append(' ');
                else if (c == 'r') sb.append(' ');
                else if (c == 't') sb.append(' ');
                else if (c == '"') sb.append('"');
                else if (c == '\\') sb.append('\\');
                else sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
