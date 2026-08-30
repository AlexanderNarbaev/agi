package io.matrix.federation;

import io.matrix.memory.HierarchicalMemory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Knowledge share pipeline (Wave F): gossip M3→M4 digest sync
 * between MATRIX nodes. The {@link Anonymizer} keeps the digests
 * k-anonymous; this class orchestrates the digests dispatched
 * to peer nodes (stubs for now — peer wiring requires net code
 * that's out of scope).
 */
public final class KnowledgeShare {

    private final Anonymizer anonymizer;
    private final HierarchicalMemory memory;
    private final AtomicLong digestsDispatched = new AtomicLong();
    private final AtomicLong digestsDropped = new AtomicLong();

    public KnowledgeShare(Anonymizer anonymizer, HierarchicalMemory memory) {
        this.anonymizer = Objects.requireNonNull(anonymizer, "anonymizer");
        this.memory = memory;
    }

    /**
     * Produce a digest bundle: the current Anonymizer snapshot +
     * memory-level statistics. Returns the entries that pass the
     * k-anonymity gate.
     */
    public List<DecentralizedDigestPipeline.Digest> digestBundle() {
        var digests = new DecentralizedDigestPipeline(anonymizer, 1.0).emitDigests();
        digestsDispatched.addAndGet(digests.size());
        return digests;
    }

    /** Dispatch a digest to a peer (stubs; peer wiring TBD). */
    public void dispatchToPeer(DecentralizedDigestPipeline.Digest digest, String peerId) {
        // k-anonymous dispatch: only if the digest is shared
        if (!digest.shared()) {
            digestsDropped.incrementAndGet();
            return;
        }
        digestsDispatched.incrementAndGet();
    }

    public long digestsDispatched() { return digestsDispatched.get(); }
    public long digestsDropped() { return digestsDropped.get(); }
}