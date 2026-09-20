package io.matrix.brain;

import io.matrix.autonomy.AutonomyEngine;
import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.ethics.EthicalFilter;
import io.matrix.knowledge.SimpleKnowledgeBase;
import io.matrix.lifecycle.ImpulseScheduler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * W493 — Production Brain.
 * 
 * The final brain that runs everything together:
 * - LlmBrainLoopRag (RAG + LLM)
 * - ConfidenceFilter (anti-hallucination)
 * - AutonomyEngine (self-initiating cycles)
 * - HTTP server (for external access)
 * 
 * This is the COMPLETE REAL BRAIN.
 */
public final class BrainProduction {
    
    private final LlmBrainLoopRag brain;
    private final ConfidenceFilteredBrain filteredBrain;
    private final SimpleKnowledgeBase knowledgeBase;
    private final ConfidenceFilter filter;
    private final AutonomyEngine autonomy;
    private final HttpServer server;
    private final long startMs;
    
    public BrainProduction(String modelPath, int port) throws IOException {
        this.knowledgeBase = new SimpleKnowledgeBase();
        this.brain = new LlmBrainLoopRag(modelPath, knowledgeBase);
        this.filter = new ConfidenceFilter(0.3);
        this.filteredBrain = new ConfidenceFilteredBrain(brain, filter);
        
        // Autonomy engine
        ConjugateBudgeter budgeter = new ConjugateBudgeter();
        EthicalFilter ethics = new EthicalFilter();
        ImpulseScheduler scheduler = new ImpulseScheduler(budgeter, ethics);
        this.autonomy = new AutonomyEngine(filteredBrain, scheduler);
        
        // HTTP server
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/chat", new ChatHandler(filteredBrain));
        server.createContext("/stats", new StatsHandler());
        server.setExecutor(null);
        
        this.startMs = System.currentTimeMillis();
    }
    
    public void start() {
        autonomy.start();
        server.start();
        System.out.println("[brain] LIVE");
        System.out.println("[brain] HTTP: " + server.getAddress());
        System.out.println("[brain] KB: " + knowledgeBase.size() + " documents");
        System.out.println("[brain] Autonomy: idle=30s, curiosity=120s, integrity=300s");
        System.out.println("[brain] Anti-hallucination: min confidence = " + 
            String.format("%.0f%%", filter.getMinConfidence() * 100));
    }
    
    public void stop() {
        autonomy.stop();
        server.stop(0);
        brain.close();
    }
    
    public BrainCycle.CycleResult query(String input) {
        return filteredBrain.cycle(input);
    }
    
    // HTTP Handlers
    class HealthHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String body = "{\"status\":\"ok\",\"service\":\"brain-production\"}";
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length());
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    class ChatHandler implements HttpHandler {
        private final BrainCycle brain;
        ChatHandler(BrainCycle b) { this.brain = b; }
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
    
    class StatsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String body = "{" +
                "\"uptime_ms\":" + (System.currentTimeMillis() - startMs) + "," +
                "\"kb_size\":" + knowledgeBase.size() + "," +
                "\"autonomy_cycles\":" + autonomy.getCycleCount() + "," +
                "\"autonomy_reflections\":0" +
                "}";
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length());
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    // JSON helpers
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
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
               .replace("\n", "\\n").replace("\r", "\\r");
    }
    
    public static void main(String[] args) throws Exception {
        String modelPath = args.length > 0 ? args[0] : "models/onnx/qwen05b";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9200;
        
        BrainProduction brain = new BrainProduction(modelPath, port);
        brain.start();
        
        System.out.println("[brain] Press Ctrl+C to stop");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[brain] Shutting down");
            brain.stop();
        }));
        
        // Keep main thread alive
        Thread.currentThread().join();
    }
}
