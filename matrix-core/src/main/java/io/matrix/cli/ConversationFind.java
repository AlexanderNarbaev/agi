package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * W424 — Conversation Find Similar CLI.
 * 
 * Finds sessions similar to a query using simple term frequency (TF).
 * 
 * Usage:
 *   java io.matrix.cli.ConversationFind <query> [top-n=5]
 */
public final class ConversationFind {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: ConversationFind <query> [top-n=5]");
            System.exit(1);
        }
        
        String query = args[0].toLowerCase();
        int topN = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        
        String[] queryTerms = query.split("\\s+");
        
        // Compute TF for each session
        Map<String, Map<String, Integer>> sessionTerms = new HashMap<>();
        Path dataDir = Paths.get("data/conversations");
        
        if (Files.exists(dataDir)) {
            try (Stream<Path> files = Files.list(dataDir)) {
                files
                    .filter(p -> p.toString().endsWith(".ndjson"))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(p -> {
                        try {
                            String sessionId = p.getFileName().toString().replace(".ndjson", "");
                            Map<String, Integer> tf = computeTF(p);
                            sessionTerms.put(sessionId, tf);
                        } catch (IOException e) {
                            // ignore
                        }
                    });
            }
        }
        
        // Score each session
        List<Score> scores = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> entry : sessionTerms.entrySet()) {
            double score = 0;
            Map<String, Integer> tf = entry.getValue();
            for (String term : queryTerms) {
                score += tf.getOrDefault(term, 0);
            }
            if (score > 0) {
                scores.add(new Score(entry.getKey(), score));
            }
        }
        
        // Sort by score descending
        scores.sort(Comparator.comparingDouble((Score s) -> s.score).reversed());
        
        System.out.println("Top " + Math.min(topN, scores.size()) + " sessions for: \"" + query + "\"");
        System.out.println("=" .repeat(60));
        for (int i = 0; i < Math.min(topN, scores.size()); i++) {
            Score s = scores.get(i);
            System.out.printf("  %-30s  score=%.0f%n", s.sessionId, s.score);
        }
        
        if (scores.isEmpty()) {
            System.out.println("  (no matches)");
        }
    }
    
    private static Map<String, Integer> computeTF(Path ndjson) throws IOException {
        Map<String, Integer> tf = new HashMap<>();
        try (Stream<String> lines = Files.lines(ndjson)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String content = extractField(line, "content");
                if (content == null) continue;
                
                String[] words = content.toLowerCase().split("[^a-z0-9]+");
                for (String word : words) {
                    if (word.length() > 2) {  // skip short
                        tf.merge(word, 1, Integer::sum);
                    }
                }
            }
        }
        return tf;
    }
    
    static class Score {
        String sessionId;
        double score;
        Score(String s, double sc) { sessionId = s; score = sc; }
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
