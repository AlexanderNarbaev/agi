package io.matrix.brain;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.matrix.knowledge.SimpleKnowledgeBase;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * W479 — HTTP Server for the brain.
 * 
 * Exposes the LlmBrainLoopRag as an HTTP endpoint:
 * - POST /chat - Send a message, get response
 * - GET /health - Health check
 * - GET /info - Brain info
 * 
 * Usage:
 *   java io.matrix.brain.BrainServer [port] [model-path]
 *   Default port: 9100
 */
public final class BrainServer {
    
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9100;
        String modelPath = args.length > 1 ? args[1] : "models/onnx/qwen05b";
        
        // Setup brain with RAG
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        LlmBrainLoopRag brain = new LlmBrainLoopRag(modelPath, kb);
        
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/info", new InfoHandler(kb));
        server.createContext("/chat", new ChatHandler(brain));
        server.setExecutor(null);
        server.start();
        
        System.out.println("[brain-server] Started on http://localhost:" + port);
        System.out.println("[brain-server] Model: " + modelPath);
        System.out.println("[brain-server] KB: " + kb.size() + " documents");
        System.out.println("[brain-server] Endpoints: /health, /info, /chat");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[brain-server] Shutting down");
            server.stop(0);
            brain.close();
        }));
    }
    
    static class HealthHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String body = "{\"status\":\"ok\",\"service\":\"brain-server\"}";
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length());
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    static class InfoHandler implements HttpHandler {
        private final SimpleKnowledgeBase kb;
        InfoHandler(SimpleKnowledgeBase kb) { this.kb = kb; }
        public void handle(HttpExchange ex) throws IOException {
            String body = "{\"kb_size\":" + kb.size() + ",\"model\":\"qwen2.5-0.5b\"}";
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length());
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    static class ChatHandler implements HttpHandler {
        private final LlmBrainLoopRag brain;
        ChatHandler(LlmBrainLoopRag b) { this.brain = b; }
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, 0);
                return;
            }
            
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String message = extractField(body, "message");
            if (message == null) {
                message = "Hello";
            }
            
            LlmBrainLoopService.CycleResult result = brain.cycle(message);
            
            String response = "{\"message\":\"" + escape(message) + "\"," +
                "\"reply\":\"" + escape(result.reply()) + "\"," +
                "\"accepted\":" + result.accepted() + "," +
                "\"confidence\":" + result.confidence() + "," +
                "\"duration_ms\":" + result.durationMs() + "}";
            
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, response.length());
            try (OutputStream os = ex.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
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
    
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
