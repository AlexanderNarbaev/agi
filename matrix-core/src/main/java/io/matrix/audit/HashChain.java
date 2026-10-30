package io.matrix.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Append-only hash-chain manager.
 *
 * <p>Wraps {@link HashLink} into a navigable, verifiable, monotonically-
 * increasing sequence. Each appended link references the previous link's
 * hash, so any retroactive mutation invalidates every subsequent
 * {@link #verify()} check.
 *
 * <p>Thread-safe: all public methods are protected by an internal lock,
 * so concurrent appenders serialise their writes. Reads are lock-free
 * (volatile snapshot of the latest link).
 *
 * <p>Persistence: {@link #snapshot()} returns the full ordered list of
 * links for storage in any append-only medium (Postgres, Kafka, S3 — see
 * {@code io.matrix.privacy.storage.TombstoneStorage} for the analogous
 * pattern). Reconstruction via {@link #restore(List)} is straightforward.
 *
 * <p>Ref: L7 §5 (FROZEN audit), L12 §4 (legal framework).
 */
public final class HashChain {

    /** Link source — defaults to {@code System.currentTimeMillis()}. */
    private final Supplier<Long> clock;

    /** All links appended, in order. */
    private final List<HashLink> links = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    /** Latest link — {@code null} only when the chain is empty. */
    private volatile HashLink latest;

    public HashChain() {
        this(System::currentTimeMillis);
    }

    public HashChain(Supplier<Long> clock) {
        this.clock = clock;
    }

    /** Append a new link. Returns the new link with hash. */
    public HashLink append(String payload, String extra) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(extra, "extra");
        lock.lock();
        try {
            // RECON-W39: the timestamp now comes from the CONFIGURED clock.
            // It used to be taken inside HashLink.extend() from
            // System.currentTimeMillis(), which made the injected clock
            // supplier dead code: a test could not reproduce a chain, and a
            // wall-clock change silently altered the hashed content.
            HashLink link = HashLink.extend(latest, payload, extra, clock.get());
            links.add(link);
            latest = link;
            return link;
        } finally {
            lock.unlock();
        }
    }

    /** Returns the latest link or {@code null} if the chain is empty. */
    public HashLink latest() {
        return latest;
    }

    /** Returns the link at the given index (0-based). */
    public HashLink get(int index) {
        lock.lock();
        try {
            return links.get(index);
        } finally {
            lock.unlock();
        }
    }

    /** Number of links in the chain. */
    public int size() {
        lock.lock();
        try {
            return links.size();
        } finally {
            lock.unlock();
        }
    }

    /** Returns a defensive copy of every link in order. */
    public List<HashLink> snapshot() {
        lock.lock();
        try {
            return new ArrayList<>(links);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Verify the entire chain:
     * <ol>
     *   <li>Every link's hash matches its fields (no in-place mutation).</li>
     *   <li>Each link's {@code previousHash} equals the previous link's
     *       {@code hash} (no gap, no fork).</li>
     *   <li>The first link's {@code previousHash} equals the genesis hash.</li>
     *   <li>The sequence numbers are contiguous from zero: the link at index
     *       {@code i} has {@code sequence == i}.</li>
     * </ol>
     *
     * <p>RECON-W39: the sequence rule was neither checked nor documented.
     * {@link HashLink#extend} produces contiguous sequences, but a chain
     * assembled by {@link #restore} from storage could carry gaps, duplicates
     * or a reordered sequence and still verify, because the previous-hash
     * linkage alone does not constrain the sequence numbers. The contract is
     * now stated and enforced in both {@code verify()} and {@code restore()}.
     *
     * <p><b>Scope of this check.</b> The chain is tamper-EVIDENT against
     * accidental corruption and against partial historical mutation. It is
     * <b>not</b> protection against an attacker who can rewrite every stored
     * record and replace the trusted chain head: such an attacker recomputes
     * a consistent chain. See the threat model in
     * {@code docs-v2/architecture/ADR-2026-10-30-003-hashchain-trust-model.md}.
     *
     * @return {@code true} when the chain is intact
     */
    public boolean verify() {
        lock.lock();
        try {
            String expectedPrev = HashLink.genesisHash();
            for (int i = 0; i < links.size(); i++) {
                HashLink link = links.get(i);
                if (!link.previousHash().equals(expectedPrev)) return false;
                if (link.sequence() != i) return false;
                if (!link.verify()) return false;
                expectedPrev = link.hash();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Replace the chain with a list of pre-existing links. The supplied list
     * must be a valid chain (verified) and is appended verbatim.
     *
     * <p>The list is rejected unless it is a well-formed chain: the
     * predecessor hashes must link up from the genesis hash, every link's own
     * hash must match its fields, and the sequence numbers must be
     * contiguous from zero (RECON-W39 — the sequence rule was previously
     * unenforced, so a chain read back from storage could carry gaps or
     * duplicates and still be accepted).
     *
     * @throws IllegalArgumentException when the supplied list is not a valid chain
     */
    public void restore(List<HashLink> restored) {
        Objects.requireNonNull(restored, "restored");
        lock.lock();
        try {
            // Validate the chain we are about to install.
            String expectedPrev = HashLink.genesisHash();
            for (int i = 0; i < restored.size(); i++) {
                HashLink link = restored.get(i);
                if (!link.previousHash().equals(expectedPrev)) {
                    throw new IllegalArgumentException(
                            "Broken chain at seq=" + link.sequence() + ": bad previousHash");
                }
                if (link.sequence() != i) {
                    throw new IllegalArgumentException(
                            "Broken chain at index " + i + ": expected sequence " + i
                                    + " but found " + link.sequence());
                }
                if (!link.verify()) {
                    throw new IllegalArgumentException(
                            "Hash mismatch at seq=" + link.sequence());
                }
                expectedPrev = link.hash();
            }
            links.clear();
            links.addAll(restored);
            latest = restored.isEmpty() ? null : restored.get(restored.size() - 1);
        } finally {
            lock.unlock();
        }
    }

    /** Compact summary suitable for an audit log. */
    public String summary() {
        return "HashChain[size=" + size() + " latest=" + (latest == null ? "none"
                : latest.toDisplayString()) + "]";
    }
}
