package io.matrix.audit;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Immutable link in a cryptographic hash chain.
 *
 * <p>Each {@code HashLink} references the previous link by hash (the
 * "previousHash" field), forming a tamper-evident chain — any change
 * to a historical link invalidates every subsequent link.
 *
 * <p>Used to:
 * <ul>
 *   <li>Audit the FROZEN FNL network: every canonical-neuron modification
 *       (or attestation that none occurred) is recorded as a new link.</li>
 *   <li>GDPR-style immutable event logs: tombstones, deletions, exports
 *       can be chained so that any retroactive tampering is detectable.</li>
 *   <li>Lightweight "blockchain" without consensus — sufficient for
 *       internal audit trails where trust is local to the operator.</li>
 * </ul>
 *
 * <p>Hash function: SHA-256 over a canonical byte encoding of the link
 * fields. The encoding is:
 * <pre>
 *   previousHash | sequence | payloadHash | timestamp | extra
 * </pre>
 * (each field length-prefixed to prevent ambiguity).
 *
 * <p>This is a small, self-contained implementation — no blockchain
 * framework dependency. Suitable for both embedded use and unit tests.
 */
public record HashLink(
        long sequence,
        String previousHash,
        String payloadHash,
        long timestampMs,
        String extra,
        String hash) {

    public HashLink {
        if (sequence < 0) throw new IllegalArgumentException("sequence must be >= 0");
        Objects.requireNonNull(previousHash, "previousHash");
        Objects.requireNonNull(payloadHash, "payloadHash");
        Objects.requireNonNull(timestampMs >= 0 ? "ok" : "ts", "timestampMs");
        Objects.requireNonNull(extra, "extra");
        Objects.requireNonNull(hash, "hash");
    }

    /** Length (bytes) of the fixed-width sequence field. */
    private static final int SEQ_BYTES = 8;
    /** Length (bytes) of the fixed-width timestamp field. */
    private static final int TS_BYTES = 8;
    /** Length (bytes) of each length-prefix. */
    private static final int PREFIX_BYTES = 4;

    /**
     * Factory: build a new link that extends the given previous link
     * (or null for the genesis link).
     */
    public static HashLink extend(HashLink previous, String payload, String extra) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(extra, "extra");
        String previousHash = previous == null
                ? "0000000000000000000000000000000000000000000000000000000000000000"
                : previous.hash();
        long sequence = previous == null ? 0L : previous.sequence() + 1;
        long now = System.currentTimeMillis();
        String payloadHash = sha256Hex(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String hash = computeHash(previousHash, sequence, payloadHash, now, extra);
        return new HashLink(sequence, previousHash, payloadHash, now, extra, hash);
    }

    /**
     * Verify the hash is consistent with the link's fields. Returns
     * {@code true} when the hash matches the canonical encoding.
     */
    public boolean verify() {
        String expected = computeHash(previousHash, sequence, payloadHash, timestampMs, extra);
        return constantTimeEquals(expected, hash);
    }

    /**
     * Build a link with a pre-computed hash (used for tests and for
     * reconstructing chains from persistent storage).
     */
    public static HashLink reconstruct(long sequence, String previousHash, String payloadHash,
                                        long timestampMs, String extra, String hash) {
        return new HashLink(sequence, previousHash, payloadHash, timestampMs, extra, hash);
    }

    /** {@code "0000…0000"} (64 zero hex chars) — the genesis predecessor. */
    public static String genesisHash() {
        return "0".repeat(64);
    }

    // ── Hash computation ──

    static String computeHash(String previousHash, long sequence, String payloadHash,
                                long timestampMs, String extra) {
        try {
            byte[] prevBytes = hexDecode(previousHash);
            byte[] payBytes = hexDecode(payloadHash);
            byte[] extraBytes = extra.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            byte[] seqBytes = longToBytes(sequence);
            byte[] tsBytes = longToBytes(timestampMs);

            byte[] prevLen = intToBytes(prevBytes.length);
            byte[] payLen = intToBytes(payBytes.length);
            byte[] extraLen = intToBytes(extraBytes.length);

            byte[] data = new byte[prevLen.length + prevBytes.length
                    + seqBytes.length
                    + payLen.length + payBytes.length
                    + tsBytes.length
                    + extraLen.length + extraBytes.length];
            int off = 0;
            System.arraycopy(prevLen, 0, data, off, prevLen.length); off += prevLen.length;
            System.arraycopy(prevBytes, 0, data, off, prevBytes.length); off += prevBytes.length;
            System.arraycopy(seqBytes, 0, data, off, seqBytes.length); off += seqBytes.length;
            System.arraycopy(payLen, 0, data, off, payLen.length); off += payLen.length;
            System.arraycopy(payBytes, 0, data, off, payBytes.length); off += payBytes.length;
            System.arraycopy(tsBytes, 0, data, off, tsBytes.length); off += tsBytes.length;
            System.arraycopy(extraLen, 0, data, off, extraLen.length); off += extraLen.length;
            System.arraycopy(extraBytes, 0, data, off, extraBytes.length);

            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] hexDecode(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >>> 24), (byte) (value >>> 16),
                (byte) (value >>> 8), (byte) value
        };
    }

    private static byte[] longToBytes(long value) {
        return new byte[]{
                (byte) (value >>> 56), (byte) (value >>> 48),
                (byte) (value >>> 40), (byte) (value >>> 32),
                (byte) (value >>> 24), (byte) (value >>> 16),
                (byte) (value >>> 8), (byte) value
        };
    }

    /** Constant-time string comparison to defeat timing-side-channels. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }

    /** Useful in tests and audit reports. */
    public String toDisplayString() {
        return String.format("HashLink[seq=%d ts=%d hash=%s.. prev=%s..]",
                sequence, timestampMs,
                hash.length() > 12 ? hash.substring(0, 12) : hash,
                previousHash.length() > 12 ? previousHash.substring(0, 12) : previousHash);
    }
}
