package io.matrix.lifecycle;

import io.matrix.federation.Anonymizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Subconscious consolidator (SPEC-007 / DESIGN-19, H-I): runs the
 * TR (test/replay) and REM (rapid replay) phases over a checkpoint,
 * with gossip M3→M4 of k-anonymous digests via the
 * {@link Anonymizer}.
 *
 * <p>Two filters gate outbound gossip:
 * <ol>
 *   <li><b>Integrity check</b> — the current state hash must match the
 *       checkpoint's recorded hash. Any mismatch short-circuits with
 *       a {@link PhaseResult#INTEGRITY_FAILED} verdict.</li>
 *   <li><b>k-anonymity</b> — the digest is gated by
 *       {@link Anonymizer#isAnonymous(String)}; below the threshold,
 *       the digest is suppressed (kept in the local backlog).</li>
 * </ol>
 *
 * <p>Deterministic: no randomness, no wall-clock.
 */
public final class SubconsciousConsolidator {

    /** A single consolidation phase result. */
    public enum PhaseResult {
        CONSOLIDATED,
        GOSSIP_SHARED,
        GOSSIP_SUPPRESSED,
        INTEGRITY_FAILED
    }

    /** Snapshot of one consolidation cycle. */
    public record ConsolidationReport(long cycleId,
                                      String contentHash,
                                      PhaseResult result,
                                      int digestsShared,
                                      int digestsSuppressed) {
        public ConsolidationReport {
            Objects.requireNonNull(contentHash, "contentHash");
            Objects.requireNonNull(result, "result");
        }
    }

    private final Anonymizer anonymizer;
    private final String nodeId;
    private final AtomicLong cycleCounter = new AtomicLong();

    /** Cached checkpoint hash from the previous integrity-check. */
    private volatile String lastCheckpointHash;

    public SubconsciousConsolidator(Anonymizer anonymizer, String nodeId) {
        this.anonymizer = Objects.requireNonNull(anonymizer, "anonymizer");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
    }

    /**
     * Run one consolidation cycle over a checkpoint.
     *
     * @param checkpoint key/value map of the current state to consolidate
     * @return a {@link ConsolidationReport}
     */
    public ConsolidationReport runOnce(Map<String, String> checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        long cycleId = cycleCounter.incrementAndGet();
        String hash = sha256Hex(String.join("\n", sortedEntries(checkpoint)));

        // 1. integrity check
        if (lastCheckpointHash != null && !lastCheckpointHash.equals(hash)) {
            return new ConsolidationReport(cycleId, hash,
                    PhaseResult.INTEGRITY_FAILED, 0, 0);
        }
        lastCheckpointHash = hash;

        // 2. TR phase: passive consolidation (no-op for the minimal impl,
        //    the digest is recorded for outbound gossip).
        anonymizer.recordContribution(hash, nodeId);

        // 3. REM phase: active replay — for now just emits the same digest
        //    a configurable number of times.
        int digestsShared = 0;
        int digestsSuppressed = 0;
        // minimal replay: one replay per checkpoint entry
        for (var entry : checkpoint.entrySet()) {
            String replayHash = sha256Hex(entry.getKey() + "=" + entry.getValue());
            anonymizer.recordContribution(replayHash, nodeId);
            if (anonymizer.isAnonymous(replayHash)) {
                digestsShared++;
            } else {
                digestsSuppressed++;
            }
        }

        PhaseResult result;
        if (anonymizer.isAnonymous(hash)) {
            result = PhaseResult.GOSSIP_SHARED;
        } else if (digestsShared > 0) {
            result = PhaseResult.GOSSIP_SHARED;
        } else {
            result = PhaseResult.GOSSIP_SUPPRESSED;
        }
        return new ConsolidationReport(cycleId, hash, result,
                digestsShared, digestsSuppressed);
    }

    private static List<String> sortedEntries(Map<String, String> m) {
        TreeMap<String, String> sorted = new TreeMap<>(m);
        return new ArrayList<>(sorted.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList());
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public long totalCycles() { return cycleCounter.get(); }
}