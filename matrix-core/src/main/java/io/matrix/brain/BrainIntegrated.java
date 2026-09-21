package io.matrix.brain;

import io.matrix.autonomy.AutonomyEngine;
import io.matrix.knowledge.SimpleKnowledgeBase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.matrix.lifecycle.ImpulseScheduler;
import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.ethics.EthicalFilter;

/**
 * W481 — Integrated Brain Runner.
 * 
 * Combines all brain components into a single process:
 * 1. Loads Qwen2.5-0.5B model
 * 2. Initializes knowledge base
 * 3. Starts autonomy engine (self-initiating cycles)
 * 4. Starts HTTP server (for external access)
 * 5. Connects autonomy cycles to brain through RAG
 * 
 * Usage:
 *   java io.matrix.brain.BrainIntegrated [model-path] [port]
 *   Default model: models/onnx/qwen05b
 *   Default port: 9100
 *   Default duration: 5 minutes
 * 
 * This is the FULL REAL BRAIN running.
 */
public final class BrainIntegrated {
    
    public static void main(String[] args) throws Exception {
        String modelPath = args.length > 0 ? args[0] : "models/onnx/qwen05b";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9100;
        int durationMin = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        
        System.out.println("===== MATRIX Brain Integrated =====");
        System.out.println("Model: " + modelPath);
        System.out.println("Port: " + port);
        System.out.println("Duration: " + durationMin + " minutes");
        System.out.println();
        
        // Setup knowledge base
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        System.out.println("Knowledge base: " + kb.size() + " documents");
        
        // Setup RAG-enhanced brain
        LlmBrainLoopRag rag = new LlmBrainLoopRag(modelPath, kb);
        System.out.println("Brain loaded with RAG");
        
        // Setup scheduler
        ConjugateBudgeter budgeter = new ConjugateBudgeter();
        EthicalFilter ethics = new EthicalFilter();
        ImpulseScheduler scheduler = new ImpulseScheduler(budgeter, ethics);
        
        // Start autonomy engine
        AutonomyEngine autonomy = new AutonomyEngine(rag, scheduler);
        autonomy.start();
        System.out.println("Autonomy engine started (self-initiating cycles)");
        
        // Start HTTP server
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress(port), 0);
        server.createContext("/health", new HttpHandler() {
            public void handle(com.sun.net.httpserver.HttpExchange ex) throws java.io.IOException {
                String body = "{\"status\":\"ok\",\"service\":\"brain-integrated\"}";
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(200, body.length());
                try (java.io.OutputStream os = ex.getResponseBody()) {
                    os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        });
        server.createContext("/chat", new HttpHandler() {
            public void handle(com.sun.net.httpserver.HttpExchange ex) throws java.io.IOException {
                if (!"POST".equals(ex.getRequestMethod())) {
                    ex.sendResponseHeaders(405, 0);
                    return;
                }
                String body = new String(ex.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                String message = extractField(body, "message");
                if (message == null) message = "Hello";
                
                BrainCycle.CycleResult result = rag.cycle(message);
                
                String response = "{\"reply\":\"" + escape(result.reply()) + "\"," +
                    "\"confidence\":" + result.confidence() + "," +
                    "\"duration_ms\":" + result.durationMs() + "}";
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(200, response.length());
                try (java.io.OutputStream os = ex.getResponseBody()) {
                    os.write(response.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        });
        server.start();
        System.out.println("HTTP server started on port " + port);
        System.out.println();
        System.out.println("Brain is LIVE. Self-initiating cycles every 30s/120s/300s.");
        System.out.println("Press Ctrl+C to stop.");
        
        // Run for the specified duration
        Thread.sleep(durationMin * 60L * 1000L);
        
        System.out.println();
        System.out.println("===== Brain Run Summary =====");
        System.out.println("Total cycles: " + autonomy.getCycleCount());
        System.out.println("Total reflections: " + autonomy.getTotalReflections());
        String last = autonomy.getLastReflection();
        if (last != null) {
            System.out.println("Last reflection: " + 
                (last.length() > 200 ? last.substring(0, 200) + "..." : last));
        }
        
        autonomy.stop();
        server.stop(0);
        rag.close();
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
