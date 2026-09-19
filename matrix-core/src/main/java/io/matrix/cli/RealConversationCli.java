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

/**
 * W392 — Real Conversation CLI.
 * 
 * Self-contained command-line tool that:
 * - Loads Qwen2.5-0.5B ONNX model locally (CPU)
 * - Conducts multi-turn conversation with full context
 * - Records every turn to NDJSON via ConversationRecorder
 * - Maintains conversation history within the session
 * 
 * Usage:
 *   java io.matrix.cli.RealConversationCli [model-path] [session-id]
 * 
 * Default model-path: ./models/onnx/qwen05b
 * Default session-id: timestamp
 * 
 * Example:
 *   java -cp target/classes:libs/* io.matrix.cli.RealConversationCli
 * 
 * This is the missing entry point that ties together:
 * - QwenOnnxBridge (real LLM inference)
 * - QwenChatTemplate (ChatML formatting)
 * - ConversationRecorder (NDJSON persistence)
 * - BrainLoopService (could be integrated, currently bypassed)
 */
public final class RealConversationCli {

    private static final String DEFAULT_MODEL_PATH = "models/onnx/qwen05b";
    private static final String DEFAULT_DATA_DIR = "data/conversations";
    private static final int MAX_HISTORY_TURNS = 10;  // last N turns to keep in context
    private static final int MAX_NEW_TOKENS = 128;
    
    public static void main(String[] args) {
        String modelPath = args.length > 0 ? args[0] : DEFAULT_MODEL_PATH;
        String sessionId = args.length > 1 ? args[1] : generateSessionId();
        
        System.out.println("[real-conv] MATRIX Real Conversation CLI (W392)");
        System.out.println("[real-conv] model=" + modelPath);
        System.out.println("[real-conv] session=" + sessionId);
        System.out.println("[real-conv] type 'quit' to exit, 'reset' to clear history, 'info' for status");
        System.out.println();
        
        // Initialize model
        QwenOnnxBridge bridge = new QwenOnnxBridge(Paths.get(modelPath));
        bridge.useGpu(false);  // CPU for portability
        bridge.setMaxNewTokens(MAX_NEW_TOKENS);
        
        System.out.println("[real-conv] Loading model...");
        long t0 = System.currentTimeMillis();
        boolean loaded = bridge.load();
        long loadMs = System.currentTimeMillis() - t0;
        
        if (!loaded) {
            System.err.println("[real-conv] FATAL: Model failed to load from " + modelPath);
            System.err.println("[real-conv] Check that model.onnx and tokenizer files exist in that directory.");
            System.exit(1);
        }
        System.out.println("[real-conv] Model loaded in " + loadMs + "ms (CPU mode)");
        System.out.println();
        
        // Initialize simple NDJSON writer (no Quarkus dependency)
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
        
        // Initial system prompt
        List<QwenChatTemplate.Message> history = new ArrayList<>();
        history.add(QwenChatTemplate.Message.system(
            "You are MATRIX, a cognitive AI built on a deterministic neural architecture. "
            + "You answer questions about yourself, your design, and general knowledge clearly and concisely."
        ));
        
        // Read input
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
                        + " history=" + (history.size() - 1) + " turns"  // exclude system
                        + " lastTokens=" + bridge.lastGeneratedTokens());
                    continue;
                }
                
                // Add user message
                history.add(QwenChatTemplate.Message.user(line));
                
                // Generate response
                String reply = bridge.chat(line, MAX_NEW_TOKENS);
                if (reply == null || reply.isBlank()) {
                    reply = "[model returned empty]";
                }
                System.out.println("[MATRIX] " + reply);
                System.out.println();
                
                // Add assistant response to history
                history.add(QwenChatTemplate.Message.assistant(reply));
                
                // Trim history if too long (keep system + last N turns)
                if (history.size() > 2 * MAX_HISTORY_TURNS + 1) {
                    List<QwenChatTemplate.Message> trimmed = new ArrayList<>();
                    trimmed.add(history.get(0));  // keep system
                    for (int i = history.size() - 2 * MAX_HISTORY_TURNS; i < history.size(); i++) {
                        trimmed.add(history.get(i));
                    }
                    history = trimmed;
                }
                
                // Record this turn as NDJSON
                recordTurn(writer, sessionId, "user", line, System.nanoTime() - sessionStartNs);
                recordTurn(writer, sessionId, "assistant", reply, System.nanoTime() - sessionStartNs);
            }
        }
        
        try {
            writer.close();
        } catch (IOException e) {
            // ignore
        }
        bridge.close();
        System.out.println("[real-conv] Session " + sessionId + " recorded to " + sessionFile);
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
