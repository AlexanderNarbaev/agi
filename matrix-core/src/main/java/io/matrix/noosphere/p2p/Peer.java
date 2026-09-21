package io.matrix.noosphere.p2p;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a connected peer in the P2P network.
 */
public class Peer {

    private final String id;
    private final Socket socket;
    private final PrintWriter out;
    private final BufferedReader in;
    private final AtomicLong lastSeen = new AtomicLong(System.currentTimeMillis());
    private volatile boolean alive = true;

    public Peer(String id, Socket socket) throws IOException {
        this.id = id;
        this.socket = socket;
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public String getId() {
        return id;
    }

    public boolean isAlive() {
        return alive && !socket.isClosed() && 
               (System.currentTimeMillis() - lastSeen.get() < 60_000);
    }

    public void sendMessage(String message) {
        out.println(message);
        out.flush();
    }

    public String sendAndReceive(String message) throws IOException {
        out.println(message);
        out.flush();
        String response = in.readLine();
        lastSeen.set(System.currentTimeMillis());
        return response;
    }

    public void close() {
        alive = false;
        try { socket.close(); } catch (Exception ignored) {}
    }

    @Override
    public String toString() {
        return "Peer{id='" + id + "', alive=" + isAlive() + "}";
    }
}
