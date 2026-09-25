package io.matrix.brain.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * MIND-W7 — Lightweight SHA-256 audit chain.
 *
 * <p>Each entry contains a hash of the previous entry, forming a
 * tamper-evident chain (CONSTITUTION Article VIII — every decision is
 * auditable). This is a focused, lightweight chain suitable for the
 * gateway's audit log; the full {@code matrix-audit/HashChainedLog}
 * handles the heavy-weight policy chain.</p>
 */
public final class AuditChain {

    public record Entry(
        long index,
        long timestampMillis,
        String action,
        String details,
        String prevHash,
        String hash
    ) {}

    private final List<Entry> chain = new ArrayList<>();
    private long index = 0;

    public synchronized Entry append(String action, String details) {
        String prevHash = chain.isEmpty() ? "GENESIS" : chain.get(chain.size() - 1).hash();
        long ts = System.currentTimeMillis();
        index++;
        String hash = sha256Hex(index + "|" + ts + "|" + action + "|" + details + "|" + prevHash);
        Entry e = new Entry(index, ts, action, details, prevHash, hash);
        chain.add(e);
        return e;
    }

    public synchronized Entry get(long idx) {
        if (idx < 1 || idx > chain.size()) return null;
        return chain.get((int) idx - 1);
    }

    public synchronized int size() {
        return chain.size();
    }

    public synchronized List<Entry> all() {
        return new ArrayList<>(chain);
    }

    /** Verify the chain: every entry's hash must match SHA-256 of its content. */
    public synchronized boolean verify() {
        String prev = "GENESIS";
        for (Entry e : chain) {
            String expected = sha256Hex(e.index + "|" + e.timestampMillis + "|"
                + e.action + "|" + e.details + "|" + prev);
            if (!expected.equals(e.hash)) return false;
            if (!e.prevHash.equals(prev)) return false;
            prev = e.hash;
        }
        return true;
    }

    /** Test helper: tamper with the entry at {@code idx}. */
    public synchronized void tamper(long idx, String newDetails) {
        if (idx < 1 || idx > chain.size()) throw new IllegalArgumentException();
        Entry old = chain.get((int) idx - 1);
        Entry tampered = new Entry(old.index, old.timestampMillis, old.action,
            newDetails, old.prevHash, old.hash);
        chain.set((int) idx - 1, tampered);
    }

    private static String sha256Hex(String s) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            return "no-sha256";
        }
    }
}
