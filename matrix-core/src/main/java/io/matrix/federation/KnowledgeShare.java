package io.matrix.federation;

import io.matrix.memory.HierarchicalMemory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Knowledge share pipeline (Wave F + Wave L fix): gossip M3→M4
 * digest sync between MATRIX nodes via a pluggable {@link MessageBus}.
 *
 * <p>The {@link Anonymizer} keeps the digests k-anonymous; this class
 * orchestrates dispatching them to peer nodes over a real transport
 * (default: filesystem-backed — see {@link FileSystemMessageBus}).
 */
public final class KnowledgeShare {

    private final Anonymizer anonymizer;
    private final HierarchicalMemory memory;
    private final MessageBus bus;
    private final AtomicLong digestsDispatched = new AtomicLong();
    private final AtomicLong digestsDropped = new AtomicLong();
    private final AtomicLong sendErrors = new AtomicLong();

    public KnowledgeShare(Anonymizer anonymizer, HierarchicalMemory memory,
                          MessageBus bus) {
        this.anonymizer = Objects.requireNonNull(anonymizer, "anonymizer");
        this.memory = memory;
        this.bus = Objects.requireNonNull(bus, "bus");
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

    /**
     * Dispatch a digest to a peer. The peer receives a JSON-encoded
     * line via the configured {@link MessageBus} (filesystem in
     * production; in-memory in tests).
     *
     * @return true if the message was successfully sent; false if the
     *         digest was dropped (k-anonymous threshold not met) or the
     *         transport failed.
     */
    public boolean dispatchToPeer(DecentralizedDigestPipeline.Digest digest, String peerId) {
        // k-anonymous dispatch: only if the digest is shared
        if (!digest.shared()) {
            digestsDropped.incrementAndGet();
            return false;
        }
        if (!bus.isAvailable()) {
            digestsDropped.incrementAndGet();
            return false;
        }
        String content = serializeDigest(digest, peerId);
        try {
            boolean ok = bus.send(peerId, content);
            if (ok) {
                digestsDispatched.incrementAndGet();
            } else {
                digestsDropped.incrementAndGet();
            }
            return ok;
        } catch (IOException e) {
            sendErrors.incrementAndGet();
            digestsDropped.incrementAndGet();
            return false;
        }
    }

    private static String serializeDigest(DecentralizedDigestPipeline.Digest digest, String peerId) {
        return "{\"peer\":\"" + peerId + "\","
                + "\"hash\":\"" + digest.contentHash() + "\","
                + "\"shared\":" + digest.shared() + ","
                + "\"noisyCount\":" + digest.noisyCount() + "}";
    }

    public long digestsDispatched() { return digestsDispatched.get(); }
    public long digestsDropped() { return digestsDropped.get(); }
    public long sendErrors() { return sendErrors.get(); }
}