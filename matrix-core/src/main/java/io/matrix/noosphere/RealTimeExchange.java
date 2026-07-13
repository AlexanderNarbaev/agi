package io.matrix.noosphere;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Real-time FNL exchange between MATRIX instances using WebSocket pub/sub.
 *
 * <p>Enables live sharing of Functional Neural Lobes without central server.
 * Each instance runs a local pub/sub bus and can connect to remote instances
 * via WebSocket for cross-node FNL distribution.
 *
 * <p>Ref: L6_Memory.md §6, Noosphere concept
 */
public final class RealTimeExchange {

    private static final Logger LOG = LoggerFactory.getLogger(RealTimeExchange.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, List<Consumer<FnlPackage>>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, FnlPackage> publishedPackages = new ConcurrentHashMap<>();
    private final List<RemoteConnection> remoteConnections = new CopyOnWriteArrayList<>();
    private final String nodeId;

    public RealTimeExchange(String nodeId) {
        this.nodeId = nodeId;
    }

    public String nodeId() {
        return nodeId;
    }

    /**
     * Publishes an FNL package locally and forwards it to all connected remote nodes.
     */
    public void publish(FnlPackage pkg) {
        publishedPackages.put(pkg.name(), pkg);
        String channel = "fnl:" + pkg.type();
        var subs = subscribers.getOrDefault(channel, List.of());
        for (Consumer<FnlPackage> sub : subs) {
            sub.accept(pkg);
        }
        broadcastToRemotes(pkg);
    }

    public void subscribe(String channel, Consumer<FnlPackage> handler) {
        subscribers.computeIfAbsent(channel, k -> new ArrayList<>()).add(handler);
    }

    public void unsubscribe(String channel, Consumer<FnlPackage> handler) {
        var subs = subscribers.get(channel);
        if (subs != null) subs.remove(handler);
    }

    public List<FnlPackage> discover(String typePattern) {
        return publishedPackages.values().stream()
                .filter(p -> p.type().contains(typePattern))
                .toList();
    }

    /**
     * Connects to a remote RealTimeExchange instance via WebSocket.
     *
     * @param remoteUri WebSocket URI (e.g., ws://host:port/noosphere/exchange)
     * @return true if connection was established
     */
    public boolean connectTo(String remoteUri) {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            RemoteEndpoint.Basic remote = null;
            // Use Jakarta WebSocket client to connect
            Session session = container.connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {
                    session.addMessageHandler(String.class, message -> {
                        handleRemoteMessage(message);
                    });
                    LOG.info("Connected to remote exchange at {}", remoteUri);
                }

                @Override
                public void onClose(Session session, CloseReason closeReason) {
                    remoteConnections.removeIf(rc -> rc.session() == session);
                    LOG.info("Disconnected from remote exchange: {}", closeReason.getReasonPhrase());
                }

                @Override
                public void onError(Session session, Throwable thr) {
                    LOG.error("WebSocket error with {}: {}", remoteUri, thr.getMessage());
                }
            }, URI.create(remoteUri));

            remoteConnections.add(new RemoteConnection(remoteUri, session, session.getBasicRemote()));
            return true;
        } catch (Exception e) {
            LOG.warn("Failed to connect to remote exchange at {}: {}", remoteUri, e.getMessage());
            return false;
        }
    }

    /**
     * Disconnects from a remote exchange node.
     */
    public void disconnectFrom(String remoteUri) {
        remoteConnections.removeIf(rc -> {
            if (rc.uri().equals(remoteUri)) {
                try {
                    rc.session().close();
                } catch (Exception e) {
                    LOG.debug("Error closing connection to {}: {}", remoteUri, e.getMessage());
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Returns the number of active remote connections.
     */
    public int remoteConnectionCount() {
        return remoteConnections.size();
    }

    public Map<String, Integer> stats() {
        Map<String, Integer> s = new LinkedHashMap<>();
        s.put("published_count", publishedPackages.size());
        s.put("subscriber_count", subscribers.values().stream().mapToInt(List::size).sum());
        s.put("channels", subscribers.size());
        s.put("remote_connections", remoteConnections.size());
        return s;
    }

    // ─── internal ───

    private void broadcastToRemotes(FnlPackage pkg) {
        if (remoteConnections.isEmpty()) return;
        try {
            String json = MAPPER.writeValueAsString(Map.of(
                    "type", "fnl_publish",
                    "sourceNode", nodeId,
                    "name", pkg.name(),
                    "fnlType", pkg.type(),
                    "accuracy", pkg.accuracy(),
                    "authorInstanceId", pkg.authorInstanceId(),
                    "certified", pkg.certified()
            ));
            for (RemoteConnection rc : remoteConnections) {
                try {
                    rc.remote().sendText(json);
                } catch (Exception e) {
                    LOG.warn("Failed to send to {}: {}", rc.uri(), e.getMessage());
                }
            }
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize FNL package for broadcast", e);
        }
    }

    private void handleRemoteMessage(String message) {
        try {
            var node = MAPPER.readTree(message);
            String type = node.has("type") ? node.get("type").asText() : "";
            if ("fnl_publish".equals(type)) {
                FnlPackage pkg = FnlPackage.builder()
                        .name(node.get("name").asText())
                        .type(node.get("fnlType").asText())
                        .accuracy(node.get("accuracy").asDouble())
                        .authorInstanceId(node.get("authorInstanceId").asText())
                        .certified(node.has("certified") && node.get("certified").asBoolean())
                        .build();
                // Publish locally without re-broadcasting to remotes
                publishedPackages.put(pkg.name(), pkg);
                String channel = "fnl:" + pkg.type();
                var subs = subscribers.getOrDefault(channel, List.of());
                for (Consumer<FnlPackage> sub : subs) {
                    sub.accept(pkg);
                }
                LOG.debug("Received remote FNL {} from {}", pkg.name(),
                        node.has("sourceNode") ? node.get("sourceNode").asText() : "unknown");
            }
        } catch (Exception e) {
            LOG.warn("Failed to handle remote message: {}", e.getMessage());
        }
    }

    private record RemoteConnection(String uri, Session session, RemoteEndpoint.Basic remote) {}
}
