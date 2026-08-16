package io.matrix.bir;

import java.security.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Append-only lineage ledger for BIR artifacts.
 *
 * <p>Per CONSTITUTION Article V (Genesis Protocol): every BIR transformation
 * is recorded in an append-only ledger with cryptographic signatures for
 * tamper detection and auditability.
 *
 * <p>Each entry contains:
 * <ul>
 *   <li>{@code birId} — the BIR this operation applies to</li>
 *   <li>{@code operation} — the transformation (CREATE, TT_TO_BDD, BDD_TO_TT, etc.)</li>
 *   <li>{@code timestamp} — when the operation occurred</li>
 *   <li>{@code prevHash} — SHA-256 hash of the previous entry (chain link)</li>
 *   <li>{@code contentHash} — content hash of the resulting BIR</li>
 *   <li>{@code phi} — monotonic functional (CONSTITUTION Article III)</li>
 *   <li>{@code signature} — Ed25519 signature over (prevHash || contentHash || phi)</li>
 * </ul>
 *
 * <p>The chain is verifiable: each entry's prevHash must match the computed
 * hash of the preceding entry. Tampering anywhere in the chain breaks all
 * subsequent links.
 *
 * <p>Thread safety: CopyOnWriteArrayList for the chain (append-mostly workload).
 * Signature operations use java.security (Ed25519, SHA-256).
 */
public final class LineageLedger {

    /** Operation types for BIR lineage. */
    public enum Operation {
        CREATE,
        TT_TO_BDD,
        BDD_TO_TT,
        TT_TO_CLAUSESET,
        CLAUSESET_TO_TT,
        COMPOSE,
        MUTATE,
        VERIFY
    }

    /** A single ledger entry. Immutable. */
    public record LedgerEntry(
            String birId,
            Operation op,
            long timestamp,
            byte[] prevHash,
            byte[] contentHash,
            double phi,
            byte[] signature
    ) {
        /** Computes the hash of this entry for chain linking. */
        public byte[] computeHash() {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(birId.getBytes());
                md.update(op.name().getBytes());
                md.update(longToBytes(timestamp));
                if (prevHash != null) md.update(prevHash);
                md.update(contentHash);
                md.update(doubleToBytes(phi));
                return md.digest();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        }
    }

    private final List<LedgerEntry> chain = new CopyOnWriteArrayList<>();
    private final KeyPair signingKey;

    /**
     * Creates a new ledger with a generated Ed25519 key pair.
     */
    public LineageLedger() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            this.signingKey = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 unavailable", e);
        }
    }

    /**
     * Creates a ledger with an existing Ed25519 key pair.
     */
    public LineageLedger(KeyPair signingKey) {
        this.signingKey = Objects.requireNonNull(signingKey, "signingKey");
    }

    /**
     * Appends an operation to the ledger.
     *
     * @param birId       BIR identifier
     * @param op          operation type
     * @param contentHash resulting BIR content hash (SHA3-256)
     * @param phi         monotonic functional value
     * @return the appended ledger entry with signature
     */
    public LedgerEntry append(String birId, Operation op, byte[] contentHash, double phi) {
        byte[] prevHash = chain.isEmpty() ? null : chain.get(chain.size() - 1).computeHash();

        // Compute signature: sign(prevHash || contentHash || phi)
        byte[] signature = sign(prevHash, contentHash, phi);

        LedgerEntry entry = new LedgerEntry(
                birId, op, System.currentTimeMillis(),
                prevHash != null ? prevHash.clone() : null,
                contentHash.clone(),
                phi,
                signature
        );

        chain.add(entry);
        return entry;
    }

    /**
     * Verifies the entire chain integrity.
     *
     * @return list of corrupt entries (empty = all valid)
     */
    public List<Integer> verifyChain() {
        List<Integer> corrupt = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            LedgerEntry entry = chain.get(i);

            // Check prevHash link
            if (i > 0) {
                byte[] expectedPrev = chain.get(i - 1).computeHash();
                if (!Arrays.equals(entry.prevHash(), expectedPrev)) {
                    corrupt.add(i);
                    continue;
                }
            }

            // Verify signature
            byte[] prevHash = entry.prevHash();
            if (!verify(prevHash, entry.contentHash(), entry.phi(), entry.signature())) {
                corrupt.add(i);
            }
        }
        return corrupt;
    }

    /**
     * Returns the history for a specific BIR ID.
     */
    public List<LedgerEntry> getHistory(String birId) {
        List<LedgerEntry> result = new ArrayList<>();
        for (LedgerEntry entry : chain) {
            if (entry.birId().equals(birId)) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Returns the full chain (read-only).
     */
    public List<LedgerEntry> chain() {
        return List.copyOf(chain);
    }

    /**
     * Returns the number of entries.
     */
    public int size() {
        return chain.size();
    }

    /**
     * Returns the public key for signature verification.
     */
    public PublicKey publicKey() {
        return signingKey.getPublic();
    }

    // ─── crypto helpers ───

    private byte[] sign(byte[] prevHash, byte[] contentHash, double phi) {
        try {
            byte[] data = buildSignatureData(prevHash, contentHash, phi);
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(signingKey.getPrivate());
            sig.update(data);
            return sig.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signing failed", e);
        }
    }

    private boolean verify(byte[] prevHash, byte[] contentHash, double phi, byte[] signature) {
        try {
            byte[] data = buildSignatureData(prevHash, contentHash, phi);
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(signingKey.getPublic());
            sig.update(data);
            return sig.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static byte[] buildSignatureData(byte[] prevHash, byte[] contentHash, double phi) {
        byte[] phiBytes = doubleToBytes(phi);
        int len = (prevHash != null ? prevHash.length : 0) + contentHash.length + phiBytes.length;
        byte[] data = new byte[len];
        int pos = 0;
        if (prevHash != null) {
            System.arraycopy(prevHash, 0, data, pos, prevHash.length);
            pos += prevHash.length;
        }
        System.arraycopy(contentHash, 0, data, pos, contentHash.length);
        pos += contentHash.length;
        System.arraycopy(phiBytes, 0, data, pos, phiBytes.length);
        return data;
    }

    private static byte[] longToBytes(long v) {
        return new byte[]{
                (byte) (v >>> 56), (byte) (v >>> 48), (byte) (v >>> 40), (byte) (v >>> 32),
                (byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v
        };
    }

    private static byte[] doubleToBytes(double v) {
        return longToBytes(Double.doubleToLongBits(v));
    }
}
