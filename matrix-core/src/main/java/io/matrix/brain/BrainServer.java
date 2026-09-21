package io.matrix.brain;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.matrix.knowledge.SimpleKnowledgeBase;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * W483 — HTTP Server for the brain with web UI.
 * 
 * Endpoints:
 * - GET / - Web UI (HTML)
 * - GET /health - Health check
 * - GET /info - Brain info  
 * - POST /chat - Real Qwen response with RAG
 * 
 * Usage:
 *   java io.matrix.brain.BrainServer [port] [model-path]
 */
public final class BrainServer {
    
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9100;
        String modelPath = args.length > 1 ? args[1] : "models/onnx/qwen05b";
        
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        LlmBrainLoopRag brain = new LlmBrainLoopRag(modelPath, kb);
        
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new UiHandler());
        server.createContext("/health", new HealthHandler());
        server.createContext("/info", new InfoHandler(kb));
        server.createContext("/chat", new ChatHandler(brain));
        server.setExecutor(null);
        server.start();
        
        System.out.println("[brain-server] Started on http://localhost:" + port);
        System.out.println("[brain-server] Web UI at /");
        System.out.println("[brain-server] Model: " + modelPath);
        System.out.println("[brain-server] KB: " + kb.size() + " documents");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[brain-server] Shutting down");
            server.stop(0);
            brain.close();
        }));
    }
    
    static class UiHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"/".equals(ex.getRequestURI().getPath())) {
                ex.sendResponseHeaders(404, 0);
                return;
            }
            try (InputStream is = BrainServer.class.getResourceAsStream("/brain/index.html")) {
                if (is == null) {
                    ex.sendResponseHeaders(404, 0);
                    return;
                }
                byte[] bytes = is.readAllBytes();
                ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }
    }
    
    static class HealthHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String body = "{\"status\":\"ok\",\"service\":\"brain\"}";
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
            if (message == null) message = "Hello";
            
            BrainCycle.CycleResult result = brain.cycle(message);
            
            String response = "{\"reply\":\"" + escape(result.reply()) + "\"," +
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
