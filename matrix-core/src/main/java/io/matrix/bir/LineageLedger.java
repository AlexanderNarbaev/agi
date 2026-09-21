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
        VERIFY,
        /** ATMS/JTMS-style withdrawal: artifact no longer active (GLOSSARY §102). */
        RETRACT
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
    private final java.util.Map<String, java.util.Set<String>> justifications = new java.util.HashMap<>();
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
     * JTMS/ATMS-style retraction: appends a {@link Operation#RETRACT} entry
     * whose contentHash carries the retracted artifact's latest hash as the
     * justification link. The ledger stays append-only; activity is derived
     * via {@link #latestStatus()} / {@link #isRetracted(String)}.
     */
    public LedgerEntry retract(String birId) {
        byte[] justification = new byte[32];
        List<LedgerEntry> hist = getHistory(birId);
        if (!hist.isEmpty()) justification = hist.get(hist.size() - 1).contentHash().clone();
        return append(birId, Operation.RETRACT, justification, 0.0);
    }

    /**
     * ATMS-style label: last operation per BIR id in chain order.
     * An id whose last operation is {@link Operation#RETRACT} is OUT.
     */
    public java.util.Map<String, Operation> latestStatus() {
        var status = new java.util.LinkedHashMap<String, Operation>();
        for (LedgerEntry e : chain) status.put(e.birId(), e.op());
        return status;
    }

    /** True iff the artifact exists and its latest operation is RETRACT. */
    public boolean isRetracted(String birId) {
        return latestStatus().get(birId) == Operation.RETRACT;
    }

    /**
     * ATMS justification graph (GLOSSARY §102): registers that {@code birId}
     * DEPENDS ON {@code dependsOnBirId}. Dependencies live in a side-map —
     * the append-only hash chain stays untouched. Retracting a dependency
     * marks dependents as OUT via {@link #activeTransitively(String)}.
     */
    public void addJustification(String birId, String dependsOnBirId) {
        justifications.computeIfAbsent(birId, k -> new java.util.LinkedHashSet<>()).add(dependsOnBirId);
    }

    /** Direct dependencies of an artifact (empty if none). */
    public List<String> justificationsOf(String birId) {
        return List.copyOf(justifications.getOrDefault(birId, java.util.Set.of()));
    }

    /**
     * Transitive activity (ATMS label propagation): artifact is IN iff it is
     * not retracted AND every existing justification ancestor is IN.
     * Unknown ancestors are treated as IN.
     */
    public boolean activeTransitively(String birId) {
        return isActiveDfs(birId, new java.util.HashSet<>());
    }

    private boolean isActiveDfs(String id, java.util.Set<String> visiting) {
        if (!visiting.add(id)) return true; // cycle-safe: assume IN on revisit
        var status = latestStatus().get(id);
        if (status == Operation.RETRACT) return false;
        for (String dep : justifications.getOrDefault(id, java.util.Set.of())) {
            if (!isActiveDfs(dep, visiting)) return false;
        }
        return true;
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
