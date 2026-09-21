package io.matrix.noosphere.p2p;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Peer discovery service for P2P network.
 * 
 * Implements simple TCP-based peer discovery where peers
 * can announce themselves and discover others.
 */
@ApplicationScoped
public class PeerDiscovery {

    private static final Logger log = LoggerFactory.getLogger(PeerDiscovery.class);

    private final Map<String, InetSocketAddress> discoveredPeers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running = false;

    /**
     * Start discovery service on specified port.
     */
    public void start(int port) {
        running = true;
        executor.submit(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                log.info("PeerDiscovery listening on port {}", port);
                while (running) {
                    try {
                        Socket client = server.accept();
                        handleDiscovery(client);
                    } catch (IOException e) {
                        if (running) log.debug("Discovery accept error: {}", e.getMessage());
                    }
                }
            } catch (IOException e) {
                log.warn("PeerDiscovery failed to start: {}", e.getMessage());
            }
        });
    }

    /**
     * Stop discovery service.
     */
    public void stop() {
        running = false;
        executor.shutdownNow();
    }

    /**
     * Announce this peer to a remote peer.
     */
    public void announce(String host, int port, String peerId) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            var out = socket.getOutputStream();
            out.write(("ANNOUNCE:" + peerId + "\n").getBytes());
            out.flush();
        } catch (IOException e) {
            log.debug("Announce to {}:{} failed: {}", host, port, e.getMessage());
        }
    }

    /**
     * Get all discovered peers.
     */
    public Map<String, InetSocketAddress> getDiscoveredPeers() {
        return Map.copyOf(discoveredPeers);
    }

    /**
     * Clear discovered peers.
     */
    public void clear() {
        discoveredPeers.clear();
    }

    private void handleDiscovery(Socket client) {
        try {
            var in = new java.io.BufferedReader(new java.io.InputStreamReader(client.getInputStream()));
            String line = in.readLine();
            if (line != null && line.startsWith("ANNOUNCE:")) {
                String peerId = line.substring(9);
                discoveredPeers.put(peerId, new InetSocketAddress(
                        client.getInetAddress().getHostAddress(), client.getPort()));
                log.info("Peer discovered: {} from {}:{}", peerId,
                        client.getInetAddress().getHostAddress(), client.getPort());
            }
            client.close();
        } catch (IOException e) {
            log.debug("Discovery handling error: {}", e.getMessage());
        }
    }
}
