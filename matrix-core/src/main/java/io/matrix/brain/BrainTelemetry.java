package io.matrix.brain;

import io.matrix.knowledge.SimpleKnowledgeBase;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * W508 — Brain Telemetry Endpoint.
 * 
 * Exposes Prometheus-compatible metrics for the brain:
 * - /metrics: Prometheus text format
 * - /health: health check
 * - /stats: JSON stats
 */
public final class BrainTelemetry {
    
    private static final int DEFAULT_PORT = 9201;
    
    private final SimpleKnowledgeBase knowledgeBase;
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final long startMs;
    
    public BrainTelemetry(SimpleKnowledgeBase kb) {
        this.knowledgeBase = kb;
        this.startMs = System.currentTimeMillis();
    }
    
    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(DEFAULT_PORT), 0);
        server.createContext("/metrics", new MetricsHandler());
        server.createContext("/health", new HealthHandler());
        server.createContext("/stats", new StatsHandler());
        server.setExecutor(null);
        server.start();
        
        System.out.println("[brain-telemetry] Started on port " + DEFAULT_PORT);
        System.out.println("[brain-telemetry] Endpoints: /metrics, /health, /stats");
    }
    
    class MetricsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("# HELP brain_total_queries Total brain queries\n");
            sb.append("# TYPE brain_total_queries counter\n");
            sb.append("brain_total_queries ").append(totalQueries.get()).append("\n");
            sb.append("# HELP brain_total_errors Total brain errors\n");
            sb.append("# TYPE brain_total_errors counter\n");
            sb.append("brain_total_errors ").append(totalErrors.get()).append("\n");
            sb.append("# HELP brain_kb_size Knowledge base size\n");
            sb.append("# TYPE brain_kb_size gauge\n");
            sb.append("brain_kb_size ").append(knowledgeBase.size()).append("\n");
            sb.append("# HELP brain_uptime_seconds Uptime in seconds\n");
            sb.append("# TYPE brain_uptime_seconds gauge\n");
            sb.append("brain_uptime_seconds ").append((System.currentTimeMillis() - startMs) / 1000).append("\n");
            
            String body = sb.toString();
            ex.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            ex.sendResponseHeaders(200, body.length());
            try (var os = ex.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    class HealthHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String body = "{\"status\":\"ok\",\"service\":\"brain-telemetry\"}";
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length());
            try (var os = ex.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    class StatsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            long uptime = (System.currentTimeMillis() - startMs) / 1000;
            String body = "{\"uptime_s\":" + uptime + 
                ",\"kb_size\":" + knowledgeBase.size() + 
                ",\"total_queries\":" + totalQueries.get() + 
                ",\"total_errors\":" + totalErrors.get() + "}";
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length());
            try (var os = ex.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    // Public accessors
    public long getTotalQueries() { return totalQueries.get(); }
    public long getTotalErrors() { return totalErrors.get(); }
    public SimpleKnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    
    public void incrementQueries() { totalQueries.incrementAndGet(); }
    public void incrementErrors() { totalErrors.incrementAndGet(); }
    
    public static void main(String[] args) throws Exception {
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        BrainTelemetry telemetry = new BrainTelemetry(kb);
        telemetry.start();
        
        System.out.println("[brain-telemetry] Running. Press Ctrl+C to stop.");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[brain-telemetry] Shutting down");
            System.exit(0);
        }));
        
        Thread.currentThread().join();
    }
}
