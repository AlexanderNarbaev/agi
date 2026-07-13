package io.matrix.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.matrix.agent.AgentBrainService;
import io.matrix.observability.MatrixMetrics;
import io.matrix.redis.NeuronCacheService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Secure WebSocket endpoint for agent communication.
 *
 * <p>Security features:
 * <ul>
 *   <li>Token-based authentication via query parameter</li>
 *   <li>Message size limit (64 KB)</li>
 *   <li>Time-windowed rate limiting (100 messages per minute)</li>
 *   <li>Input validation for all message fields</li>
 *   <li>Path traversal protection for file operations</li>
 * </ul>
 */
@ServerEndpoint(value = "/api/v1/agent/ws", configurator = AgentWebSocketConfigurator.class)
@ApplicationScoped
public class AgentWebSocket {

    private static final Logger log = LoggerFactory.getLogger(AgentWebSocket.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_SENSOR_BITS = 0xFFFFF;
    private static final int MAX_MESSAGE_RATE = 100;
    private static final long RATE_WINDOW_MS = 60_000; // 1 minute
    private static final int MAX_MESSAGE_BYTES = 65_536; // 64 KB
    private static final int MAX_TRAIN_GENERATIONS = 500;
    private static final int MAX_TRAIN_POPULATION = 500;
    private static final int MAX_TRAIN_K = 20;
    private static final int MAX_ONLINE_ITERATIONS = 1000;

    private final Map<String, Session> agentSessions = new ConcurrentHashMap<>();
    private final Map<Session, String> sessionAgents = new ConcurrentHashMap<>();
    private final Map<Session, RateWindow> rateWindows = new ConcurrentHashMap<>();

    @Inject
    AgentBrainService brainService;

    @Inject
    NeuronCacheService neuronCache;

    @Inject
    MatrixMetrics metrics;

    /**
     * Tracks message count within a sliding time window.
     */
    private static final class RateWindow {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart;

        RateWindow() {
            this.windowStart = System.currentTimeMillis();
        }

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart > RATE_WINDOW_MS) {
                count.set(0);
                windowStart = now;
            }
            return count.incrementAndGet() <= MAX_MESSAGE_RATE;
        }
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        // Authentication: check token from query parameter
        String token = extractToken(session);
        if (!isValidToken(token)) {
            log.warn("WebSocket rejected: invalid or missing token, session={}", session.getId());
            try {
                session.getBasicRemote().sendText("{\"type\":\"error\",\"message\":\"Authentication required\"}");
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Authentication required"));
            } catch (IOException e) {
                log.debug("Failed to send auth rejection", e);
            }
            return;
        }

        // Configure max message size
        session.setMaxTextMessageBufferSize(MAX_MESSAGE_BYTES);
        session.setMaxBinaryMessageBufferSize(MAX_MESSAGE_BYTES);

        rateWindows.put(session, new RateWindow());
        log.info("WebSocket opened: session={}", session.getId());
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        String agentId = sessionAgents.remove(session);
        rateWindows.remove(session);
        if (agentId != null) {
            agentSessions.remove(agentId);
            log.info("Agent {} disconnected: session={}, reason={}", agentId, session.getId(), reason.getCloseCode());
        } else {
            log.info("WebSocket closed: session={}, reason={}", session.getId(), reason.getCloseCode());
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket error: session={}", session.getId(), error);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            // Rate limiting with sliding window
            RateWindow window = rateWindows.get(session);
            if (window == null || !window.tryAcquire()) {
                sendError(session, "Rate limit exceeded: max " + MAX_MESSAGE_RATE + " messages per minute");
                return;
            }

            // Message size check (defense in depth)
            if (message.length() > MAX_MESSAGE_BYTES) {
                sendError(session, "Message too large: max " + MAX_MESSAGE_BYTES + " bytes");
                return;
            }

            JsonNode msg = MAPPER.readTree(message);
            String type = msg.has("type") ? msg.get("type").asText() : "";

            switch (type) {
                case "start" -> { validateAgentId(msg); handleStart(msg, session); }
                case "stop" -> handleStop(session);
                case "sensors" -> handleSensors(msg, session);
                case "train" -> handleTrain(msg, session);
                case "train-online" -> handleTrainOnline(msg, session);
                case "save" -> handleSave(session);
                case "feedback" -> handleFeedback(msg, session);
                default -> sendError(session, "Unknown message type: " + type);
            }
        } catch (IllegalArgumentException e) {
            sendError(session, "Validation error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to process message", e);
            sendError(session, "Message processing failed");
        }
    }

    private void validateAgentId(JsonNode msg) {
        if (!msg.has("agentId")) return;
        String id = msg.get("agentId").asText();
        if (id.length() > 64 || !id.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid agentId: max 64 chars, alphanumeric+[-_]");
        }
    }

    private void handleStart(JsonNode msg, Session session) throws IOException {
        String agentId = msg.has("agentId") ? msg.get("agentId").asText() : "agent-" + session.getId();

        if (agentSessions.containsKey(agentId)) {
            sendError(session, "Agent " + agentId + " is already active");
            return;
        }

        sessionAgents.put(session, agentId);
        agentSessions.put(agentId, session);

        log.info("Agent {} started: session={}", agentId, session.getId());

        ObjectNode response = MAPPER.createObjectNode();
        response.put("type", "started");
        response.put("agentId", agentId);
        session.getAsyncRemote().sendText(MAPPER.writeValueAsString(response));
    }

    private void handleStop(Session session) throws IOException {
        String agentId = sessionAgents.remove(session);
        if (agentId != null) {
            agentSessions.remove(agentId);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("type", "stopped");
            response.put("agentId", agentId);
            session.getAsyncRemote().sendText(MAPPER.writeValueAsString(response));

            log.info("Agent {} stopped: session={}", agentId, session.getId());
        }
    }

    private void handleSensors(JsonNode msg, Session session) throws IOException {
        String agentId = msg.has("agentId")
                ? msg.get("agentId").asText()
                : sessionAgents.get(session);

        if (agentId == null) {
            sendError(session, "No agent started for this session");
            return;
        }

        long sensorBits = msg.has("data") ? msg.get("data").asLong() : 0L;
        if (sensorBits < 0 || sensorBits > MAX_SENSOR_BITS) {
            sendError(session, "sensorBits out of range: 0-" + MAX_SENSOR_BITS);
            return;
        }

        String role = msg.has("role") ? msg.get("role").asText() : "unknown";
        if (role.length() > 64) {
            sendError(session, "role too long: max 64 chars");
            return;
        }

        String action = brainService.act(sensorBits);

        metrics.recordSensorRequest();
        metrics.recordBotTick(agentId, role, action);

        neuronCache.cacheBrainState(agentId, sensorBits, action);

        ObjectNode response = MAPPER.createObjectNode();
        response.put("type", "action");
        response.put("data", action);
        session.getAsyncRemote().sendText(MAPPER.writeValueAsString(response));
    }

    private void handleTrain(JsonNode msg, Session session) {
        String agentId = sessionAgents.get(session);
        if (agentId == null) {
            sendError(session, "No agent started for this session");
            return;
        }

        int generations = clampInt(msg, "generations", 20, 1, MAX_TRAIN_GENERATIONS);
        int population = clampInt(msg, "population", 30, 1, MAX_TRAIN_POPULATION);
        int k = clampInt(msg, "k", 8, 1, MAX_TRAIN_K);

        log.info("Starting training for agent {}: generations={}, population={}, k={}",
                agentId, generations, population, k);

        metrics.recordTrainRequest();

        CompletableFuture.runAsync(() -> {
            try {
                AgentBrainService.EvolutionResult result = brainService.train(generations, population, k);

                ObjectNode response = MAPPER.createObjectNode();
                response.put("type", "training_complete");
                response.put("bestFitness", result.bestFitness());
                response.put("generations", result.generations());
                session.getAsyncRemote().sendText(MAPPER.writeValueAsString(response));

            } catch (Exception e) {
                log.error("Training failed for agent {}", agentId, e);
                sendError(session, "Training failed: " + e.getMessage());
            }
        });
    }

    private void handleTrainOnline(JsonNode msg, Session session) {
        String agentId = sessionAgents.get(session);
        if (agentId == null) {
            sendError(session, "No agent started for this session");
            return;
        }

        int iterations = clampInt(msg, "iterations", 5, 1, MAX_ONLINE_ITERATIONS);
        log.info("Starting online training for agent {}: iterations={}", agentId, iterations);

        metrics.recordTrainRequest();

        CompletableFuture.runAsync(() -> {
            try {
                brainService.onlineTrain(iterations);

                ObjectNode response = MAPPER.createObjectNode();
                response.put("type", "training_complete");
                response.put("method", "online");
                response.put("iterations", iterations);
                session.getAsyncRemote().sendText(MAPPER.writeValueAsString(response));

                log.info("Online training complete for agent {}", agentId);
            } catch (Exception e) {
                log.error("Online training failed for agent {}", agentId, e);
                sendError(session, "Online training failed: " + e.getMessage());
            }
        });
    }

    private void handleSave(Session session) throws IOException {
        String agentId = sessionAgents.get(session);
        if (agentId == null) {
            sendError(session, "No agent started for this session");
            return;
        }

        // Path traversal protection: agentId is already validated
        String filePath = "/tmp/matrix-brain-" + agentId + ".json";
        brainService.save(filePath);

        ObjectNode response = MAPPER.createObjectNode();
        response.put("type", "saved");
        response.put("path", filePath);
        session.getAsyncRemote().sendText(MAPPER.writeValueAsString(response));
    }

    private void handleFeedback(JsonNode msg, Session session) {
        if (!msg.has("sensors") || !msg.has("success")) {
            sendError(session, "Feedback requires 'sensors' and 'success' fields");
            return;
        }
        long sensors = msg.get("sensors").asLong();
        if (sensors < 0 || sensors > MAX_SENSOR_BITS) {
            sendError(session, "sensors out of range: 0-" + MAX_SENSOR_BITS);
            return;
        }
        boolean success = msg.get("success").asBoolean();
        brainService.recordFeedback(sensors, success);
        log.debug("Recorded feedback: sensors={}, success={}", sensors, success);
    }

    private void sendError(Session session, String message) {
        try {
            ObjectNode response = MAPPER.createObjectNode();
            response.put("type", "error");
            response.put("message", message);
            session.getAsyncRemote().sendText(MAPPER.writeValueAsString(response));
        } catch (Exception e) {
            log.error("Failed to send error to session {}", session.getId(), e);
        }
    }

    /**
     * Extracts authentication token from the WebSocket query string.
     */
    private String extractToken(Session session) {
        String query = session.getQueryString();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }

    /**
     * Validates the authentication token.
     * In development mode, accepts any non-empty token.
     * In production, should validate against a token store or JWT.
     */
    private boolean isValidToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        // Reject tokens that are too long (potential DoS)
        if (token.length() > 512) {
            return false;
        }
        // Reject tokens with control characters
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < 0x20 || c == '\0') {
                return false;
            }
        }
        // TODO: In production, validate against JWT or token store
        return true;
    }

    /**
     * Reads an integer from JSON with clamping to [min, max].
     */
    private int clampInt(JsonNode msg, String field, int defaultVal, int min, int max) {
        if (!msg.has(field)) return defaultVal;
        int val = msg.get(field).asInt();
        return Math.max(min, Math.min(max, val));
    }
}
