package io.matrix.federation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pluggable transport for the federation layer. Production code
 * uses a real network (TCP/HTTP); tests use an in-memory queue.
 * The current {@link FileSystemMessageBus} writes each dispatched
 * digest to a per-peer file as a JSON line — this is the simplest
 * transport that's actually exercised on disk and visible to the
 * user (so federation activity is auditable without a live peer).
 */
public interface MessageBus {

    /** Send a digest to a peer. Returns true on success. */
    boolean send(String peerId, String content) throws IOException;

    /** Whether the bus is configured and ready (e.g. a real socket
     *  is open, or a directory exists). */
    boolean isAvailable();
}

/**
 * File-system-backed bus: writes each message to
 * {@code <root>/<peerId>/<timestamp>.jsonl}. Auditable, no live peer
 * required, works on disk for both the test and the live system.
 */
final class FileSystemMessageBus implements MessageBus {

    private final Path root;

    FileSystemMessageBus(Path root) {
        this.root = root;
    }

    @Override
    public boolean send(String peerId, String content) throws IOException {
        Files.createDirectories(root.resolve(peerId));
        Path target = root.resolve(peerId)
                .resolve(System.currentTimeMillis() + "-" + Long.toHexString(content.hashCode()) + ".jsonl");
        Files.writeString(target, content + "\n");
        return true;
    }

    @Override
    public boolean isAvailable() {
        try {
            Files.createDirectories(root);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

/** In-memory bus for unit tests. */
final class InMemoryMessageBus implements MessageBus {

    final Map<String, java.util.List<String>> sent = new ConcurrentHashMap<>();

    @Override
    public boolean send(String peerId, String content) {
        sent.computeIfAbsent(peerId, k -> new java.util.ArrayList<>()).add(content);
        return true;
    }

    @Override
    public boolean isAvailable() { return true; }
}