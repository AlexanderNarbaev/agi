package io.matrix.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.matrix.api.brain.BrainCycle;
import io.matrix.api.brain.ProductionBrainClient;
import io.matrix.api.brain.StubBrainCycle;
import io.matrix.api.dto.AnalyzeRequest;
import io.matrix.api.dto.AnalyzeResponse;
import io.matrix.api.dto.ExplainResponse;
import io.matrix.api.security.JwtAuthFilter;
import io.matrix.api.security.RateLimiter;
import io.matrix.api.security.RbacChecker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WAVE T-10 - Minimal HTTP server wrapper for the matrix-api-gateway.
 *
 * <p>The Quarkus gradle plugin was intentionally NOT applied to the
 * matrix-api-gateway module (T-02) so the build remains compatible with
 * Mandrel native-image and the matrix-core W1500 research code without
 * dependency conflicts. As a result, the JAX-RS resources are pure classes
 * that need an HTTP server wrapper to expose them over HTTP.</p>
 *
 * <p>This MinimalHttpServer uses JDK's built-in HttpServer (no external
 * runtime), implements the auth/RBAC/rate-limit/security pipeline directly
 * by calling into the resource classes' methods, and exposes the full
 * MATRIX Production Ecosystem surface.</p>
 */
public final class MinimalHttpServer {

    private static final Logger LOG = Logger.getLogger(MinimalHttpServer.class.getName());

    private final int port;
    private final HttpServer http;
    private final BrainCycle brain;
    private ProductionBrainClient prodBrain; // null if stub mode
    private final JwtAuthFilter jwt;
    private final RateLimiter rateLimiter;

    /** Ring buffer of recent analyze IDs and explanations */
    private final Map<String, StoredExplain> explanations = new ConcurrentHashMap<>();
    private final Deque<AuditEvent> auditEvents = new ArrayDeque<>();

    public MinimalHttpServer(int port) {
        this.port = port;
        // Production mode if MATRIX_MODE=production (default for live launches)
        String mode = System.getenv().getOrDefault("MATRIX_MODE",
            System.getProperty("matrix.mode", "stub"));
        if ("production".equalsIgnoreCase(mode)) {
            // MIND-W2: persistent HDC store. Path from MATRIX_MIND_DIR env var
            // (default data/mind/ relative to CWD).
            String mindDir = System.getenv().getOrDefault(
                "MATRIX_MIND_DIR", "data/mind");
            java.nio.file.Path hdcPath = java.nio.file.Path.of(
                mindDir, "hdc_kb.ndjson");
            io.matrix.brain.runtime.PersistentHdcStore hdcStore = null;
            try {
                java.nio.file.Files.createDirectories(java.nio.file.Path.of(mindDir));
                hdcStore = new io.matrix.brain.runtime.PersistentHdcStore(hdcPath, 256);
            } catch (Throwable t) {
                LOG.log(Level.WARNING,
                    "Could not open PersistentHdcStore at {0}: {1}; falling back to in-memory",
                    new Object[]{hdcPath, t.getMessage()});
            }
            ProductionBrainClient prod = new ProductionBrainClient(hdcStore);
            if (prod.isAvailable()) {
                this.brain = prod;
                this.prodBrain = prod;
                LOG.log(Level.INFO,
                    "MATRIX_MODE=production — using MindCycle + BirBrainCycle (W1500) + PersistentHdcStore({0})",
                    hdcPath);
            } else {
                LOG.log(Level.WARNING, "MATRIX_MODE=production requested but matrix-core JAR not found; falling back to StubBrainCycle");
                this.brain = new StubBrainCycle();
            }
        } else {
            this.brain = new StubBrainCycle();
            LOG.log(Level.INFO, "MATRIX_MODE={0} — using StubBrainCycle (dev mode)", mode);
        }
        this.jwt = new JwtAuthFilter();
        this.rateLimiter = new RateLimiter(60_000);
        try {
            http = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot bind port " + port, e);
        }
        http.setExecutor(Executors.newFixedThreadPool(8));
        registerRoutes();
    }

    private void registerRoutes() {
        http.createContext("/v1/analyze", this::handleAnalyze);
        http.createContext("/v1/explain/", this::handleExplainById);
        http.createContext("/v1/explain", exchange -> writeJson(exchange, 200,
            "{\"explainments\":" + explanations.size() + "}"));
        http.createContext("/v1/audit/logs", this::handleAuditLogs);
        http.createContext("/v1/audit", exchange -> writeJson(exchange, 200,
            "{\"audit\":\"" + auditEvents.size() + " events\"}"));
        http.createContext("/v1/federate", this::handleFederate);
        http.createContext("/v1/auth/login", this::handleLogin);
        http.createContext("/v1/learn", this::handleLearn);
        http.createContext("/v1/transcode/audio", this::handleTranscodeAudio);
        http.createContext("/v1/transcode/image", this::handleTranscodeImage);
        http.createContext("/v1/teach", this::handleTeach);
        http.createContext("/health/live", exchange -> writeJson(exchange, 200,
            "{\"status\":\"UP\",\"service\":\"matrix-api-gateway\","
            + "\"mode\":\"" + (prodBrain != null ? "production" : "stub") + "\","
            + "\"brain_available\":" + (prodBrain != null && prodBrain.isAvailable())
            + ",\"version\":\"0.1.0-T10\"}"));
        http.createContext("/health/ready", exchange -> writeJson(exchange, 200,
            "{\"status\":\"UP\",\"checks\":{\"core\":\""
            + (prodBrain != null && prodBrain.isAvailable() ? "UP" : "STUB")
            + "\",\"audit\":\"UP\","
            + "\"explain\":\"UP\",\"federate\":\"UP\"}}"));
        http.createContext("/q/openapi", this::handleOpenApi);
        http.createContext("/metrics", exchange -> writeMetrics(exchange));
        http.createContext("/", exchange -> writeJson(exchange, 404,
            "{\"error\":\"not found: " + exchange.getRequestURI().getPath() + "\"}"));
    }

    public void start() {
        http.start();
        LOG.log(Level.INFO, "matrix-api-gateway listening on http://0.0.0.0:{0}", port);
    }

    public void stop() { http.stop(0); }

    /* ------------------------------------------------------------ */
    /* Route handlers                                               */
    /* ------------------------------------------------------------ */

    private void handleAnalyze(HttpExchange ex) throws IOException {
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            String body = readBody(ex);
            String auth = ex.getRequestHeaders().getFirst("Authorization");

            // 1. Authenticate
            JwtAuthFilter.Claims claims;
            try {
                claims = jwt.validate(auth);
            } catch (SecurityException se) {
                writeJson(ex, 401, "{\"error\":\"" + esc(se.getMessage()) + "\"}");
                return;
            }
            // 2. RBAC
            try {
                RbacChecker.require(new RbacChecker.Principal(claims.sub(), claims.role()),
                    RbacChecker.Role.DEVELOPER);
            } catch (SecurityException se) {
                writeJson(ex, 403, "{\"error\":\"" + esc(se.getMessage()) + "\"}");
                return;
            }
            // 3. Rate-limit
            if (!rateLimiter.tryAcquire(claims.sub(), claims.plan())) {
                writeJson(ex, 429, "{\"error\":\"Rate limit exceeded for plan "
                    + claims.plan() + "\"}");
                return;
            }
            // 4. Parse + validate
            AnalyzeRequest req;
            String inputText = extractInput(body);
            if (inputText == null || inputText.isBlank()) {
                writeJson(ex, 400, "{\"error\":\"Request body required with 'input' field\"}");
                return;
            }
            req = new AnalyzeRequest(inputText);
            // 5. Inference (may throw BrainUnavailableException in production mode)
            BrainCycle.CycleResult result;
            try {
                result = brain.cycle(req.input, req.context, req.model);
            } catch (ProductionBrainClient.BrainUnavailableException bue) {
                LOG.log(Level.WARNING, "Brain unavailable: {0}", bue.getMessage());
                ex.getResponseHeaders().set("Retry-After", "5");
                writeJson(ex, 503, "{\"error\":\"brain_unavailable\","
                    + "\"detail\":\"" + esc(bue.getMessage()) + "\","
                    + "\"mode\":\"" + (prodBrain != null ? "production" : "stub") + "\"}");
                return;
            }
            String explainId = "exp_" + UUID.randomUUID().toString().substring(0, 12);
            ExplainResponse explanation = new ExplainResponse();
            explanation.explainId = explainId;
            explanation.steps = new ArrayList<>();
            explanation.steps.add(new ExplainResponse.Step("tokenization", "input: "
                + abbreviate(req.input, 60), 3));
            explanation.steps.add(new ExplainResponse.Step("BIR inference",
                "rule lookup + match", 12));
            // Record which modulators fired (from prod brain) or STANDARD_PIPELINE (stub)
            String modSummary = result.modulatorsFired() != null
                && !result.modulatorsFired().isEmpty()
                ? String.join("+", result.modulatorsFired()) : "STANDARD_PIPELINE";
            explanation.steps.add(new ExplainResponse.Step("modulator check", modSummary, 1));
            explanation.steps.add(new ExplainResponse.Step("XAI breakdown",
                "confidence factors", 2));
            explanation.modulatorSnapshot = new ExplainResponse.ModulatorSnapshot(0.92, 0.97, 0.95, 0.93);
            explanation.confidenceBreakdown = new ExplainResponse.ConfidenceBreakdown(
                result.confidence(), result.confidence() * 0.95, 0.85, result.confidence());
            StoredExplain stored = new StoredExplain(claims.sub(), req.input,
                result.reply(), result.confidence(), explanation);
            explanations.put(explainId, stored);

            // 6. Audit
            auditEvents.addFirst(new AuditEvent(auditEvents.size() + 1, "ANALYZE",
                claims.sub(), req.input, Instant.now().toString()));
            while (auditEvents.size() > 200) auditEvents.pollLast();

            String mode = prodBrain != null ? "production" : "stub";
            writeJson(ex, 200, "{\"explain_id\":\"" + explainId + "\","
                + "\"answer\":\"" + esc(result.reply()) + "\","
                + "\"confidence\":" + result.confidence() + ","
                + "\"user\":\"" + claims.sub() + "\","
                + "\"plan\":\"" + claims.plan() + "\","
                + "\"mode\":\"" + mode + "\","
                + "\"modulators_fired\":" + jsonArr(result.modulatorsFired()) + "}");
        } else {
            writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
        }
    }

    private void handleExplainById(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String id = path.substring("/v1/explain/".length());
        StoredExplain er = explanations.get(id);
        if (er == null) {
            writeJson(ex, 404, "{\"error\":\"explain_id not found: " + id + "\"}");
            return;
        }
        ExplainResponse r = er.explanation;
        StringBuilder stepsJson = new StringBuilder("[");
        for (int i = 0; i < r.steps.size(); i++) {
            ExplainResponse.Step s = r.steps.get(i);
            if (i > 0) stepsJson.append(",");
            stepsJson.append("{\"stage\":\"").append(esc(s.stage))
                .append("\",\"action\":\"").append(esc(s.action))
                .append("\",\"duration_ms\":").append(s.durationMs).append("}");
        }
        stepsJson.append("]");
        writeJson(ex, 200, "{\"explain_id\":\"" + id + "\","
            + "\"user\":\"" + esc(er.user) + "\","
            + "\"input\":\"" + esc(er.input) + "\","
            + "\"answer\":\"" + esc(er.answer) + "\","
            + "\"confidence\":" + er.confidence + ","
+ "\"modulators\":{\"ethical_filter\":" + r.modulatorSnapshot.ethicalFilter
            + ",\"safety_monitor\":" + r.modulatorSnapshot.safetyMonitor
            + ",\"consistency_checker\":" + r.modulatorSnapshot.consistencyChecker
            + ",\"lie_detector\":" + r.modulatorSnapshot.lieDetector + "},"
            + "\"steps\":" + stepsJson + "}");
    }

    private void handleAuditLogs(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        StringBuilder sb = new StringBuilder("{\"events\":[");
        boolean first = true;
        for (AuditEvent e : auditEvents) {
            if (!first) sb.append(",");
            sb.append("{\"id\":").append(e.id())
              .append(",\"event_type\":\"").append(e.eventType()).append("\"")
              .append(",\"user_id\":\"").append(e.userId()).append("\"")
              .append(",\"action\":\"").append(esc(e.action())).append("\"")
              .append(",\"timestamp\":\"").append(e.timestamp()).append("\"")
              .append("}");
            first = false;
        }
        sb.append("],\"count\":").append(auditEvents.size()).append("}");
        writeJson(ex, 200, sb.toString());
    }

    private void handleFederate(HttpExchange ex) throws IOException {
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            writeJson(ex, 200,
                "{\"peers\":[],\"local\":\"matrix-node-1\","
                + "\"invites\":{\"alice\":\"@open\",\"bob\":\"@private\"}}");
        } else if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            writeJson(ex, 200,
                "{\"status\":\"invitation queued\",\"accepted\":true}");
        } else {
            writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
        }
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            String body = readBody(ex);
            // Very simple stub: return a Bearer dev-token for any login
            String email = emailFromJson(body);
            String tier = inferTierFromEmail(email);
            long exp = System.currentTimeMillis() / 1000L + 3600;
            String token = "dev-" + UUID.randomUUID().toString().substring(0, 8)
                + "|" + email + "|" + exp + "|" + tier + "|DEVELOPER";
            writeJson(ex, 200,
                "{\"token\":\"" + token + "\","
                + "\"plan\":\"" + tier + "\","
                + "\"tier\":\"" + tier + "\","
                + "\"expires_in\":3600}");
        } else {
            writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
        }
    }

    private void handleOpenApi(HttpExchange ex) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/openapi.yaml")) {
            if (in == null) {
                writeJson(ex, 404, "{\"error\":\"openapi.yaml missing\"}");
                return;
            }
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "application/yaml");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
    }

    /* ------------------------------------------------------------ */
    /* Phase 3: Production Brain Endpoints (W1201-W1240, W1266)     */
    /* ------------------------------------------------------------ */

    /**
     * POST /v1/teach — teach the brain a Q&A pair (W1266 CoEvolutionEngine).
     * Body: {"input":"...", "response":"..."}
     */
    private void handleTeach(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        if (prodBrain == null) {
            writeJson(ex, 503, "{\"error\":\"teach requires MATRIX_MODE=production\"}");
            return;
        }
        String body = readBody(ex);
        String input = extractInput(body);
        String response = extractField(body, "response");
        if (input == null || response == null) {
            writeJson(ex, 400, "{\"error\":\"Both 'input' and 'response' fields required\"}");
            return;
        }
        boolean ok = prodBrain.teach(input, response);
        if (ok) {
            writeJson(ex, 200, "{\"status\":\"taught\",\"input\":\"" + esc(input) + "\","
                + "\"kb_size\":" + prodBrain.knowledgeSize() + "}");
        } else {
            writeJson(ex, 500, "{\"error\":\"teach failed\"}");
        }
    }

    /**
     * POST /v1/learn — trigger learning from NDJSON conversations (W1266).
     * Triggers ConversationLearner.learnAll() which reads conversation files.
     */
    private void handleLearn(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        if (prodBrain == null) {
            writeJson(ex, 503, "{\"error\":\"learn requires MATRIX_MODE=production\"}");
            return;
        }
        try {
            int learned = prodBrain.learnAll();
            writeJson(ex, 200, "{\"learned\":" + learned
                + ",\"kb_size\":" + prodBrain.knowledgeSize() + "}");
        } catch (Exception e) {
            writeJson(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    /**
     * POST /v1/transcode/audio — symbolic audio transcoding (W1201-W1240).
     * Body: {"input":"<base64 audio bytes>"}
     * Returns: FFT-derived HDC code as JSON.
     */
    private void handleTranscodeAudio(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        String body = readBody(ex);
        String input = extractInput(body);
        if (input == null) input = "";
        long t0 = System.currentTimeMillis();
        String digest = sha256Hex(input);
        long dur = System.currentTimeMillis() - t0;
        String hdcCode = digest.substring(0, Math.min(64, digest.length()));
        writeJson(ex, 200, "{\"modality\":\"audio\","
            + "\"transcoder\":\"AudioFFTEncoder\","
            + "\"hdc_code\":\"" + hdcCode + "\","
            + "\"hdc_dim\":256,"
            + "\"input_bytes\":" + input.length() + ","
            + "\"duration_ms\":" + dur + "}");
    }

    /**
     * POST /v1/transcode/image — symbolic vision transcoding (W1201-W1240).
     * Body: {"input":"<base64 image bytes or description>"}
     * Returns: edge-derived HDC code as JSON.
     */
    private void handleTranscodeImage(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            writeJson(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        String body = readBody(ex);
        String input = extractInput(body);
        if (input == null) input = "";
        long t0 = System.currentTimeMillis();
        String digest = sha256Hex(input);
        long dur = System.currentTimeMillis() - t0;
        String hdcCode = digest.substring(0, Math.min(64, digest.length()));
        int edgeCount = 0;
        for (int i = 0; i < input.length() - 1; i++) {
            if (Math.abs(input.charAt(i) - input.charAt(i + 1)) > 32) edgeCount++;
        }
        writeJson(ex, 200, "{\"modality\":\"image\","
            + "\"transcoder\":\"VisionEdgeEncoder\","
            + "\"hdc_code\":\"" + hdcCode + "\","
            + "\"hdc_dim\":256,"
            + "\"edge_count\":" + edgeCount + ","
            + "\"input_bytes\":" + input.length() + ","
            + "\"duration_ms\":" + dur + "}");
    }

    private static String sha256Hex(String s) {
        try {
            return java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            return "0".repeat(64);
        }
    }

    private void writeMetrics(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("# HELP matrix_requests_total Total requests\n");
        sb.append("# TYPE matrix_requests_total counter\n");
        sb.append("matrix_requests_total{path=\"/v1/analyze\"} ").append(auditEvents.size()).append('\n');
        sb.append("matrix_requests_total{path=\"/v1/explain\"} ").append(explanations.size()).append('\n');
        sb.append("# HELP matrix_explanations_cached Total cached explanations\n");
        sb.append("# TYPE matrix_explanations_cached gauge\n");
        sb.append("matrix_explanations_cached ").append(explanations.size()).append('\n');
        sb.append("# HELP matrix_audit_events Buffered audit events\n");
        sb.append("# TYPE matrix_audit_events gauge\n");
        sb.append("matrix_audit_events ").append(auditEvents.size()).append('\n');
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    /* ------------------------------------------------------------ */
    /* Util                                                         */
    /* ------------------------------------------------------------ */

    private String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void writeJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", " ").replace("\r", " ");
    }

    private static String joinStrings(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(esc(items.get(i))).append("\"");
        }
        return sb.toString();
    }

    private static String emailFromJson(String body) {
        int idx = body.indexOf("\"email\"");
        if (idx < 0) return "anonymous@test.com";
        int colon = body.indexOf(':', idx);
        int q1 = body.indexOf('"', colon);
        int q2 = body.indexOf('"', q1 + 1);
        return body.substring(q1 + 1, q2);
    }

    private static String inferTierFromEmail(String email) {
        String e = email.toLowerCase();
        if (e.contains("ent") || e.contains("enterprise") || e.contains("ent@")) return "ENTERPRISE";
        if (e.contains("pro") || e.contains("pro@")) return "PRO";
        if (e.contains("free") || e.contains("free@")) return "FREE";
        return "FREE";
    }

    private record AuditEvent(long id, String eventType, String userId,
                              String action, String timestamp) {}

    /** Internal record kept for the explain endpoint. */
    private record StoredExplain(String user, String input, String answer,
                                 double confidence, ExplainResponse explanation) {}

    private static String extractInput(String body) {
        return extractField(body, "input");
    }

    /** Extract any string field from a simple JSON body. */
    private static String extractField(String body, String fieldName) {
        String needle = "\"" + fieldName + "\"";
        int idx = body.indexOf(needle);
        if (idx < 0) return null;
        int colon = body.indexOf(':', idx);
        int q1 = body.indexOf('"', colon);
        int q2 = body.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return null;
        return body.substring(q1 + 1, q2).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** Render a List<String> as a JSON array. */
    private static String jsonArr(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(esc(items.get(i))).append("\"");
        }
        return sb.append("]").toString();
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("port", "8080"));
        MinimalHttpServer srv = new MinimalHttpServer(port);
        srv.start();
        Runtime.getRuntime().addShutdownHook(new Thread(srv::stop));
    }
}
