package io.matrix.spigot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP + WebSocket client for communicating with the MATRIX Core
 * neural inference server.
 *
 * <p>Protocol:
 * <ul>
 *   <li>WebSocket: sends sensor data as text, receives string actions</li>
 *   <li>REST POST /api/v1/agent/train — triggers genetic algorithm training</li>
 *   <li>REST POST /api/v1/agent/save — saves current brain state</li>
 * </ul>
 *
 * <p>WebSocket message format (client → server):
 * <pre>{@code
 *   {"type":"start","agentId":"MatrixBot1"}
 *   {"type":"sensors","data":1234567}
 *   {"type":"stop"}
 * }</pre>
 *
 * <p>WebSocket message format (server → client):
 * <pre>{@code
 *   {"type":"action","data":"MOVE_N"}
 *   {"type":"status","data":"connected"}
 *   {"type":"error","data":"message"}
 * }</pre>
 */
public class MatrixCoreClient {

    public interface ActionCallback {
        void onAction(String action);
        void onStatus(String status);
        void onError(String error);
    }

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Logger logger;

    private volatile WebSocket webSocket;
    private volatile ActionCallback callback;
    private volatile boolean connected;
    private volatile boolean reconnectEnabled;

    private final ScheduledExecutorService reconnectScheduler;
    private ScheduledFuture<?> reconnectFuture;

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final int RECONNECT_DELAY_SECONDS = 5;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final long RECONNECT_DELAY_MS = 3000;
    private volatile int reconnectAttempts = 0;
    private volatile boolean shouldReconnect = true;

    /**
     * Creates a client targeting the given matrix-core base URL.
     *
     * @param baseUrl e.g. "http://localhost:9091"
     */
    public MatrixCoreClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.logger = Logger.getLogger("MatrixCoreClient");
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "matrix-reconnect");
            t.setDaemon(true);
            return t;
        });
        this.reconnectEnabled = true;
    }

    /**
     * Connects to matrix-core via WebSocket asynchronously.
     * Registers the provided callback for incoming actions and status updates.
     *
     * @param callback receives actions, status, and errors from the server
     * @return future that completes when the WebSocket handshake finishes
     */
    public CompletableFuture<Void> connect(ActionCallback callback) {
        this.callback = callback;
        this.reconnectEnabled = true;

        CompletableFuture<WebSocket> wsFuture = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(wsUrl()), new WebSocketListener());

        return wsFuture.thenAccept(ws -> {
            this.webSocket = ws;
            this.connected = true;
            this.reconnectAttempts = 0;
            logger.info("WebSocket connected to " + wsUrl());
            notifyStatus("connected");
        }).exceptionally(ex -> {
            logger.log(Level.WARNING, "Failed to connect WebSocket: " + ex.getMessage());
            this.connected = false;
            notifyError("Connection failed: " + ex.getMessage());
            scheduleReconnect();
            return null;
        });
    }

    /**
     * Sends sensor data to matrix-core via WebSocket for neural inference.
     * The action result arrives asynchronously via {@link ActionCallback#onAction}.
     *
     * @param agentId    unique agent identifier (for multi-agent mode)
     * @param sensorBits 20-bit sensor vector from Minecraft world state
     */
    public void sendSensors(String agentId, long sensorBits) {
        if (!connected || webSocket == null) {
            return;
        }
        String msg = "{\"type\":\"sensors\",\"data\":" + sensorBits
                + ",\"agentId\":\"" + agentId + "\"}";
        webSocket.sendText(msg, true)
                .exceptionally(ex -> {
                    logger.warning("Failed to send sensors: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Sends sensor data without explicit agentId (backward compat).
     */
    public void sendSensors(long sensorBits) {
        sendSensors("default", sensorBits);
    }

    /**
     * Sends a start message to activate an agent on the server.
     *
     * @param agentId agent identifier
     */
    public void start(String agentId) {
        if (!connected || webSocket == null) {
            logger.warning("Cannot start agent — not connected");
            return;
        }
        String msg = "{\"type\":\"start\",\"agentId\":\"" + agentId + "\"}";
        webSocket.sendText(msg, true)
                .exceptionally(ex -> {
                    logger.warning("Failed to send start: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Sends a stop message to deactivate the agent.
     */
    public void stop() {
        if (!connected || webSocket == null) {
            return;
        }
        webSocket.sendText("{\"type\":\"stop\"}", true)
                .exceptionally(ex -> {
                    logger.warning("Failed to send stop: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Triggers genetic algorithm training via REST API.
     *
     * @param generations number of GA generations
     * @param population  population size
     * @param k           number of input bits for the neuron
     * @return future with the server's JSON response as a String
     */
    public CompletableFuture<String> train(int generations, int population, int k) {
        String body = "{\"generations\":" + generations
                + ",\"population\":" + population
                + ",\"k\":" + k + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/agent/train"))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        logger.info("Train response: " + response.body());
                    } else {
                        logger.warning("Train failed: HTTP " + response.statusCode()
                                + " — " + response.body());
                    }
                    return response.body();
                });
    }

    /**
     * Saves the current brain state via REST API.
     *
     * @return future that completes when save is acknowledged
     */
    public CompletableFuture<Void> save() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/agent/save"))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        logger.info("Brain saved: " + response.body());
                    } else {
                        logger.warning("Save failed: HTTP " + response.statusCode());
                    }
                });
    }

    /**
     * Loads pretrained brain weights from a specified path via REST API.
     *
     * @param path file path to the pretrained brain JSON file, or
     *             "models/pretrained" to auto-discover pretrained weights
     * @return future that completes when load is acknowledged
     */
    public CompletableFuture<Void> load(String path) {
        String body = "{\"path\":\"" + (path != null ? path : "models/pretrained") + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/agent/load"))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        logger.info("Brain loaded: " + response.body());
                    } else {
                        logger.warning("Load failed: HTTP " + response.statusCode());
                    }
                });
    }

    /**
     * Triggers online training (hill-climbing) via REST API.
     *
     * <p>This is a lightweight alternative to full GA training,
     * using recent feedback to adjust weights locally.
     *
     * @param iterations number of hill-climbing iterations per tree
     * @return future with the server's JSON response
     */
    public CompletableFuture<String> trainOnline(int iterations) {
        String body = "{\"iterations\":" + iterations + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/agent/train-online"))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        logger.info("Online training response: " + response.body());
                    } else {
                        logger.warning("Online training failed: HTTP " + response.statusCode()
                                + " — " + response.body());
                    }
                    return response.body();
                });
    }

    /**
     * Shares best-performing neurons with the swarm via REST API.
     *
     * @param role       agent role (miner, crafter, explorer, etc.)
     * @param agentId    unique identifier of the sharing agent
     * @param neuronData base64-encoded neuron data
     * @param fitness    fitness score of this neuron
     * @return future with the server's JSON response
     */
    public CompletableFuture<String> shareNeurons(String role, String agentId,
                                                   String neuronData, double fitness) {
        String body = "{\"role\":\"" + role + "\",\"agentId\":\"" + agentId
                + "\",\"neuronData\":\"" + neuronData + "\",\"fitness\":" + fitness + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/agent/share"))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        logger.info("Neuron shared: " + response.body());
                    } else {
                        logger.warning("Share failed: HTTP " + response.statusCode());
                    }
                    return response.body();
                });
    }

    /**
     * Retrieves shared neurons from the swarm for a given role.
     *
     * @param role agent role (miner, crafter, explorer, etc.)
     * @return future with the server's JSON response (list of shared neurons)
     */
    public CompletableFuture<String> getSharedNeurons(String role) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/agent/neurons/" + role))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response.body();
                    } else {
                        logger.warning("Get neurons failed: HTTP " + response.statusCode());
                        return "[]";
                    }
                });
    }

    /**
     * Returns whether the WebSocket is currently connected.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Disconnects the WebSocket and stops reconnection attempts.
     */
    public void disconnect() {
        reconnectEnabled = false;
        connected = false;
        cancelReconnect();
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Plugin shutdown");
            } catch (Exception ignored) {
            }
            webSocket = null;
        }
        logger.info("Disconnected from matrix-core");
    }

    /**
     * Shuts down the client completely — disconnects and releases resources.
     */
    public void shutdown() {
        disconnect();
        reconnectScheduler.shutdown();
        try {
            reconnectScheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sends feedback data to matrix-core for online training.
     * Fire-and-forget via WebSocket — non-blocking.
     *
     * @param agentId    agent identifier
     * @param sensorBits sensor vector that triggered the action
     * @param success    whether the action was successful
     */
    public void sendFeedback(String agentId, long sensorBits, boolean success) {
        if (!connected || webSocket == null) return;
        String msg = "{\"type\":\"feedback\",\"agentId\":\""
                + agentId + "\",\"sensors\":" + sensorBits
                + ",\"success\":" + success + "}";
        webSocket.sendText(msg, true)
                .exceptionally(ex -> {
                    logger.fine("Failed to send feedback: " + ex.getMessage());
                    return null;
                });
        logger.fine("Sent feedback: " + agentId + " success=" + success);
    }

    private String wsUrl() {
        return baseUrl.replaceFirst("^http", "ws") + "/api/v1/agent/ws";
    }

    private void notifyAction(String action) {
        ActionCallback cb = callback;
        if (cb != null) {
            cb.onAction(action);
        }
    }

    private void notifyStatus(String status) {
        ActionCallback cb = callback;
        if (cb != null) {
            cb.onStatus(status);
        }
    }

    private void notifyError(String error) {
        ActionCallback cb = callback;
        if (cb != null) {
            cb.onError(error);
        }
    }

    private void scheduleReconnect() {
        if (!reconnectEnabled) return;
        cancelReconnect();
        logger.info("Will reconnect in " + RECONNECT_DELAY_SECONDS + "s...");
        reconnectFuture = reconnectScheduler.schedule(() -> {
            if (reconnectEnabled && !connected) {
                logger.info("Attempting reconnection...");
                connect(callback);
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelReconnect() {
        if (reconnectFuture != null) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
    }

    /**
     * WebSocket listener handling incoming messages and lifecycle events.
     */
    private class WebSocketListener implements WebSocket.Listener {

        private final StringBuilder messageBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            connected = true;
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (last) {
                handleMessage(messageBuffer.toString());
                messageBuffer.setLength(0);
            }
            ws.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            connected = false;
            logger.log(Level.WARNING, "WebSocket error: " + error.getMessage());
            notifyError("WebSocket error");
            scheduleReconnect();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            connected = false;
            logger.warning("WebSocket closed: " + statusCode + " " + reason);
            notifyStatus("disconnected");
            if (shouldReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++;
                logger.info("Reconnecting in " + RECONNECT_DELAY_MS + "ms (attempt "
                        + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                new Thread(() -> {
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS);
                        connect(callback);
                    } catch (Exception e) {
                        logger.warning("Reconnect failed: " + e.getMessage());
                    }
                }, "matrix-reconnect-" + reconnectAttempts).start();
            } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                logger.severe("Max reconnect attempts (" + MAX_RECONNECT_ATTEMPTS
                        + ") reached. Giving up.");
            }
            return null;
        }

        private void handleMessage(String raw) {
            String msg = raw.trim();
            if (msg.isEmpty()) return;

            // Parse simple JSON: {"type":"...","data":"..."}
            String type = extractJsonField(msg, "type");
            String payload = extractJsonField(msg, "data");

            if (type == null) return;

            switch (type) {
                case "action" -> {
                    if (payload != null) {
                        notifyAction(payload);
                    }
                }
                case "status" -> {
                    if (payload != null) {
                        logger.info("Server status: " + payload);
                        notifyStatus(payload);
                    }
                }
                case "error" -> {
                    String err = payload != null ? payload : "unknown error";
                    logger.warning("Server error: " + err);
                    notifyError(err);
                }
                default -> logger.fine("Unknown message type: " + type);
            }
        }

        /**
         * Extracts a simple JSON string field value. Handles both quoted and unquoted values.
         */
        private String extractJsonField(String json, String fieldName) {
            String key = "\"" + fieldName + "\"";
            int keyIdx = json.indexOf(key);
            if (keyIdx < 0) return null;

            int colonIdx = json.indexOf(':', keyIdx + key.length());
            if (colonIdx < 0) return null;

            int valueStart = colonIdx + 1;
            // Skip whitespace
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }
            if (valueStart >= json.length()) return null;

            if (json.charAt(valueStart) == '"') {
                // Quoted string value
                int valueEnd = json.indexOf('"', valueStart + 1);
                if (valueEnd < 0) return null;
                return json.substring(valueStart + 1, valueEnd);
            } else {
                // Unquoted value (number, boolean, null)
                int valueEnd = valueStart;
                while (valueEnd < json.length()
                        && !Character.isWhitespace(json.charAt(valueEnd))
                        && json.charAt(valueEnd) != ','
                        && json.charAt(valueEnd) != '}') {
                    valueEnd++;
                }
                return json.substring(valueStart, valueEnd);
            }
        }
    }
}
