package io.matrix.federation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W47 — k counts DISTINCT NODES.
 *
 * <p>The class promises that a published digest is held by "at least k distinct
 * nodes". The counter used to count CONTRIBUTIONS, so one node could reach the
 * threshold on its own by recording the same content hash repeatedly, and a
 * digest attributable to exactly one node would be published as anonymous. The
 * tests below are written so that they FAIL against that implementation: the
 * same node recording the same hash many times must NOT move the level.
 */
class AnonymizerTest {

    @Test
    void twoDifferentNodesCountAsTwo() {
        Anonymizer anon = new Anonymizer(2);
        anon.recordContribution("hash", "node-A");
        anon.recordContribution("hash", "node-B");
        assertThat(anon.anonymityLevel("hash")).isEqualTo(2);
        assertThat(anon.isAnonymous("hash")).isTrue();
    }

    @Test
    void theSameNodeTwiceCountsAsOne() {
        // THE discriminating case: against the old per-contribution counter
        // this was 2, and a single node could therefore satisfy k=2 alone.
        Anonymizer anon = new Anonymizer(2);
        anon.recordContribution("hash", "node-A");
        anon.recordContribution("hash", "node-A");
        assertThat(anon.anonymityLevel("hash")).isEqualTo(1);
        assertThat(anon.isAnonymous("hash")).isFalse();
    }

    @Test
    void aSingleNodeCannotMakeADigestAnonymousByReplayingIt() {
        Anonymizer anon = new Anonymizer(3);
        for (int i = 0; i < 100; i++) {
            anon.recordContribution("hash", "node-A");
        }
        assertThat(anon.anonymityLevel("hash")).isEqualTo(1);
        assertThat(anon.isAnonymous("hash"))
                .as("a digest held by one node is not anonymous, however often it is replayed")
                .isFalse();
    }

    @Test
    void theSecondDistinctNodeCompletesTheGroup() {
        Anonymizer anon = new Anonymizer(3);
        anon.recordContribution("hash", "node-A");
        anon.recordContribution("hash", "node-A");
        anon.recordContribution("hash", "node-B");
        anon.recordContribution("hash", "node-C");
        assertThat(anon.anonymityLevel("hash")).isEqualTo(3);
        assertThat(anon.isAnonymous("hash")).isTrue();
    }

    @Test
    void differentHashesCountSeparately() {
        Anonymizer anon = new Anonymizer(2);
        anon.recordContribution("hash-a", "node-A");
        anon.recordContribution("hash-a", "node-B");
        anon.recordContribution("hash-b", "node-A");
        anon.recordContribution("hash-b", "node-A");

        assertThat(anon.anonymityLevel("hash-a")).isEqualTo(2);
        assertThat(anon.anonymityLevel("hash-b")).isEqualTo(1);
        assertThat(anon.isAnonymous("hash-a")).isTrue();
        assertThat(anon.isAnonymous("hash-b")).isFalse();
    }

    @Test
    void anUnknownHashHasNoContributors() {
        Anonymizer anon = new Anonymizer(2);
        assertThat(anon.anonymityLevel("never-seen")).isZero();
        assertThat(anon.isAnonymous("never-seen")).isFalse();
    }

    @Test
    void theSnapshotCountsDistinctNodes() {
        Anonymizer anon = new Anonymizer(2);
        anon.recordContribution("hash-a", "node-A");
        anon.recordContribution("hash-a", "node-A");
        anon.recordContribution("hash-a", "node-B");
        anon.recordContribution("hash-b", "node-A");

        assertThat(anon.snapshotEntries())
                .containsEntry("hash-a", 2)
                .containsEntry("hash-b", 1);
    }

    @Test
    void clearingRemovesEveryContribution() {
        Anonymizer anon = new Anonymizer(2);
        anon.recordContribution("hash", "node-A");
        anon.recordContribution("hash", "node-B");
        anon.clear();
        assertThat(anon.anonymityLevel("hash")).isZero();
        assertThat(anon.snapshotEntries()).isEmpty();
    }

    @Test
    void aContributionMustComeFromANode() {
        Anonymizer anon = new Anonymizer(2);
        assertThatThrownBy(() -> anon.recordContribution("hash", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("nodeId");
    }

    @Test
    void theThresholdMustBeAtLeastTwo() {
        assertThatThrownBy(() -> new Anonymizer(1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
