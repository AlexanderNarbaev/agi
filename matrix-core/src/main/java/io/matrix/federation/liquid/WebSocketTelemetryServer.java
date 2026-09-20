package io.matrix.federation.liquid;

import java.io.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * W586 — WebSocket Telemetry Server.
 *
 * Streams real-time metrics to browser via HTTP.
 */
public final class WebSocketTelemetryServer {

    private final int port;
    private final FederationTelemetryV2 telemetry;
    private final CorridorHomeostat homeostat;
    private final Map<String, KineticModulator> modulators;
    private HttpServer server;
    private volatile boolean running;

    public WebSocketTelemetryServer(int port, FederationTelemetryV2 telemetry,
                                     CorridorHomeostat homeostat,
                                     Map<String, KineticModulator> modulators) {
        this.port = port;
        this.telemetry = telemetry;
        this.homeostat = homeostat;
        this.modulators = modulators;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/metrics", exchange -> {
            String json = buildMetricsJson();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, json.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes());
            }
        });

        server.createContext("/modulators", exchange -> {
            String json = buildModulatorsJson();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, json.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes());
            }
        });

        server.createContext("/homeostat", exchange -> {
            String json = buildHomeostatJson();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, json.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes());
            }
        });

        server.createContext("/health", exchange -> {
            String json = "{\"status\":\"ok\",\"service\":\"telemetry\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes());
            }
        });

        server.createContext("/", exchange -> {
            String html = buildDashboardHtml();
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, html.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(html.getBytes());
            }
        });

        server.setExecutor(null);
        server.start();
        running = true;
    }

    public void stop() {
        running = false;
        if (server != null) {
            server.stop(0);
        }
    }

    private String buildMetricsJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"counters\":{");
        boolean first = true;
        for (var entry : telemetry.getAllCounters().entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        sb.append("},\"gauges\":{");
        first = true;
        for (var entry : telemetry.getAllGauges().entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    private String buildModulatorsJson() {
        StringBuilder sb = new StringBuilder("{\"modulators\":[");
        boolean first = true;
        for (var entry : modulators.entrySet()) {
            if (!first) sb.append(",");
            sb.append("{\"id\":\"").append(entry.getKey()).append("\"")
              .append(",\"level\":").append(entry.getValue().getCurrentLevel())
              .append(",\"sensitivity\":").append(entry.getValue().getReceptorSensitivity())
              .append("}");
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildHomeostatJson() {
        StringBuilder sb = new StringBuilder("{\"corridors\":[");
        boolean first = true;
        for (CorridorHomeostat.Corridor corridor : homeostat.getAllCorridors()) {
            if (!first) sb.append(",");
            sb.append("{\"name\":\"").append(corridor.getName()).append("\"")
              .append(",\"value\":").append(corridor.getCurrentValue())
              .append(",\"state\":\"").append(corridor.getState()).append("\"")
              .append(",\"throttle\":").append(String.format("%.2f", corridor.getThrottleFactor()))
              .append("}");
            first = false;
        }
        sb.append("],\"violations\":").append(homeostat.getViolations().size()).append("}");
        return sb.toString();
    }

    private String buildDashboardHtml() {
        return "<!DOCTYPE html><html><head><title>MATRIX Telemetry</title>" +
            "<style>body{font-family:monospace;background:#0a0a0a;color:#00ff88;padding:20px;}" +
            "h1{color:#00ffff;}.card{background:#111;border:1px solid #333;padding:15px;margin:10px 0;}" +
            "table{border-collapse:collapse;width:100%;}td,th{border:1px solid #333;padding:8px;}</style></head>" +
            "<body><h1>MATRIX Telemetry Dashboard</h1>" +
            "<div class='card'><h2>Modulators</h2><div id='m'>Loading...</div></div>" +
            "<div class='card'><h2>Homeostat</h2><div id='h'>Loading...</div></div>" +
            "<script>setInterval(async()=>{" +
            "try{const[r1,r2]=await Promise.all([fetch('/modulators'),fetch('/homeostat')]);" +
            "const m=await r1.json(),h=await r2.json();" +
            "document.getElementById('m').innerHTML='<table><tr><th>ID</th><th>Level</th></tr>'+m.modulators.map(x=>'<tr><td>'+x.id+'</td><td>'+x.level.toFixed(3)+'</td></tr>').join('')+'</table>';" +
            "document.getElementById('h').innerHTML='<table><tr><th>Corridor</th><th>Value</th><th>State</th></tr>'+h.corridors.map(c=>'<tr><td>'+c.name+'</td><td>'+c.value.toFixed(3)+'</td><td>'+c.state+'</td></tr>').join('')+'</table>';" +
            "}catch(e){}},2000);</script></body></html>";
    }

    public boolean isRunning() { return running; }
    public int getPort() { return port; }
}
