package io.matrix.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import io.matrix.api.QwenChatTemplate;
import io.matrix.api.QwenOnnxBridge;

/**
 * W450 — MatrixChatClient: Simple library API for embedding conversation in apps.
 * 
 * Usage:
 *   MatrixChatClient client = MatrixChatClient.builder()
 *       .modelPath("models/onnx/qwen05b")
 *       .dataDir("data/conversations")
 *       .build();
 *   
 *   String reply = client.chat("user-session", "Hello!");
 *   
 *   client.close();
 * 
 * This is the recommended way to integrate the MATRIX conversation stack
 * into your own Java application.
 */
public final class MatrixChatClient {
    
    private final QwenOnnxBridge bridge;
    private final Path dataDir;
    
    private MatrixChatClient(QwenOnnxBridge bridge, Path dataDir) {
        this.bridge = bridge;
        this.dataDir = dataDir;
    }
    
    /**
     * Send a message and get a response. Conversation history is preserved
     * per sessionId.
     * 
     * @param sessionId unique identifier for the conversation
     * @param message the user's message
     * @return the assistant's response
     */
    public String chat(String sessionId, String message) {
        // Load prior history
        List<QwenChatTemplate.Message> history = new java.util.ArrayList<>();
        history.add(QwenChatTemplate.Message.system(
            "You are MATRIX, a cognitive AI built on a deterministic neural architecture."
        ));
        loadPriorHistory(sessionId, history);
        
        // Add user message
        history.add(QwenChatTemplate.Message.user(message));
        
        // Generate response
        String reply = bridge.chat(message, 128);
        if (reply == null || reply.isBlank()) {
            reply = "[model returned empty]";
        }
        
        // Record both turns
        recordTurn(sessionId, "user", message);
        recordTurn(sessionId, "assistant", reply);
        
        return reply;
    }
    
    /**
     * Get conversation history for a session (list of role/content pairs).
     */
    public List<Turn> getHistory(String sessionId) {
        java.util.List<Turn> result = new java.util.ArrayList<>();
        Path sessionFile = dataDir.resolve(sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) return result;
        
        try (Stream<String> lines = Files.lines(sessionFile)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String role = extractField(line, "role");
                String content = extractField(line, "content");
                if (role != null && content != null) {
                    result.add(new Turn(role, content));
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return result;
    }
    
    /**
     * Delete a session.
     */
    public boolean deleteSession(String sessionId) {
        Path sessionFile = dataDir.resolve(sessionId + ".ndjson");
        try {
            return Files.deleteIfExists(sessionFile);
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Close the client and release model resources.
     */
    public void close() {
        if (bridge != null) bridge.close();
    }
    
    private void loadPriorHistory(String sessionId, List<QwenChatTemplate.Message> history) {
        Path sessionFile = dataDir.resolve(sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) return;
        
        try (Stream<String> lines = Files.lines(sessionFile)) {
            int count = 0;
            for (String line : (Iterable<String>) lines::iterator) {
                if (count >= 10) break;  // Last 10 turns
                if (line.isBlank() || line.startsWith("#")) continue;
                String role = extractField(line, "role");
                String content = extractField(line, "content");
                if (role != null && content != null) {
                    QwenChatTemplate.Role r = QwenChatTemplate.Role.valueOf(role.toUpperCase());
                    history.add(new QwenChatTemplate.Message(r, content));
                    count++;
                }
            }
        } catch (IOException e) {
            // ignore
        }
    }
    
    private void recordTurn(String sessionId, String role, String content) {
        try {
            Files.createDirectories(dataDir);
            Path sessionFile = dataDir.resolve(sessionId + ".ndjson");
            try (var w = Files.newBufferedWriter(sessionFile, 
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                String escaped = content
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
                w.write(String.format(
                    "{\"conversationId\":\"%s\",\"role\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}\n",
                    sessionId, role, escaped, Instant.now().toString()));
            }
        } catch (IOException e) {
            // ignore
        }
    }
    
    private String extractField(String json, String fieldName) {
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
    
    /** Simple value object for conversation turn. */
    public record Turn(String role, String content) {}
    
    /**
     * Builder for MatrixChatClient.
     */
    public static final class Builder {
        private String modelPath = "models/onnx/qwen05b";
        private Path dataDir = Paths.get("data/conversations");
        private boolean useGpu = false;
        private int maxNewTokens = 128;
        
        public Builder modelPath(String path) {
            this.modelPath = path;
            return this;
        }
        
        public Builder dataDir(String dir) {
            this.dataDir = Paths.get(dir);
            return this;
        }
        
        public Builder useGpu(boolean use) {
            this.useGpu = use;
            return this;
        }
        
        public Builder maxNewTokens(int n) {
            this.maxNewTokens = n;
            return this;
        }
        
        public MatrixChatClient build() {
            try {
                Files.createDirectories(dataDir);
                QwenOnnxBridge bridge = new QwenOnnxBridge(Paths.get(modelPath));
                bridge.useGpu(useGpu);
                bridge.setMaxNewTokens(maxNewTokens);
                bridge.load();
                return new MatrixChatClient(bridge, dataDir);
            } catch (Exception e) {
                throw new RuntimeException("Failed to build client", e);
            }
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}
