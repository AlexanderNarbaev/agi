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
 * W499 — Production Brain HTTP Server.
 * 
 * Complete brain server with:
 * - /health: health check
 * - /chat: send message, get response (with RAG)
 * - /learn: trigger learning from conversations
 * - /stats: brain statistics
 * - /knowledge: search knowledge base
 * 
 * Usage:
 *   java io.matrix.brain.BrainHttpServer [port] [model-path]
 *   Default port: 9200
 *   Default model: models/onnx/qwen05b
 */
public final class BrainHttpServer {
    
    private static final String DEFAULT_MODEL = "models/onnx/qwen05b";
    private static final int DEFAULT_PORT = 9200;
    
    private final LlmBrainLoopRag brain;
    private final SimpleKnowledgeBase knowledgeBase;
    private final ConversationLearner learner;
    private final int port;
    
    public BrainHttpServer(int port, String modelPath) {
        this.port = port;
        this.knowledgeBase = new SimpleKnowledgeBase();
        this.brain = new LlmBrainLoopRag(modelPath, knowledgeBase);
        this.learner = new ConversationLearner(knowledgeBase, Paths.get("data/conversations"));
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
        System.out.println("  GET /health - Health check");
        System.out.println("  POST /chat - Send message (JSON: {\"message\": \"...\"})");
        System.out.println("  POST /learn - Learn from conversations");
        BrainCycle.CycleResult r = brain.cycle("test");
        System.out.println("  GET /stats - Brain statistics");
        System.out.println("  GET /knowledge?q=query - Search KB");
        System.out.println();
        System.out.println("Brain ready. KB: " + knowledgeBase.size() + " docs");
    }
    
    // ====== HTTP Handlers ======
    
    class HealthHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            sendJson(ex, 200, "{\"status\":\"ok\",\"version\":\"1.0\"}");
        }
    }
    
    class RootHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String html = """
                <!DOCTYPE html>
                <html><head><title>MATRIX Brain</title>
                <style>
                body { font-family: monospace; background: #0a0a0a; color: #00ff88; padding: 20px; }
                h1 { color: #00ffff; }
                pre { background: #111; padding: 10px; }
                </style></head>
                <body>
                <h1>MATRIX Brain Server</h1>
                <p>Endpoints:</p>
                <ul>
                <li><a href="/health">/health</a> - Health check</li>
                <li><a href="/stats">/stats</a> - Brain statistics</li>
                <li><a href="/knowledge?q=matrix">/knowledge?q=matrix</a> - Search KB</li>
                <li>POST /chat {"message": "..."} - Talk to brain</li>
                <li>POST /learn - Learn from conversations</li>
                </ul>
                <h2>Test Chat</h2>
                <form id="chat"><input id="msg" placeholder="Ask..." size=50><button>Send</button></form>
                <pre id="out"></pre>
                <script>
                document.getElementById('chat').addEventListener('submit', async (e) => {
                    e.preventDefault();
                    const msg = document.getElementById('msg').value;
                    document.getElementById('out').textContent = 'Thinking...';
                    const r = await fetch('/chat', {method:'POST', headers:{'Content-Type':'application/json'},
                        body: JSON.stringify({message: msg})});
                    const j = await r.json();
                    document.getElementById('out').textContent = j.reply;
                });
                </script>
                </body></html>
                """;
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
            String response = String.format(
                "{\"reply\":\"%s\",\"confidence\":%.4f,\"duration_ms\":%d,\"accepted\":%s}",
                escape(result.reply()), result.confidence(), result.durationMs(), result.accepted());
            sendJson(ex, 200, response);
        }
    }
    
    class LearnHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) {
                sendJson(ex, 405, "{\"error\":\"POST only\"}");
                return;
            }
            int learned = learner.learnAll();
            sendJson(ex, 200, "{\"learned\":" + learned + ",\"kb_size\":" + knowledgeBase.size() + "}");
        }
    }
    
    class StatsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String stats = String.format(
                "{\"kb_size\":%d,\"model\":\"qwen2.5-0.5b\",\"status\":\"running\"}",
                knowledgeBase.size());
            sendJson(ex, 200, stats);
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
        
        // Keep alive
        Thread.currentThread().join();
    }
}
