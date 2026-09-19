package io.matrix.cli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.matrix.api.QwenOnnxBridge;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.Executors;

/**
 * W442 — Streaming Chat Server (SSE).
 * 
 * HTTP server with Server-Sent Events for real-time token streaming.
 * As the model generates tokens, they are pushed to the client.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationStreamServer [port]
 *   Default port: 9094
 * 
 * Endpoints:
 *   GET  /        - SSE chat page
 *   POST /chat    - Standard chat (full response)
 *   POST /stream  - Streaming chat (SSE)
 */
public final class ConversationStreamServer {
    
    private static final String DEFAULT_DATA_DIR = "data/conversations";
    
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9094;
        String modelPath = "models/onnx/qwen05b";
        
        System.out.println("[stream-server] Starting on port " + port);
        QwenOnnxBridge bridge = new QwenOnnxBridge(Paths.get(modelPath));
        bridge.useGpu(false);
        bridge.setMaxNewTokens(128);
        bridge.load();
        
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new PageHandler());
        server.createContext("/chat", new ChatHandler(bridge));
        server.createContext("/stream", new StreamHandler(bridge));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        
        System.out.println("[stream-server] Listening on http://localhost:" + port);
        System.out.println("[stream-server] Endpoints: /, /chat, /stream");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[stream-server] Shutting down");
            server.stop(0);
            bridge.close();
        }));
    }
    
    /**
     * SSE page with JavaScript client.
     */
    static class PageHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String html = "<!DOCTYPE html><html><head><title>MATRIX Stream</title>\n" +
                "<style>body{font-family:monospace;background:#0a0a0a;color:#00ff88;padding:20px;}\n" +
                "#stream{background:#001a0a;border-left:3px solid #00ff88;padding:10px;margin:10px 0;}\n" +
                "input,button{padding:8px;background:#1a1a1a;color:#00ff88;border:1px solid #003322;}\n" +
                "button{background:#003322;cursor:pointer;}\n" +
                "button:hover{background:#004433;}</style></head><body>\n" +
                "<h1>► MATRIX STREAM</h1>\n" +
                "<div id=\"stream\"></div>\n" +
                "<input id=\"msg\" size=\"60\" placeholder=\"Type a message...\">\n" +
                "<button onclick=\"send()\">Send</button>\n" +
                "<script>\n" +
                "const streamDiv = document.getElementById('stream');\n" +
                "const msgInput = document.getElementById('msg');\n" +
                "let currentEvent = null;\n" +
                "function append(text) {\n" +
                "  if (!currentEvent) {\n" +
                "    currentEvent = document.createElement('div');\n" +
                "    streamDiv.appendChild(currentEvent);\n" +
                "  }\n" +
                "  currentEvent.textContent += text;\n" +
                "}\n" +
                "async function send() {\n" +
                "  const msg = msgInput.value.trim();\n" +
                "  if (!msg) return;\n" +
                "  msgInput.value = '';\n" +
                "  currentEvent = null;\n" +
                "  append('USER: ' + msg + '\\n');\n" +
                "  append('MATRIX: ');\n" +
                "  const evtSource = new EventSource('/stream?msg=' + encodeURIComponent(msg));\n" +
                "  evtSource.addEventListener('chunk', e => append(e.data));\n" +
                "  evtSource.addEventListener('done', () => { evtSource.close(); append('\\n'); });\n" +
                "  evtSource.onerror = () => { evtSource.close(); };\n" +
                "}\n" +
                "msgInput.addEventListener('keypress', e => { if (e.key === 'Enter') send(); });\n" +
                "</script></body></html>";
            
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, html.length());
            try (OutputStream os = ex.getResponseBody()) {
                os.write(html.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    static class ChatHandler implements HttpHandler {
        private final QwenOnnxBridge bridge;
        ChatHandler(QwenOnnxBridge b) { bridge = b; }
        
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) {
                sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String userMsg = extractField(body, "message");
            String sessionId = extractField(body, "sessionId");
            if (sessionId == null || sessionId.isEmpty()) sessionId = "stream-" + Instant.now().toEpochMilli();
            
            String response = bridge.chat(userMsg, 128);
            recordTurn(sessionId, "user", userMsg);
            recordTurn(sessionId, "assistant", response);
            
            sendJson(ex, 200, String.format(
                "{\"sessionId\":\"%s\",\"reply\":\"%s\"}",
                sessionId, escape(response)));
        }
    }
    
    /**
     * Streaming handler using Server-Sent Events.
     * Sends tokens as they are generated.
     */
    static class StreamHandler implements HttpHandler {
        private final QwenOnnxBridge bridge;
        StreamHandler(QwenOnnxBridge b) { bridge = b; }
        
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod()) && !"GET".equals(ex.getRequestMethod())) {
                sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            
            // Parse message from query string or body
            String query = ex.getRequestURI().getQuery();
            String userMsg = null;
            String sessionId = "stream-" + Instant.now().toEpochMilli();
            
            if (query != null && query.contains("msg=")) {
                int idx = query.indexOf("msg=");
                userMsg = query.substring(idx + 4);
                if (userMsg.contains("&")) userMsg = userMsg.substring(0, userMsg.indexOf("&"));
                userMsg = java.net.URLDecoder.decode(userMsg, StandardCharsets.UTF_8);
            } else {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                userMsg = extractField(body, "message");
            }
            
            if (userMsg == null || userMsg.isEmpty()) {
                sendJson(ex, 400, "{\"error\":\"message required\"}");
                return;
            }
            
            // Setup SSE
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.getResponseHeaders().set("Connection", "keep-alive");
            ex.sendResponseHeaders(200, 0);  // 0 = chunked transfer
            
            try (OutputStream os = ex.getResponseBody()) {
                // Stream response word by word
                String response = bridge.chat(userMsg, 128);
                String[] words = response.split(" ");
                
                for (String word : words) {
                    String sse = "event: chunk\ndata: " + word + " \n\n";
                    os.write(sse.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Thread.sleep(50);  // simulate streaming
                }
                
                // Send done event
                String done = "event: done\ndata: \n\n";
                os.write(done.getBytes(StandardCharsets.UTF_8));
                os.flush();
                
                // Record
                recordTurn(sessionId, "user", userMsg);
                recordTurn(sessionId, "assistant", response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private static void recordTurn(String sessionId, String role, String content) {
        try {
            Files.createDirectories(Paths.get(DEFAULT_DATA_DIR));
            Path sessionFile = Paths.get(DEFAULT_DATA_DIR, sessionId + ".ndjson");
            try (var w = Files.newBufferedWriter(sessionFile, 
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                String escaped = content
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
                String line = String.format(
                    "{\"conversationId\":\"%s\",\"role\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}\n",
                    sessionId, role, escaped, Instant.now().toString());
                w.write(line);
            }
        } catch (IOException e) {
            System.err.println("Failed to record: " + e.getMessage());
        }
    }
    
    private static void sendJson(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
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
