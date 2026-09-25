package io.matrix.brain.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * MIND-W7 — Federation registry (simplified).
 *
 * <p>Tracks known peer gateways and lets nodes exchange knowledge.
 * Two instances sharing the same backing directory form a federation
 * (each appends its share; knowledge exchange is just file-based
 * re-import). A future W7.4 wave may add gRPC / mDNS discovery; for now
 * the registry is file-backed so it survives restarts and works
 * across docker-compose services.</p>
 *
 * <p>CONSTITUTION Article VI — federation is observable: every peer is
 * recorded with name + endpoint + last-seen.</p>
 */
public final class FederationRegistry {

    public record Peer(String id, String name, String endpoint, long lastSeenMillis) {}

    private final Path registryPath;
    private final Map<String, Peer> peers = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public FederationRegistry(Path registryPath) {
        this.registryPath = registryPath;
        load();
    }

    /** Register a peer; idempotent on id. */
    public Peer register(String id, String name, String endpoint) {
        lock.writeLock().lock();
        try {
            Peer p = new Peer(id, name, endpoint, System.currentTimeMillis());
            peers.put(id, p);
            persist();
            return p;
        } finally {
            lock.writeLock().unlock(); }
    }

    /** Heartbeat — updates lastSeenMillis for {@code id}. */
    public Peer heartbeat(String id) {
        lock.writeLock().lock();
        try {
            Peer old = peers.get(id);
            if (old == null) return null;
            Peer p = new Peer(old.id, old.name, old.endpoint, System.currentTimeMillis());
            peers.put(id, p);
            persist();
            return p;
        } finally {
            lock.writeLock().unlock(); }
    }

    public Peer get(String id) {
        lock.readLock().lock();
        try { return peers.get(id); }
        finally { lock.readLock().unlock(); }
    }

    /** All known peers, most-recently-seen first. */
    public java.util.List<Peer> list() {
        lock.readLock().lock();
        try {
            java.util.List<Peer> all = new java.util.ArrayList<>(peers.values());
            all.sort((a, b) -> Long.compare(b.lastSeenMillis, a.lastSeenMillis));
            return all;
        } finally { lock.readLock().unlock(); }
    }

    public int size() {
        lock.readLock().lock();
        try { return peers.size(); }
        finally { lock.readLock().unlock(); }
    }

    public synchronized void persist() {
        try {
            Files.createDirectories(registryPath.getParent() == null
                ? Path.of(".") : registryPath.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("id,name,endpoint,lastSeenMillis\n");
            for (Peer p : peers.values()) {
                sb.append(esc(p.id)).append(',')
                  .append(esc(p.name)).append(',')
                  .append(esc(p.endpoint)).append(',')
                  .append(p.lastSeenMillis).append('\n');
            }
            Path tmp = registryPath.resolveSibling(registryPath.getFileName() + ".tmp");
            Files.writeString(tmp, sb.toString());
            Files.move(tmp, registryPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            throw new RuntimeException("FederationRegistry persist failed", ex);
        }
    }

    private void load() {
        if (!Files.exists(registryPath)) return;
        try {
            for (String line : Files.readAllLines(registryPath)) {
                if (line.isBlank() || line.startsWith("id,")) continue;
                String[] parts = line.split(",", 4);
                if (parts.length != 4) continue;
                peers.put(parts[0], new Peer(parts[0], parts[1], parts[2],
                    Long.parseLong(parts[3])));
            }
        } catch (Exception ex) {
            throw new RuntimeException("FederationRegistry load failed", ex);
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace(",", "%2C").replace("\n", " ");
    }
}
