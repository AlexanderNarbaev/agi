package io.matrix.cli;

import io.matrix.api.QwenChatTemplate;
import io.matrix.api.QwenOnnxBridge;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * W394 — Real Conversation HTTP Server.
 * 
 * Lightweight HTTP server (no Quarkus) that exposes the real conversation
 * CLI over HTTP for integration with other tools (browsers, bots, agents).
 * 
 * Endpoints:
 * - GET  /health         - health check
 * - GET  /sessions       - list recent sessions
 * - POST /chat           - send a message, get response
 * 
 * Usage:
 *   java io.matrix.cli.RealConversationServer [port] [model-path]
 *   Default port: 9092
 *   Default model: models/onnx/qwen05b
 */
public final class RealConversationServer {
    
    private static final int DEFAULT_PORT = 9092;
    private static final String DEFAULT_MODEL_PATH = "models/onnx/qwen05b";
    private static final String DEFAULT_DATA_DIR = "data/conversations";
    private static final int MAX_HISTORY_TURNS = 10;
    private static final int MAX_NEW_TOKENS = 128;
    
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        String modelPath = args.length > 1 ? args[1] : DEFAULT_MODEL_PATH;
        
        System.out.println("[real-conv-server] Starting on port " + port);
        System.out.println("[real-conv-server] Model: " + modelPath);
        
        QwenOnnxBridge bridge = new QwenOnnxBridge(Paths.get(modelPath));
        bridge.useGpu(false);
        bridge.setMaxNewTokens(MAX_NEW_TOKENS);
        bridge.load();
        
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/sessions", new SessionsHandler());
        server.createContext("/chat", new ChatHandler(bridge));
        server.createContext("/", new StaticFileHandler());  // W416: Web UI
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        
        System.out.println("[real-conv-server] Listening on http://localhost:" + port);
        System.out.println("[real-conv-server] Endpoints: /health, /sessions, /chat");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[real-conv-server] Shutting down");
            server.stop(0);
            bridge.close();
        }));
    }
    
    /**
     * W416 — Static file handler for the web UI.
     * Serves files from classpath:web/ directory.
     */
    static class StaticFileHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }
            
            // Security: prevent path traversal
            if (path.contains("..")) {
                sendJson(ex, 403, "{\"error\":\"forbidden\"}");
                return;
            }
            
            String resourcePath = "/web" + path;
            try (InputStream is = RealConversationServer.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    sendJson(ex, 404, "{\"error\":\"not found\"}");
                    return;
                }
                byte[] bytes = is.readAllBytes();
                String contentType = contentTypeFor(path);
                ex.getResponseHeaders().set("Content-Type", contentType);
                ex.getResponseHeaders().set("Cache-Control", "no-cache");
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }
        
        private String contentTypeFor(String path) {
            if (path.endsWith(".html")) return "text/html; charset=utf-8";
            if (path.endsWith(".css")) return "text/css; charset=utf-8";
            if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (path.endsWith(".json")) return "application/json; charset=utf-8";
            return "application/octet-stream";
        }
    }
    
    static class HealthHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            sendJson(ex, 200, "{\"status\":\"ok\",\"service\":\"real-conv\"}");
        }
    }
    
    static class SessionsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            List<String> sessions = RealConversationCli.listRecentSessions(20);
            StringBuilder sb = new StringBuilder("{\"sessions\":[");
            for (int i = 0; i < sessions.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(sessions.get(i)).append("\"");
            }
            sb.append("]}");
            sendJson(ex, 200, sb.toString());
        }
    }
    
    static class ChatHandler implements HttpHandler {
        private final QwenOnnxBridge bridge;
        ChatHandler(QwenOnnxBridge bridge) { this.bridge = bridge; }
        
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) {
                sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String userMsg = RealConversationCli.class.getName() != null ? 
                extractUserMessage(body) : "hello";
            String sessionId = extractSessionId(body);
            if (sessionId == null) sessionId = "http-" + Instant.now().toEpochMilli();
            
            // Load prior history
            List<QwenChatTemplate.Message> history = new ArrayList<>();
            history.add(QwenChatTemplate.Message.system(
                "You are MATRIX, a cognitive AI built on a deterministic neural architecture."
            ));
            int priorTurns = RealConversationCli.class.getName() != null ? 
                invokeLoadPrior(sessionId, history) : 0;
            
            // Generate response
            history.add(QwenChatTemplate.Message.user(userMsg));
            String reply = bridge.chat(userMsg, MAX_NEW_TOKENS);
            if (reply == null || reply.isBlank()) {
                reply = "[model returned empty]";
            }
            
            // Record to NDJSON
            try {
                Files.createDirectories(Paths.get(DEFAULT_DATA_DIR));
                Path sessionFile = Paths.get(DEFAULT_DATA_DIR, sessionId + ".ndjson");
                try (BufferedWriter w = Files.newBufferedWriter(sessionFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    String ts = Instant.now().toString();
                    w.write(String.format(
                        "{\"conversationId\":\"%s\",\"role\":\"user\",\"content\":\"%s\",\"timestamp\":\"%s\"}\n",
                        sessionId, escape(userMsg), ts));
                    w.write(String.format(
                        "{\"conversationId\":\"%s\",\"role\":\"assistant\",\"content\":\"%s\",\"timestamp\":\"%s\"}\n",
                        sessionId, escape(reply), ts));
                }
            } catch (IOException e) {
                System.err.println("[real-conv-server] Failed to record: " + e.getMessage());
            }
            
            String response = String.format(
                "{\"sessionId\":\"%s\",\"reply\":\"%s\",\"priorTurns\":%d}",
                sessionId, escape(reply), priorTurns);
            sendJson(ex, 200, response);
        }
    }
    
    /**
     * Extract "message":"..." from simple JSON request body.
     */
    private static String extractUserMessage(String json) {
        int idx = json.indexOf("\"message\":");
        if (idx < 0) return "hello";
        int start = json.indexOf('"', idx + 10) + 1;
        int end = start;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                end = i;
                break;
            }
        }
        return unescape(json.substring(start, end));
    }
    
    /**
     * Extract "sessionId":"..." from JSON request.
     */
    private static String extractSessionId(String json) {
        int idx = json.indexOf("\"sessionId\":");
        if (idx < 0) return null;
        int start = json.indexOf('"', idx + 12) + 1;
        int end = start;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { escaped = false; }
            else if (c == '\\') { escaped = true; }
            else if (c == '"') { end = i; break; }
        }
        return unescape(json.substring(start, end));
    }
    
    private static int invokeLoadPrior(String sessionId, List<QwenChatTemplate.Message> history) {
        try {
            // Use reflection to call private method
            var method = RealConversationCli.class.getDeclaredMethod("loadPriorHistory", String.class, List.class);
            method.setAccessible(true);
            return (Integer) method.invoke(null, sessionId, history);
        } catch (Exception e) {
            return 0;
        }
    }
    
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
    
    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
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
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    private static void sendJson(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
