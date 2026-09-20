package io.matrix.brain;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.matrix.knowledge.SimpleKnowledgeBase;
import io.matrix.learning.ConversationLearner;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * W502 — Production Brain HTTP Server with Anti-Hallucination.
 */
public final class BrainHttpServer {
    
    private static final String DEFAULT_MODEL = "models/onnx/qwen05b";
    private static final int DEFAULT_PORT = 9200;
    
    private final LlmBrainLoopRag brain;
    private final SimpleKnowledgeBase knowledgeBase;
    private final ConversationLearner learner;
    private final ConfidenceFilter confidenceFilter;
    private final int port;
    
    public BrainHttpServer(int port, String modelPath) {
        this.port = port;
        this.knowledgeBase = new SimpleKnowledgeBase();
        this.brain = new LlmBrainLoopRag(modelPath, knowledgeBase);
        this.learner = new ConversationLearner(knowledgeBase, Paths.get("data/conversations"));
        this.confidenceFilter = new ConfidenceFilter(0.3);
    }
    
    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/chat", new ChatHandler());
        server.createContext("/learn", new LearnHandler());
        server.createContext("/stats", new StatsHandler());
        server.createContext("/knowledge", new KnowledgeHandler());
        server.createContext("/", new RootHandler());
        server.setExecutor(null);
        server.start();
        
        System.out.println("[brain-server] Started on port " + port);
        System.out.println("[brain-server] Endpoints:");
        System.out.println("  GET /health");
        System.out.println("  POST /chat");
        System.out.println("  POST /learn");
        System.out.println("  GET /stats");
        System.out.println("  GET /knowledge?q=query");
        System.out.println();
        System.out.println("Anti-hallucination: min confidence = " + confidenceFilter.getMinConfidence());
    }
    
    // ====== HTTP Handlers ======
    
    class HealthHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            sendJson(ex, 200, "{\"status\":\"ok\",\"version\":\"1.0\"}");
        }
    }
    
    class RootHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String html = "<!DOCTYPE html>\n<html><head><title>MATRIX Brain</title>\n" +
                "<style>body{font-family:monospace;background:#0a0a0a;color:#00ff88;padding:20px;}\n" +
                "h1{color:#00ffff;}pre{background:#111;padding:10px;white-space:pre-wrap;}\n" +
                "button{padding:8px 16px;background:#003322;color:#00ff88;border:1px solid #00ff88;cursor:pointer;}\n" +
                "button:hover{background:#004433;}</style></head>\n<body>\n" +
                "<h1>MATRIX Brain Server</h1>\n" +
                "<p>Endpoints: /health, /chat, /learn, /stats, /knowledge?q=query</p>\n" +
                "<h2>Chat</h2>\n" +
                "<form id='chat'><input id='msg' placeholder='Ask the brain...' size=50><button>Send</button></form>\n" +
                "<pre id='out'></pre>\n" +
                "<script>\n" +
                "document.getElementById('chat').addEventListener('submit', async (e) => {\n" +
                "    e.preventDefault();\n" +
                "    const msg = document.getElementById('msg').value;\n" +
                "    document.getElementById('out').textContent = 'Thinking...';\n" +
                "    const r = await fetch('/chat', {method:'POST', headers:{'Content-Type':'application/json'},\n" +
                "        body: JSON.stringify({message: msg})});\n" +
                "    const j = await r.json();\n" +
                "    document.getElementById('out').textContent = 'Reply: ' + j.reply + '\\nConfidence: ' + \n" +
                "        (j.confidence*100).toFixed(1) + '%\\nDuration: ' + j.duration_ms + 'ms\\nAccepted: ' + j.accepted;\n" +
                "});\n" +
                "</script>\n</body></html>";
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, html.length());
            try (var os = ex.getResponseBody()) {
                os.write(html.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    class ChatHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) {
                sendJson(ex, 405, "{\"error\":\"POST only\"}");
                return;
            }
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String message = extractField(body, "message");
            if (message == null || message.isEmpty()) {
                sendJson(ex, 400, "{\"error\":\"message required\"}");
                return;
            }
            
            BrainCycle.CycleResult result = brain.cycle(message);
            
            String filtered = confidenceFilter.filter(result.reply(), result.confidence());
            if (filtered == null) {
                String refusal = confidenceFilter.rejectionMessage(result.confidence());
                sendJson(ex, 200, "{\"reply\":\"" + escape(refusal) + 
                    "\",\"confidence\":" + result.confidence() + 
                    ",\"duration_ms\":" + result.durationMs() + ",\"accepted\":false}");
                return;
            }
            
            sendJson(ex, 200, "{\"reply\":\"" + escape(result.reply()) + 
                "\",\"confidence\":" + result.confidence() + 
                ",\"duration_ms\":" + result.durationMs() + ",\"accepted\":true}");
        }
    }
    
    class LearnHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) {
                sendJson(ex, 405, "{\"error\":\"POST only\"}");
                return;
            }
            try {
                int learned = learner.learnAll();
                sendJson(ex, 200, "{\"learned\":" + learned + ",\"kb_size\":" + knowledgeBase.size() + "}");
            } catch (Exception e) {
                sendJson(ex, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }
    
    class StatsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            sendJson(ex, 200, "{\"kb_size\":" + knowledgeBase.size() + 
                ",\"model\":\"qwen2.5-0.5b\",\"status\":\"running\",\"min_confidence\":" + 
                confidenceFilter.getMinConfidence() + "}");
        }
    }
    
    class KnowledgeHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String query = extractField(ex.getRequestURI().getQuery(), "q");
            if (query == null || query.isEmpty()) {
                sendJson(ex, 400, "{\"error\":\"q param required\"}");
                return;
            }
            var results = knowledgeBase.retrieve(query, 3);
            StringBuilder sb = new StringBuilder("{\"results\":[");
            boolean first = true;
            for (var r : results) {
                if (!first) sb.append(",");
                sb.append("{\"id\":\"").append(r.doc().id())
                  .append("\",\"title\":\"").append(escape(r.doc().title()))
                  .append("\",\"score\":").append(r.score()).append("}");
                first = false;
            }
            sb.append("]}");
            sendJson(ex, 200, sb.toString());
        }
    }
    
    // ====== Utilities ======
    
    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, json.length());
        try (var os = ex.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
    
    private static String extractField(String json, String fieldName) {
        if (json == null) return null;
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
    
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        String modelPath = args.length > 1 ? args[1] : DEFAULT_MODEL;
        
        BrainHttpServer server = new BrainHttpServer(port, modelPath);
        server.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[brain-server] Shutting down");
            System.exit(0);
        }));
        
        Thread.currentThread().join();
    }
}
