package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * W448 — Conversation Summarize CLI.
 * 
 * Uses the Qwen model to generate a summary of a session.
 * Returns a concise 1-2 sentence summary.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationSummarize <session-id> [model-path]
 */
public final class ConversationSummarize {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: ConversationSummarize <session-id> [model-path]");
            System.exit(1);
        }
        
        String sessionId = args[0];
        String modelPath = args.length > 1 ? args[1] : "models/onnx/qwen05b";
        
        Path sessionFile = Paths.get("data/conversations", sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) {
            System.err.println("Session not found: " + sessionId);
            System.exit(1);
        }
        
        // Load model
        io.matrix.api.QwenOnnxBridge bridge = new io.matrix.api.QwenOnnxBridge(Paths.get(modelPath));
        bridge.useGpu(false);
        bridge.setMaxNewTokens(80);
        bridge.load();
        
        // Read session content
        StringBuilder conversation = new StringBuilder();
        try (Stream<String> lines = Files.lines(sessionFile)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String role = extractField(line, "role");
                String content = extractField(line, "content");
                if (role != null && content != null) {
                    conversation.append(role).append(": ").append(content).append("\n");
                }
            }
        }
        
        // Build prompt
        String prompt = "Summarize this conversation in 1-2 sentences:\n" + conversation.toString();
        
        System.out.println("=== Summarizing: " + sessionId + " ===");
        System.out.println();
        String summary = bridge.chat(prompt, 80);
        System.out.println(summary);
        
        bridge.close();
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
