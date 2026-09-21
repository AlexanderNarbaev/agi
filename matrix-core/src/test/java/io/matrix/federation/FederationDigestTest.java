package io.matrix.federation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 165 — FederationDigest unit tests. */
class FederationDigestTest {

    @Test
    void emptyDigestHashDeterministic() {
        var d1 = FederationDigest.compute("node-1", 1000L, List.of());
        var d2 = FederationDigest.compute("node-1", 1000L, List.of());
        assertThat(d1.bodyHash()).isEqualTo(d2.bodyHash());
    }

    @Test
    void sameInputsSameHash() {
        var e1 = new FederationDigest.Entry("k1", "h1", "M2");
        var e2 = new FederationDigest.Entry("k2", "h2", "M1");
        var d1 = FederationDigest.compute("node-X", 5000L, List.of(e1, e2));
        var d2 = FederationDigest.compute("node-X", 5000L, List.of(e1, e2));
        assertThat(d1.bodyHash()).isEqualTo(d2.bodyHash());
    }

    @Test
    void differentNodesDifferentHashes() {
        var e = new FederationDigest.Entry("k", "h", "M2");
        var d1 = FederationDigest.compute("node-A", 1L, List.of(e));
        var d2 = FederationDigest.compute("node-B", 1L, List.of(e));
        assertThat(d1.bodyHash()).isNotEqualTo(d2.bodyHash());
    }

    @Test
    void verifyPassesForLegitDigest() {
        var d = FederationDigest.compute("node-1", 100L,
                List.of(new FederationDigest.Entry("k", "h", "M2")));
        assertThat(FederationDigest.verify(d)).isTrue();
    }

    @Test
    void verifyFailsForTamperedDigest() {
        var d = FederationDigest.compute("node-1", 100L,
                List.of(new FederationDigest.Entry("k", "h", "M2")));
        var tampered = new FederationDigest.Digest(d.nodeId(),
                d.timestampMillis() + 1,  // changed ts
                d.entries(),
                d.bodyHash());
        assertThat(FederationDigest.verify(tampered)).isFalse();
    }

    @Test
    void entriesForTierFiltersCorrectly() {
        var e1 = new FederationDigest.Entry("k1", "h", "M0");
        var e2 = new FederationDigest.Entry("k2", "h", "M1");
        var e3 = new FederationDigest.Entry("k3", "h", "M2");
        var d = FederationDigest.compute("node", 1L, List.of(e1, e2, e3));
        assertThat(FederationDigest.entriesForTier(d, "M0")).hasSize(1);
        assertThat(FederationDigest.entriesForTier(d, "M1")).hasSize(1);
        assertThat(FederationDigest.entriesForTier(d, "M2")).hasSize(1);
        assertThat(FederationDigest.entriesForTier(d, "M3")).isEmpty();
    }

    @Test
    void sha256HelperIsDeterministic() {
        String h1 = FederationDigest.sha256("hello");
        String h2 = FederationDigest.sha256("hello");
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64);
    }

    @Test
    void digestEntryRecord() {
        FederationDigest.Entry e = new FederationDigest.Entry("k", "h", "M0");
        assertThat(e.key()).isEqualTo("k");
        assertThat(e.hash()).isEqualTo("h");
        assertThat(e.tier()).isEqualTo("M0");
    }
}
