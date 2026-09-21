package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * W458 — Conversation Dedup CLI.
 * 
 * Detects duplicate or near-duplicate sessions.
 * Uses simple TF (term frequency) hashing for fast comparison.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationDedup [--threshold=0.8]
 *   Default threshold: 0.8
 */
public final class ConversationDedup {
    
    public static void main(String[] args) throws IOException {
        double threshold = 0.8;
        for (String arg : args) {
            if (arg.startsWith("--threshold=")) {
                threshold = Double.parseDouble(arg.substring("--threshold=".length()));
            }
        }
        
        Path dataDir = Paths.get("data/conversations");
        if (!Files.exists(dataDir)) {
            System.err.println("Data dir not found: " + dataDir);
            System.exit(1);
        }
        
        // Compute TF for all sessions
        Map<String, Map<String, Integer>> sessionTfs = new HashMap<>();
        try (Stream<Path> files = Files.list(dataDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!file.toString().endsWith(".ndjson")) continue;
                if (file.getFileName().toString().startsWith(".")) continue;
                if (file.getFileName().toString().contains("/archive/")) continue;
                
                String sessionId = file.getFileName().toString().replace(".ndjson", "");
                Map<String, Integer> tf = computeTF(file);
                sessionTfs.put(sessionId, tf);
            }
        }
        
        // Compare all pairs
        List<String> sessionIds = new ArrayList<>(sessionTfs.keySet());
        System.out.println("=".repeat(60));
        System.out.println("DUPLICATE DETECTION (threshold=" + threshold + ")");
        System.out.println("Sessions: " + sessionIds.size());
        System.out.println("=".repeat(60));
        
        int dupCount = 0;
        for (int i = 0; i < sessionIds.size(); i++) {
            for (int j = i + 1; j < sessionIds.size(); j++) {
                String s1 = sessionIds.get(i);
                String s2 = sessionIds.get(j);
                double sim = cosineSimilarity(sessionTfs.get(s1), sessionTfs.get(s2));
                if (sim >= threshold) {
                    System.out.printf("  %.3f  %s  <->  %s%n", sim, s1, s2);
                    dupCount++;
                }
            }
        }
        
        if (dupCount == 0) {
            System.out.println("No duplicates found at threshold " + threshold);
        } else {
            System.out.println();
            System.out.println("Found " + dupCount + " duplicate pairs");
        }
    }
    
    private static Map<String, Integer> computeTF(Path ndjson) throws IOException {
        Map<String, Integer> tf = new HashMap<>();
        try (Stream<String> lines = Files.lines(ndjson)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String content = extractField(line, "content");
                if (content == null) continue;
                for (String word : content.toLowerCase().split("[^a-z0-9]+")) {
                    if (word.length() > 3) {
                        tf.merge(word, 1, Integer::sum);
                    }
                }
            }
        }
        return tf;
    }
    
    private static double cosineSimilarity(Map<String, Integer> a, Map<String, Integer> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;
        
        Set<String> allWords = new HashSet<>();
        allWords.addAll(a.keySet());
        allWords.addAll(b.keySet());
        
        double dot = 0, normA = 0, normB = 0;
        for (String w : allWords) {
            int va = a.getOrDefault(w, 0);
            int vb = b.getOrDefault(w, 0);
            dot += va * vb;
            normA += va * va;
            normB += vb * vb;
        }
        
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
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
