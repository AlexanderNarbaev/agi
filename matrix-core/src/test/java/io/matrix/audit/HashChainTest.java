package io.matrix.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashChainTest {

    @Test
    void emptyChainHasSizeZero() {
        HashChain chain = new HashChain();
        assertThat(chain.size()).isZero();
        assertThat(chain.latest()).isNull();
        assertThat(chain.verify()).isTrue();  // empty chain trivially verifies
    }

    @Test
    void firstLinkStartsAtSequenceZero() {
        HashChain chain = new HashChain();
        HashLink link = chain.append("genesis", "first");
        assertThat(link.sequence()).isZero();
        assertThat(link.previousHash()).isEqualTo(HashLink.genesisHash());
    }

    @Test
    void subsequentLinksChainBack() {
        HashChain chain = new HashChain();
        HashLink a = chain.append("a", "first");
        HashLink b = chain.append("b", "second");
        HashLink c = chain.append("c", "third");
        assertThat(b.previousHash()).isEqualTo(a.hash());
        assertThat(c.previousHash()).isEqualTo(b.hash());
        assertThat(chain.size()).isEqualTo(3);
    }

    @Test
    void everyLinkVerifies() {
        HashChain chain = new HashChain();
        for (int i = 0; i < 10; i++) chain.append("payload-" + i, "tag");
        assertThat(chain.verify()).isTrue();
        for (HashLink link : chain.snapshot()) {
            assertThat(link.verify()).isTrue();
        }
    }

    @Test
    void tamperedLinkFailsVerification() {
        HashChain chain = new HashChain();
        chain.append("a", "first");
        HashLink link = chain.append("b", "second");
        // Verify the original link is good, then forge a tampered version.
        assertThat(link.verify()).isTrue();
        HashLink tampered = HashLink.reconstruct(
                link.sequence(),
                link.previousHash(),
                link.payloadHash(),
                link.timestampMs(),
                link.extra(),
                "0".repeat(64));  // wrong hash
        // The tampered link must fail its own self-verification.
        assertThat(tampered.verify()).isFalse();
    }

    @Test
    void tamperedPayloadFailsVerification() {
        HashChain chain = new HashChain();
        chain.append("a", "first");
        HashLink link = chain.append("b", "second");
        // Create a link with a different payloadHash but original hash.
        // The hash itself was computed for the original payload, so verify() returns false.
        HashLink tampered = new HashLink(
                link.sequence(),
                link.previousHash(),
                "deadbeef".repeat(8),  // wrong payload hash
                link.timestampMs(),
                link.extra(),
                link.hash());
        assertThat(tampered.verify()).isFalse();
    }

    @Test
    void snapshotIsDefensiveCopy() {
        HashChain chain = new HashChain();
        chain.append("a", "first");
        var snap = chain.snapshot();
        chain.append("b", "second");
        // Original snapshot is unchanged.
        assertThat(snap).hasSize(1);
        assertThat(chain.size()).isEqualTo(2);
    }

    @Test
    void restoreReplacesChain() {
        HashChain chain1 = new HashChain();
        chain1.append("a", "first");
        chain1.append("b", "second");

        HashChain chain2 = new HashChain();
        chain2.restore(chain1.snapshot());
        assertThat(chain2.size()).isEqualTo(2);
        assertThat(chain2.latest().hash()).isEqualTo(chain1.latest().hash());
        assertThat(chain2.verify()).isTrue();
    }

    @Test
    void restoreRejectsBrokenChain() {
        HashChain chain1 = new HashChain();
        chain1.append("a", "first");
        HashLink a = chain1.get(0);
        HashLink broken = HashLink.reconstruct(
                a.sequence(), a.previousHash(), a.payloadHash(), a.timestampMs(), a.extra(),
                "0".repeat(64));  // hash doesn't match fields
        HashChain chain2 = new HashChain();
        assertThatThrownBy(() -> chain2.restore(java.util.List.of(broken)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashIsDeterministic() {
        // Same input → same hash (no clock dependency for HashLink.computeHash).
        long ts = 1_000_000L;
        String h1 = HashLink.computeHash(HashLink.genesisHash(), 0L,
                "00".repeat(32), ts, "test");
        String h2 = HashLink.computeHash(HashLink.genesisHash(), 0L,
                "00".repeat(32), ts, "test");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void hashChangesWithEachField() {
        long ts = 1_000_000L;
        String base = HashLink.computeHash(HashLink.genesisHash(), 0L,
                "00".repeat(32), ts, "test");
        assertThat(HashLink.computeHash("ff".repeat(32), 0L,
                "00".repeat(32), ts, "test")).isNotEqualTo(base);  // prev
        assertThat(HashLink.computeHash(HashLink.genesisHash(), 1L,
                "00".repeat(32), ts, "test")).isNotEqualTo(base);  // seq
        assertThat(HashLink.computeHash(HashLink.genesisHash(), 0L,
                "ff".repeat(32), ts, "test")).isNotEqualTo(base);  // payload
        assertThat(HashLink.computeHash(HashLink.genesisHash(), 0L,
                "00".repeat(32), ts + 1, "test")).isNotEqualTo(base);  // ts
        assertThat(HashLink.computeHash(HashLink.genesisHash(), 0L,
                "00".repeat(32), ts, "different")).isNotEqualTo(base);  // extra
    }

    @Test
    void summaryContainsSizeAndLatestHash() {
        HashChain chain = new HashChain();
        chain.append("a", "first");
        String summary = chain.summary();
        assertThat(summary).contains("size=1").contains("HashLink");
    }

    @Test
    void getReturnsLinkAtIndex() {
        HashChain chain = new HashChain();
        chain.append("a", "first");
        chain.append("b", "second");
        assertThat(chain.get(0).payloadHash()).isNotEqualTo(chain.get(1).payloadHash());
    }
}
