package io.matrix.learning;

import io.matrix.knowledge.SimpleKnowledgeBase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * W485 — Conversation Learner.
 * 
 * Learns from recorded conversations to improve brain responses.
 * Extracts Q&A pairs from NDJSON and adds them to the knowledge base.
 */
public final class ConversationLearner {
    
    private final SimpleKnowledgeBase knowledgeBase;
    private final Path conversationsDir;
    private int learnedPairs = 0;
    
    public ConversationLearner(SimpleKnowledgeBase kb, Path conversationsDir) {
        this.knowledgeBase = kb;
        this.conversationsDir = conversationsDir;
    }
    
    public int learnAll() throws IOException {
        if (!Files.exists(conversationsDir)) return 0;
        int totalPairs = 0;
        try (Stream<Path> files = Files.list(conversationsDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!file.toString().endsWith(".ndjson")) continue;
                if (file.getFileName().toString().startsWith(".")) continue;
                totalPairs += learnFromFile(file);
            }
        }
        learnedPairs += totalPairs;
        return totalPairs;
    }
    
    public int learnFromFile(Path ndjsonFile) throws IOException {
        List<String[]> pairs = extractPairs(ndjsonFile);
        int count = 0;
        for (String[] pair : pairs) {
            if (pair[1].length() < 10) continue;
            String docId = "learned-" + ndjsonFile.getFileName().toString().replace(".ndjson","") + "-" + count;
            knowledgeBase.addDocument(docId, pair[0], pair[1]);
            count++;
        }
        return count;
    }
    
    public List<String[]> extractPairs(Path ndjson) throws IOException {
        List<String[]> pairs = new ArrayList<>();
        String lastUserMsg = null;
        try (Stream<String> lines = Files.lines(ndjson)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String role = extractField(line, "role");
                String content = extractField(line, "content");
                if (role == null || content == null) continue;
                if ("user".equals(role)) {
                    lastUserMsg = content;
                } else if ("assistant".equals(role) && lastUserMsg != null) {
                    pairs.add(new String[]{lastUserMsg, content});
                    lastUserMsg = null;
                }
            }
        }
        return pairs;
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
    
    public int getLearnedPairs() { return learnedPairs; }
    
    public static void main(String[] args) throws Exception {
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        Path convDir = Paths.get("data/conversations");
        ConversationLearner learner = new ConversationLearner(kb, convDir);
        
        System.out.println("=== Conversation Learner ===");
        System.out.println("Before: KB size = " + kb.size());
        
        int pairs = learner.learnAll();
        
        System.out.println("Learned " + pairs + " Q&A pairs");
        System.out.println("After: KB size = " + kb.size());
    }
}