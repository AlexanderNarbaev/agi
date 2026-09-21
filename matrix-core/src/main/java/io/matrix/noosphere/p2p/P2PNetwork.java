package io.matrix.noosphere.p2p;

import io.matrix.noosphere.FnlPackage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * P2P network for decentralized Noosphere knowledge exchange.
 * 
 * Implements mDNS-style peer discovery and direct TCP communication
 * for sharing FNL packages between Matrix nodes.
 * 
 * @see <a href="docs/improvements/P2P_NOOSPHERE.md">P2P Noosphere Plan</a>
 */
@ApplicationScoped
public class P2PNetwork {

    private static final Logger log = LoggerFactory.getLogger(P2PNetwork.class);
    private static final String MULTICAST_GROUP = "239.255.255.250";
    private static final int MULTICAST_PORT = 9093;
    private static final int TCP_PORT = 9094;

    private final Map<String, Peer> peers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private DatagramSocket multicastSocket;
    private ServerSocket tcpServer;
    private volatile boolean running = false;

    @PostConstruct
    void start() {
        running = true;
        startMulticastListener();
        startTcpServer();
        startHeartbeat();
        log.info("P2PNetwork started on TCP:{}, Multicast:{}", TCP_PORT, MULTICAST_PORT);
    }

    @PreDestroy
    void stop() {
        running = false;
        scheduler.shutdownNow();
        closeQuietly(multicastSocket);
        closeQuietly(tcpServer);
        log.info("P2PNetwork stopped");
    }

    /**
     * Broadcast knowledge to all connected peers.
     */
    public void broadcastKnowledge(FnlPackage pkg) {
        String message = serializePackage(pkg);
        for (Peer peer : peers.values()) {
            if (peer.isAlive()) {
                try {
                    peer.sendMessage(message);
                } catch (Exception e) {
                    log.warn("Failed to send to peer {}: {}", peer.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Request knowledge from all peers on a topic.
     */
    public List<FnlPackage> requestKnowledge(String topic) {
        List<FnlPackage> results = new ArrayList<>();
        String query = "{\"type\":\"query\",\"topic\":\"" + topic + "\"}";
        
        for (Peer peer : peers.values()) {
            if (peer.isAlive()) {
                try {
                    String response = peer.sendAndReceive(query);
                    results.addAll(deserializePackages(response));
                } catch (Exception e) {
                    log.debug("Query failed for peer {}: {}", peer.getId(), e.getMessage());
                }
            }
        }
        return results;
    }

    /**
     * Get list of connected peers.
     */
    public List<Peer> getPeers() {
        return new ArrayList<>(peers.values());
    }

    /**
     * Get peer count.
     */
    public int getPeerCount() {
        return peers.size();
    }

    private void startMulticastListener() {
        try {
            multicastSocket = new DatagramSocket(MULTICAST_PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            // Note: Multicast not fully implemented - using TCP direct connection
            scheduler.scheduleWithFixedDelay(this::discoverPeers, 0, 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Multicast listener failed: {}", e.getMessage());
        }
    }

    private void startTcpServer() {
        try {
            tcpServer = new ServerSocket(TCP_PORT);
            Thread acceptThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket client = tcpServer.accept();
                        handleIncomingConnection(client);
                    } catch (Exception e) {
                        if (running) log.debug("TCP accept error: {}", e.getMessage());
                    }
                }
            }, "p2p-tcp-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        } catch (Exception e) {
            log.warn("TCP server failed: {}", e.getMessage());
        }
    }

    private void startHeartbeat() {
        scheduler.scheduleWithFixedDelay(() -> {
            peers.values().removeIf(p -> !p.isAlive());
            log.debug("Peers: {}", peers.size());
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void discoverPeers() {
        // mDNS discovery placeholder - in production use JmDNS
        log.trace("Peer discovery cycle");
    }

    private void handleIncomingConnection(Socket socket) {
        try {
            String peerId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            Peer peer = new Peer(peerId, socket);
            peers.put(peerId, peer);
            log.info("Peer connected: {}", peerId);
        } catch (Exception e) {
            log.warn("Connection handling failed: {}", e.getMessage());
        }
    }

    private String serializePackage(FnlPackage pkg) {
        return "{\"type\":\"knowledge\",\"id\":\"" + pkg.id() + "\",\"name\":\"" + pkg.name() + "\",\"hash\":\"" + pkg.snapshotHash() + "\"}";
    }

    private List<FnlPackage> deserializePackages(String json) {
        // Simplified deserialization
        return Collections.emptyList();
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (Exception ignored) {}
        }
    }
}
