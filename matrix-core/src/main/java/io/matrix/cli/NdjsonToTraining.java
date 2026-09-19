package io.matrix.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * W399 — Convert NDJSON conversation logs to training pairs.
 * 
 * Transforms user/assistant pairs from RealConversationCli NDJSON files
 * into (input, output) JSONL format for MatrixTrainingEngine.
 * 
 * Usage:
 *   java io.matrix.cli.NdjsonToTraining <output-file> [input-dir]
 * 
 * Default input dir: data/conversations
 */
public final class NdjsonToTraining {
    
    private static final String DEFAULT_INPUT_DIR = "data/conversations";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: NdjsonToTraining <output.jsonl> [input-dir]");
            System.exit(1);
        }
        
        Path outputFile = Paths.get(args[0]);
        Path inputDir = Paths.get(args.length > 1 ? args[1] : DEFAULT_INPUT_DIR);
        
        if (!Files.exists(inputDir)) {
            System.err.println("Input dir not found: " + inputDir);
            System.exit(1);
        }
        
        List<String[]> pairs = new ArrayList<>();
        final int[] sessionsRead = {0};
        
        try {
            Files.list(inputDir)
                .filter(p -> p.toString().endsWith(".ndjson"))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .forEach(p -> {
                    try {
                        pairs.addAll(extractPairs(p));
                        sessionsRead[0]++;
                    } catch (IOException e) {
                        System.err.println("Failed to read " + p + ": " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.err.println("Failed to list input dir: " + e.getMessage());
            System.exit(1);
        }
        
        // Write as JSONL (one JSON object per line)
        try (BufferedWriter w = Files.newBufferedWriter(outputFile, 
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int written = 0;
            for (String[] pair : pairs) {
                String input = escape(pair[0]);
                String output = escape(pair[1]);
                w.write("{\"input\":\"" + input + "\",\"output\":\"" + output 
                    + "\",\"source\":\"conversation\"}\n");
                written++;
            }
            w.flush();
            System.out.println("Wrote " + written + " training pairs to " + outputFile);
        }
        System.out.println("Read " + sessionsRead[0] + " session files");
    }
    
    /**
     * Extract (user, assistant) pairs from a NDJSON file.
     * Each pair is a (userMsg, assistantMsg) tuple.
     */
    private static List<String[]> extractPairs(Path ndjson) throws IOException {
        List<String[]> pairs = new ArrayList<>();
        String lastUserMsg = null;
        
        try (var lines = Files.lines(ndjson)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) continue;
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
    
    /**
     * Extract a string field from simple JSON using scan.
     */
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
                if (c == 'n') sb.append('\n');
                else if (c == 'r') sb.append('\r');
                else if (c == 't') sb.append('\t');
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
    
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
