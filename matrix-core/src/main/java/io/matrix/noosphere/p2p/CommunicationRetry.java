package io.matrix.noosphere.p2p;

import io.matrix.noosphere.FnlPackage;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * Manages retry logic for failed peer communications in the P2P network.
 * 
 * Implements exponential backoff with jitter for reliable message delivery.
 */
@ApplicationScoped
public class CommunicationRetry {

    private static final Logger log = LoggerFactory.getLogger(CommunicationRetry.class);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final int maxRetries = 3;
    private final long baseDelayMs = 100;

    /**
     * Send with retry to a peer.
     */
    public boolean sendWithRetry(Peer peer, FnlPackage pkg) {
        Exception lastError = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String message = serialize(pkg);
                peer.sendMessage(message);
                return true;
            } catch (Exception e) {
                lastError = e;
                long delay = computeDelay(attempt);
                log.debug("Send failed (attempt {}/{}): {} — retrying in {}ms", 
                        attempt, maxRetries, e.getMessage(), delay);
                
                try { Thread.sleep(delay); } 
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); return false; }
            }
        }
        
        log.warn("Send failed after {} attempts: {}", maxRetries, 
                lastError != null ? lastError.getMessage() : "unknown");
        return false;
    }

    /**
     * Send asynchronously with retry.
     */
    public CompletableFuture<Boolean> sendWithRetryAsync(Peer peer, FnlPackage pkg) {
        return CompletableFuture.supplyAsync(() -> sendWithRetry(peer, pkg));
    }

    private long computeDelay(int attempt) {
        // Exponential backoff with jitter: base * 2^attempt + random
        long delay = baseDelayMs * (1L << attempt);
        long jitter = ThreadLocalRandom.current().nextLong(delay / 2);
        return delay + jitter;
    }

    private String serialize(FnlPackage pkg) {
        return "{\"type\":\"knowledge\",\"id\":\"" + pkg.id() + "\",\"name\":\"" + pkg.name() + "\"}";
    }
}
