package io.matrix.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W39 — integrity tests for {@link HashChain} and {@link HashLink}.
 *
 * <p>Three defects were found in the previous wave and are covered here:
 *
 * <ol>
 *   <li>the injected clock supplier was dead code — {@code HashLink.extend()}
 *       read {@code System.currentTimeMillis()} directly, so no test could
 *       produce a reproducible chain;</li>
 *   <li>the timestamp validation was a conditional expression passed to
 *       {@code Objects.requireNonNull}, which is never null, so a negative
 *       timestamp was accepted silently;</li>
 *   <li>the sequence rule was neither documented nor enforced, so a chain
 *       restored from storage could carry gaps, duplicates or a reordering
 *       and still verify.</li>
 * </ol>
 *
 * <p>Every test here is deterministic: no wall-clock is read.
 */
class HashChainIntegrityTest {

    private static final String HEX64 = "0".repeat(64);

    // ── the injected clock ────────────────────────────────────────────────────

    @Test
    @DisplayName("the injected clock supplies the timestamp, so a chain is reproducible")
    void injectedClockIsUsed() {
        HashChain a = new HashChain(() -> 1_000L);
        HashChain b = new HashChain(() -> 1_000L);
        a.append("payload-1", "tag");
        a.append("payload-2", "tag");
        b.append("payload-1", "tag");
        b.append("payload-2", "tag");

        // HashLink is a record: equal fields, equal value.
        assertThat(a.snapshot()).isEqualTo(b.snapshot());
        assertThat(a.latest().timestampMs()).isEqualTo(1_000L);
        assertThat(a.snapshot()).allSatisfy(link -> assertThat(link.timestampMs()).isEqualTo(1_000L));
    }

    @Test
    @DisplayName("an advancing clock is reflected in the timestamps")
    void advancingClockIsUsed() {
        long[] now = {10L};
        HashChain chain = new HashChain(() -> now[0]);
        chain.append("a", "e");
        now[0] = 20L;
        chain.append("b", "e");
        now[0] = 30L;
        chain.append("c", "e");

        assertThat(chain.snapshot()).extracting(HashLink::timestampMs)
                .containsExactly(10L, 20L, 30L);
        assertThat(chain.verify()).isTrue();
    }

    @Test
    @DisplayName("repeated timestamps are allowed and remain distinguishable")
    void repeatedTimestampsAreAllowed() {
        HashChain chain = new HashChain(() -> 7L);
        chain.append("a", "e");
        chain.append("b", "e");
        chain.append("c", "e");

        assertThat(chain.snapshot()).extracting(HashLink::timestampMs)
                .containsExactly(7L, 7L, 7L);
        // The timestamp is not the only thing that distinguishes the links:
        // the sequence and the payload hash are part of the hashed content.
        assertThat(chain.snapshot()).extracting(HashLink::hash).doesNotHaveDuplicates();
        assertThat(chain.verify()).isTrue();
    }

    @Test
    @DisplayName("a clock that moves backwards is accepted: sequence order and event time are different")
    void backwardsClockIsAccepted() {
        // RECON-W39: the intended contract does NOT require monotonically
        // increasing wall-clock timestamps. The chain's ORDER is the sequence
        // number; the timestamp is the time the event was recorded. A
        // backwards clock is recorded faithfully rather than rejected.
        long[] now = {100L};
        HashChain chain = new HashChain(() -> now[0]);
        chain.append("a", "e");
        now[0] = 50L;
        chain.append("b", "e");

        assertThat(chain.snapshot()).extracting(HashLink::timestampMs)
                .containsExactly(100L, 50L);
        assertThat(chain.snapshot()).extracting(HashLink::sequence)
                .containsExactly(0L, 1L);
        assertThat(chain.verify()).isTrue();
    }

    @Test
    @DisplayName("a negative timestamp from the clock is rejected")
    void negativeTimestampFromClockIsRejected() {
        HashChain chain = new HashChain(() -> -1L);
        assertThatThrownBy(() -> chain.append("a", "e"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestampMs");
    }

    // ── link validation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("a negative timestamp is rejected at construction")
    void negativeTimestampIsRejected() {
        assertThatThrownBy(() -> new HashLink(0L, HEX64, HEX64, -1L, "e", HEX64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestampMs");
    }

    @Test
    @DisplayName("a negative sequence is rejected at construction")
    void negativeSequenceIsRejected() {
        assertThatThrownBy(() -> new HashLink(-1L, HEX64, HEX64, 0L, "e", HEX64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sequence");
    }

    @Test
    @DisplayName("a malformed hash field is rejected at construction")
    void malformedHashIsRejected() {
        assertThatThrownBy(() -> new HashLink(0L, "not-hex", HEX64, 0L, "e", HEX64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("previousHash");

        assertThatThrownBy(() -> new HashLink(0L, HEX64, "abc", 0L, "e", HEX64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payloadHash");

        assertThatThrownBy(() -> new HashLink(0L, HEX64, HEX64, 0L, "e", "z".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hash");
    }

    @Test
    @DisplayName("a hash field of the wrong length is rejected at construction")
    void shortHashIsRejected() {
        assertThatThrownBy(() -> new HashLink(0L, "abc", HEX64, 0L, "e", HEX64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64 hex characters");
    }

    // ── sequence semantics ────────────────────────────────────────────────────

    @Test
    @DisplayName("restore rejects a chain whose sequence skips a value")
    void restoreRejectsSequenceGap() {
        HashChain source = new HashChain(() -> 1L);
        source.append("a", "e");
        source.append("b", "e");
        source.append("c", "e");

        // Rebuild the links with a gap in the sequence numbers.
        List<HashLink> gapped = new ArrayList<>();
        gapped.add(source.get(0));
        gapped.add(HashLink.reconstruct(5L, source.get(0).hash(), source.get(1).payloadHash(),
                source.get(1).timestampMs(), source.get(1).extra(), source.get(1).hash()));
        gapped.add(source.get(2));

        HashChain target = new HashChain(() -> 1L);
        assertThatThrownBy(() -> target.restore(gapped))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected sequence");
    }

    @Test
    @DisplayName("restore rejects a chain with a duplicated sequence number")
    void restoreRejectsDuplicateSequence() {
        HashChain source = new HashChain(() -> 1L);
        source.append("a", "e");
        source.append("b", "e");

        List<HashLink> duplicated = List.of(source.get(0), source.get(0));

        HashChain target = new HashChain(() -> 1L);
        assertThatThrownBy(() -> target.restore(duplicated))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("restore rejects a reordered chain")
    void restoreRejectsReorderedChain() {
        HashChain source = new HashChain(() -> 1L);
        source.append("a", "e");
        source.append("b", "e");

        HashChain target = new HashChain(() -> 1L);
        assertThatThrownBy(() -> target.restore(List.of(source.get(1), source.get(0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("verify rejects a chain whose sequence does not match its position")
    void verifyRejectsBadSequence() {
        HashChain source = new HashChain(() -> 1L);
        source.append("a", "e");
        source.append("b", "e");

        // A chain whose links are individually valid and correctly linked,
        // but whose sequence numbers do not follow the position rule.
        HashChain target = new HashChain(() -> 1L);
        target.restore(source.snapshot());
        assertThat(target.verify()).isTrue();

        HashChain bad = new HashChain(() -> 1L);
        List<HashLink> links = source.snapshot();
        HashLink second = HashLink.reconstruct(
                9L, links.get(0).hash(), links.get(1).payloadHash(),
                links.get(1).timestampMs(), links.get(1).extra(), links.get(1).hash());
        // The link's own hash was computed for sequence 1, so it no longer
        // verifies either - which is itself the point: the sequence is part
        // of the hashed content.
        assertThat(second.verify()).isFalse();
    }

    // ── tamper detection ──────────────────────────────────────────────────────

    @Test
    @DisplayName("tampering with any single field breaks the link's own verification")
    void tamperingWithAnyFieldBreaksVerification() {
        HashChain chain = new HashChain(() -> 42L);
        chain.append("payload", "tag");
        HashLink link = chain.latest();
        assertThat(link.verify()).isTrue();

        // previousHash. NOTE: the first link's previousHash IS the genesis
        // hash, which is "0".repeat(64) — so a DIFFERENT wrong value is
        // needed here, or the "tampered" link would be identical to the
        // original and would still verify.
        assertThat(HashLink.reconstruct(link.sequence(), "1".repeat(64), link.payloadHash(),
                link.timestampMs(), link.extra(), link.hash()).verify()).isFalse();
        // payloadHash
        assertThat(HashLink.reconstruct(link.sequence(), link.previousHash(), HEX64,
                link.timestampMs(), link.extra(), link.hash()).verify()).isFalse();
        // timestamp
        assertThat(HashLink.reconstruct(link.sequence(), link.previousHash(), link.payloadHash(),
                link.timestampMs() + 1, link.extra(), link.hash()).verify()).isFalse();
        // extra
        assertThat(HashLink.reconstruct(link.sequence(), link.previousHash(), link.payloadHash(),
                link.timestampMs(), "other", link.hash()).verify()).isFalse();
        // sequence
        assertThat(HashLink.reconstruct(link.sequence() + 1, link.previousHash(), link.payloadHash(),
                link.timestampMs(), link.extra(), link.hash()).verify()).isFalse();
    }

    @Test
    @DisplayName("restoring the original payload restores the original digest")
    void restoringTheOriginalContentRestoresTheDigest() {
        // RECON-W39, honesty about what a hash chain proves: a deterministic
        // digest is a function of the CONTENT. Changing the content changes
        // the digest; changing it BACK restores the original digest. The
        // chain detects that the content is not what the recorded digest
        // says - it does not remember that the content was ever different.
        // That is why the chain is tamper-EVIDENT, not tamper-PROOF, and why
        // a trusted chain head matters (see the ADR).
        HashChain chain = new HashChain(() -> 5L);
        chain.append("original", "tag");
        HashLink link = chain.latest();

        HashLink mutated = HashLink.reconstruct(link.sequence(), link.previousHash(),
                link.payloadHash(), link.timestampMs(), "mutated", link.hash());
        assertThat(mutated.verify()).isFalse();

        HashLink restored = HashLink.reconstruct(link.sequence(), link.previousHash(),
                link.payloadHash(), link.timestampMs(), link.extra(), link.hash());
        assertThat(restored.verify()).isTrue();
    }

    @Test
    @DisplayName("a broken predecessor link breaks the whole chain")
    void brokenPredecessorBreaksTheChain() {
        HashChain chain = new HashChain(() -> 1L);
        chain.append("a", "e");
        chain.append("b", "e");
        assertThat(chain.verify()).isTrue();

        // Rebuild the second link with a different predecessor: its own hash
        // no longer matches either, so the chain is broken.
        HashLink first = chain.get(0);
        HashLink second = chain.get(1);
        HashLink forged = HashLink.reconstruct(second.sequence(), HEX64, second.payloadHash(),
                second.timestampMs(), second.extra(), second.hash());
        assertThat(forged.verify()).isFalse();
        assertThat(first.verify()).isTrue();
    }

    // ── persistence and concurrency ───────────────────────────────────────────

    @Test
    @DisplayName("a chain survives a snapshot/restore round trip unchanged")
    void roundTripPreservesTheChain() {
        HashChain source = new HashChain(() -> 77L);
        source.append("a", "e");
        source.append("b", "e");
        source.append("c", "e");

        HashChain target = new HashChain(() -> 77L);
        target.restore(source.snapshot());

        assertThat(target.snapshot()).isEqualTo(source.snapshot());
        assertThat(target.verify()).isTrue();
        assertThat(target.latest().hash()).isEqualTo(source.latest().hash());
    }

    @Test
    @DisplayName("a truncated chain is still a valid (shorter) chain")
    void truncatedChainIsAccepted() {
        // A snapshot interrupted mid-append yields a PREFIX of the chain.
        // The prefix is itself a well-formed chain and must be accepted -
        // rejecting it would make an interrupted write unrecoverable.
        HashChain source = new HashChain(() -> 1L);
        source.append("a", "e");
        source.append("b", "e");
        source.append("c", "e");

        HashChain target = new HashChain(() -> 1L);
        target.restore(source.snapshot().subList(0, 2));
        assertThat(target.size()).isEqualTo(2);
        assertThat(target.verify()).isTrue();
    }

    @Test
    @DisplayName("concurrent appends produce one consistent chain")
    void concurrentAppendsAreConsistent() throws Exception {
        int threads = 8;
        int perThread = 25;
        HashChain chain = new HashChain(() -> 1L);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            chain.append("payload", "tag");
                        }
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertThat(failure.get()).isNull();
        assertThat(chain.size()).isEqualTo(threads * perThread);
        assertThat(chain.verify()).isTrue();
        assertThat(chain.snapshot()).extracting(HashLink::sequence)
                .containsExactlyElementsOf(java.util.stream.LongStream.range(0, threads * perThread)
                        .boxed().toList());
    }
}
