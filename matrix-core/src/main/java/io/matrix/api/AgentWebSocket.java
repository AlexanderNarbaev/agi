package io.matrix.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.matrix.agent.AgentBrainService;
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

@ServerEndpoint("/api/v1/agent/ws")
@ApplicationScoped
public class AgentWebSocket {

    private static final Logger log = LoggerFactory.getLogger(AgentWebSocket.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_SENSOR_BITS = 0xFFFFF;
    private static final int MAX_MESSAGE_RATE = 100;

    private final Map<String, Session> agentSessions = new ConcurrentHashMap<>();
    private final Map<Session, String> sessionAgents = new ConcurrentHashMap<>();
    private final Map<Session, AtomicInteger> messageCounters = new ConcurrentHashMap<>();

    @Inject
    AgentBrainService brainService;

    @Inject
    NeuronCacheService neuronCache;

    @OnOpen
    public void onOpen(Session session) {
        log.info("WebSocket opened: session={}", session.getId());
    }

    @OnClose
    public void onClose(Session session) {
        String agentId = sessionAgents.remove(session);
        if (agentId != null) {
            agentSessions.remove(agentId);
            log.info("Agent {} disconnected: session={}", agentId, session.getId());
        } else {
            log.info("WebSocket closed: session={}", session.getId());
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket error: session={}", session.getId(), error);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            int count = messageCounters.computeIfAbsent(session, s -> new AtomicInteger()).incrementAndGet();
            if (count > MAX_MESSAGE_RATE) {
                sendError(session, "Rate limit exceeded: " + MAX_MESSAGE_RATE + " messages per session");
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
                default -> sendError(session, "Unknown message type: " + type);
            }
        } catch (IllegalArgumentException e) {
            sendError(session, "Validation error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to process message", e);
            sendError(session, "Message processing failed: " + e.getMessage());
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
        // Support multi-agent: agentId can come from the message or the session
        String agentId = msg.has("agentId")
                ? msg.get("agentId").asText()
                : sessionAgents.get(session);

        if (agentId == null) {
            sendError(session, "No agent started for this session");
            return;
        }

        long sensorBits = msg.has("data") ? msg.get("data").asLong() : 0L;
        String action = brainService.act(sensorBits);

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

        int generations = msg.has("generations") ? msg.get("generations").asInt() : 20;
        int population = msg.has("population") ? msg.get("population").asInt() : 30;
        int k = msg.has("k") ? msg.get("k").asInt() : 8;

        log.info("Starting training for agent {}: generations={}, population={}, k={}",
                agentId, generations, population, k);

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

        int iterations = msg.has("iterations") ? msg.get("iterations").asInt() : 5;
        log.info("Starting online training for agent {}: iterations={}", agentId, iterations);

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

        String filePath = "/tmp/matrix-brain-" + agentId + ".json";
        brainService.save(filePath);

        ObjectNode response = MAPPER.createObjectNode();
        response.put("type", "saved");
        response.put("path", filePath);
        session.getAsyncRemote().sendText(MAPPER.writeValueAsString(response));
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
}
