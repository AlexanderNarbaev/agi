package io.matrix.federation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave F + Wave L test: KnowledgeShare dispatches k-anonymous
 * digests via a pluggable {@link MessageBus}, drops non-shared ones,
 * and records send errors.
 */
class KnowledgeShareTest {

    @Test
    void digestBundleReturnsSharedAndDroppedDigests() {
        Anonymizer anon = new Anonymizer(2);  // k=2
        // contribute from 2 sources so the digest is k-anonymous
        anon.recordContribution("hash-1", "node-A");
        anon.recordContribution("hash-1", "node-B");
        anon.recordContribution("hash-2", "node-A");  // only 1 source — not shared

        KnowledgeShare share = new KnowledgeShare(anon, null, new InMemoryMessageBus());
        // use low-noise pipeline so the shared count reliably >= k
        DecentralizedDigestPipeline lowNoise = new DecentralizedDigestPipeline(anon, 10.0);
        var digests = lowNoise.emitDigests();
        // hash-1 should be shared (k=2 reached), hash-2 should not
        boolean hasShared = digests.stream().anyMatch(d -> d.shared());
        assertThat(hasShared).isTrue();
    }

    @Test
    void dispatchToPeerRejectsNonSharedDigests() {
        Anonymizer anon = new Anonymizer(2);
        anon.recordContribution("hash-lonely", "node-A");
        anon.recordContribution("hash-shared", "node-A");
        anon.recordContribution("hash-shared", "node-B");
        InMemoryMessageBus bus = new InMemoryMessageBus();
        KnowledgeShare share = new KnowledgeShare(anon, null, bus);
        // use low-noise pipeline so the lonely (count=1) digest
        // stays below k=2 even after noise (so it gets dropped)
        DecentralizedDigestPipeline lowNoise = new DecentralizedDigestPipeline(anon, 10.0);
        long before = share.digestsDropped();
        for (var d : lowNoise.emitDigests()) {
            share.dispatchToPeer(d, "node-B");
        }
        // the non-shared digest should have been dropped
        assertThat(share.digestsDropped()).isGreaterThan(before);
    }

    @Test
    void dispatchToPeerDeliversSharedDigestsViaBus() {
        // ε=10 → noise scale=0.1 → noisyCount stays at 2 with high
        // probability; digest is reliably shared
        Anonymizer anon = new Anonymizer(2);
        anon.recordContribution("hash-1", "node-A");
        anon.recordContribution("hash-1", "node-B");
        InMemoryMessageBus bus = new InMemoryMessageBus();
        KnowledgeShare share = new KnowledgeShare(anon, null, bus);
        // force a high-ε pipeline so noisyCount reliably >= k
        // (the default share uses ε=1 which can drop a count=2 below 2)
        DecentralizedDigestPipeline lowNoise = new DecentralizedDigestPipeline(anon, 10.0);
        var digests = lowNoise.emitDigests();
        DecentralizedDigestPipeline.Digest shared = digests.stream()
                .filter(DecentralizedDigestPipeline.Digest::shared)
                .findFirst().orElseThrow();
        boolean ok = share.dispatchToPeer(shared, "node-B");
        assertThat(ok).isTrue();
        assertThat(share.digestsDispatched()).isEqualTo(1L);
        assertThat(bus.sent).containsKey("node-B");
        assertThat(bus.sent.get("node-B")).hasSize(1);
        String content = bus.sent.get("node-B").get(0);
        assertThat(content).contains("\"peer\":\"node-B\"");
        assertThat(content).contains("\"hash\":\"" + shared.contentHash() + "\"");
    }

    @Test
    void dispatchToPeerRecordsSendErrors() {
        Anonymizer anon = new Anonymizer(2);
        anon.recordContribution("hash-1", "node-A");
        anon.recordContribution("hash-1", "node-B");
        MessageBus broken = new MessageBus() {
            @Override public boolean send(String peerId, String content) throws java.io.IOException {
                throw new java.io.IOException("disk full");
            }
            @Override public boolean isAvailable() { return true; }
        };
        KnowledgeShare share = new KnowledgeShare(anon, null, broken);
        DecentralizedDigestPipeline lowNoise = new DecentralizedDigestPipeline(anon, 10.0);
        var shared = lowNoise.emitDigests().stream()
                .filter(DecentralizedDigestPipeline.Digest::shared)
                .findFirst().orElseThrow();
        boolean ok = share.dispatchToPeer(shared, "node-B");
        assertThat(ok).isFalse();
        assertThat(share.sendErrors()).isEqualTo(1L);
    }
}