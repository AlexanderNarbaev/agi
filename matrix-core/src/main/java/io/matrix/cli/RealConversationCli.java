package io.matrix.cli;

import io.matrix.api.QwenChatTemplate;
import io.matrix.api.QwenOnnxBridge;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

/**
 * W393 — Real Conversation CLI with multi-session continuity.
 * 
 * Self-contained command-line tool that:
 * - Loads Qwen2.5-0.5B ONNX model locally (CPU)
 * - Conducts multi-turn conversation with full context
 * - Records every turn to NDJSON via local writer
 * - Loads prior history for the same session ID (continuity across runs)
 * - Lists recent sessions
 * 
 * Usage:
 *   java io.matrix.cli.RealConversationCli [model-path] [session-id]
 *   java io.matrix.cli.RealConversationCli --list-sessions
 */
public final class RealConversationCli {

    private static final String DEFAULT_MODEL_PATH = "models/onnx/qwen05b";
    private static final String DEFAULT_DATA_DIR = "data/conversations";
    private static final int MAX_HISTORY_TURNS = 10;
    private static final int MAX_NEW_TOKENS = 128;
    
    public static void main(String[] args) {
        // W393: Support --list-sessions flag
        if (args.length > 0 && args[0].equals("--list-sessions")) {
            int max = args.length > 1 ? Integer.parseInt(args[1]) : 10;
            System.out.println("[real-conv] Recent sessions:");
            for (String s : listRecentSessions(max)) {
                System.out.println("  " + s);
            }
            return;
        }
        
        String modelPath = args.length > 0 ? args[0] : DEFAULT_MODEL_PATH;
        String sessionId = args.length > 1 ? args[1] : generateSessionId();
        
        System.out.println("[real-conv] MATRIX Real Conversation CLI (W392+W393)");
        System.out.println("[real-conv] model=" + modelPath);
        System.out.println("[real-conv] session=" + sessionId);
        System.out.println("[real-conv] type 'quit' to exit, 'reset' to clear history, 'info' for status");
        System.out.println();
        
        QwenOnnxBridge bridge = new QwenOnnxBridge(Paths.get(modelPath));
        bridge.useGpu(false);
        bridge.setMaxNewTokens(MAX_NEW_TOKENS);
        
        System.out.println("[real-conv] Loading model...");
        long t0 = System.currentTimeMillis();
        boolean loaded = bridge.load();
        long loadMs = System.currentTimeMillis() - t0;
        
        if (!loaded) {
            System.err.println("[real-conv] FATAL: Model failed to load from " + modelPath);
            System.err.println("[real-conv] Check that model.onnx and tokenizer files exist.");
            System.exit(1);
        }
        System.out.println("[real-conv] Model loaded in " + loadMs + "ms (CPU mode)");
        System.out.println();
        
        Path sessionFile = Paths.get(DEFAULT_DATA_DIR, sessionId + ".ndjson");
        try {
            Files.createDirectories(Paths.get(DEFAULT_DATA_DIR));
        } catch (IOException e) {
            System.err.println("[real-conv] Could not create data dir: " + e.getMessage());
        }
        BufferedWriter writer;
        try {
            writer = Files.newBufferedWriter(sessionFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[real-conv] Could not open session file: " + e.getMessage());
            bridge.close();
            System.exit(1);
            return;
        }
        long sessionStartNs = System.nanoTime();
        
        List<QwenChatTemplate.Message> history = new ArrayList<>();
        history.add(QwenChatTemplate.Message.system(
            "You are MATRIX, a cognitive AI built on a deterministic neural architecture. "
            + "You answer questions about yourself, your design, and general knowledge clearly and concisely."
        ));
        
        // W393: Load prior history for this session if exists
        int priorTurns = loadPriorHistory(sessionId, history);
        if (priorTurns > 0) {
            System.out.println("[real-conv] Loaded " + priorTurns + " prior turns from session " + sessionId);
        }
        
        try (Scanner sc = new Scanner(System.in)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                    System.out.println("[real-conv] Goodbye!");
                    break;
                }
                if (line.equalsIgnoreCase("reset")) {
                    history.clear();
                    history.add(QwenChatTemplate.Message.system(
                        "You are MATRIX, a cognitive AI built on a deterministic neural architecture."
                    ));
                    System.out.println("[real-conv] History cleared.");
                    continue;
                }
                if (line.equalsIgnoreCase("info")) {
                    System.out.println("[real-conv] model=" + modelPath 
                        + " loaded=" + bridge.isLoaded() 
                        + " gpu=" + bridge.isGpuEnabled()
                        + " history=" + (history.size() - 1) + " turns"
                        + " lastTokens=" + bridge.lastGeneratedTokens());
                    continue;
                }
                
                history.add(QwenChatTemplate.Message.user(line));
                
                String reply = bridge.chat(line, MAX_NEW_TOKENS);
                if (reply == null || reply.isBlank()) {
                    reply = "[model returned empty]";
                }
                System.out.println("[MATRIX] " + reply);
                System.out.println();
                
                history.add(QwenChatTemplate.Message.assistant(reply));
                
                if (history.size() > 2 * MAX_HISTORY_TURNS + 1) {
                    List<QwenChatTemplate.Message> trimmed = new ArrayList<>();
                    trimmed.add(history.get(0));
                    for (int i = history.size() - 2 * MAX_HISTORY_TURNS; i < history.size(); i++) {
                        trimmed.add(history.get(i));
                    }
                    history = trimmed;
                }
                
                recordTurn(writer, sessionId, "user", line, System.nanoTime() - sessionStartNs);
                recordTurn(writer, sessionId, "assistant", reply, System.nanoTime() - sessionStartNs);
            }
        }
        
        try { writer.close(); } catch (IOException e) { /* ignore */ }
        bridge.close();
        System.out.println("[real-conv] Session " + sessionId + " recorded to " + sessionFile);
    }
    
    /**
     * W393: Load prior conversation history for a session from NDJSON files.
     */
    private static int loadPriorHistory(String sessionId, List<QwenChatTemplate.Message> history) {
        Path dataDir = Paths.get(DEFAULT_DATA_DIR);
        if (!Files.exists(dataDir)) return 0;
        
        Path sessionFile = dataDir.resolve(sessionId + ".ndjson");
        if (!Files.exists(sessionFile)) return 0;
        
        int loaded = 0;
        List<String> lines = new ArrayList<>();
        try (Stream<String> stream = Files.lines(sessionFile)) {
            stream.filter(line -> !line.isBlank()).forEach(lines::add);
        } catch (IOException e) {
            System.err.println("[real-conv] Failed to load prior history: " + e.getMessage());
            return 0;
        }
        
        // Only keep last MAX_HISTORY_TURNS*2 lines (user+assistant pairs)
        int startIdx = Math.max(0, lines.size() - MAX_HISTORY_TURNS * 2);
        for (int i = startIdx; i < lines.size(); i++) {
            try {
                String role = extractJsonField(lines.get(i), "role");
                String content = extractJsonField(lines.get(i), "content");
                if (role != null && content != null && !content.isEmpty()) {
                    QwenChatTemplate.Role r = QwenChatTemplate.Role.valueOf(role.toUpperCase());
                    history.add(new QwenChatTemplate.Message(r, content));
                    loaded++;
                }
            } catch (Exception e) {
                // Skip malformed lines
            }
        }
        return loaded;
    }
    
    /**
     * Extract a string field from simple JSON like {"key":"value"}.
     */
    private static String extractJsonField(String json, String fieldName) {
        String needle = "\"" + fieldName + "\":\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int start = idx + needle.length();
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
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
    
    /**
     * W393: List recent NDJSON sessions sorted by modification time.
     */
    public static List<String> listRecentSessions(int max) {
        List<String> sessions = new ArrayList<>();
        Path dataDir = Paths.get(DEFAULT_DATA_DIR);
        if (!Files.exists(dataDir)) return sessions;
        
        try (Stream<Path> stream = Files.list(dataDir)) {
            List<Path> files = new ArrayList<>();
            stream
                .filter(p -> p.toString().endsWith(".ndjson"))
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .forEach(files::add);
            
            files.sort((a, b) -> {
                try {
                    return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                } catch (IOException e) {
                    return 0;
                }
            });
            
            for (int i = 0; i < Math.min(max, files.size()); i++) {
                sessions.add(files.get(i).getFileName().toString().replace(".ndjson", ""));
            }
        } catch (IOException e) {
            // ignore
        }
        return sessions;
    }
    
    /**
     * Record a single conversation turn as NDJSON.
     */
    private static void recordTurn(BufferedWriter writer, String sessionId,
                                     String role, String content, long elapsedNs) {
        try {
            String ts = Instant.now().toString();
            String escaped = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
            String line = String.format(
                "{\"conversationId\":\"%s\",\"role\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\",\"elapsedNs\":%d}\n",
                sessionId, role, escaped, ts, elapsedNs);
            writer.write(line);
            writer.flush();
        } catch (IOException e) {
            System.err.println("[real-conv] Failed to record turn: " + e.getMessage());
        }
    }
    
    private static String generateSessionId() {
        return "cli-" + System.currentTimeMillis();
    }
}
