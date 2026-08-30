package io.matrix.federation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave F test: KnowledgeShare dispatches k-anonymous digests and
 * drops non-shared ones.
 */
class KnowledgeShareTest {

    @Test
    void digestBundleReturnsSharedAndDroppedDigests() {
        Anonymizer anon = new Anonymizer(2);  // k=2
        // contribute from 2 sources so the digest is k-anonymous
        anon.recordContribution("hash-1", "node-A");
        anon.recordContribution("hash-1", "node-B");
        anon.recordContribution("hash-2", "node-A");  // only 1 source — not shared

        KnowledgeShare share = new KnowledgeShare(anon, null);
        var digests = share.digestBundle();
        // hash-1 should be shared (k=2 reached), hash-2 should not
        boolean hasShared = digests.stream().anyMatch(d -> d.shared());
        assertThat(hasShared).isTrue();
    }

    @Test
    void dispatchToPeerRejectsNonSharedDigests() {
        Anonymizer anon = new Anonymizer(2);
        // contribute 1 source for hash-1 (NOT shared) and 2 for hash-2 (shared)
        anon.recordContribution("hash-lonely", "node-A");
        anon.recordContribution("hash-shared", "node-A");
        anon.recordContribution("hash-shared", "node-B");
        KnowledgeShare share = new KnowledgeShare(anon, null);
        long before = share.digestsDropped();
        var digests = share.digestBundle();
        for (var d : digests) {
            share.dispatchToPeer(d, "node-B");
        }
        // the non-shared digest should have been dropped
        assertThat(share.digestsDropped()).isGreaterThan(before);
    }
}