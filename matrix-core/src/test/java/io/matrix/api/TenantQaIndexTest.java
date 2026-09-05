package io.matrix.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TenantQaIndex} — RUN 26 multi-tenant data isolation.
 *
 * <p>Uses a hand-rolled stub for QaCorpusIndex (Mockito is not on the
 * classpath). The stub records calls and returns canned responses.
 */
class TenantQaIndexTest {

    /** Minimal QaCorpusIndex stub: records last call, returns canned response. */
    static class StubQaCorpusIndex extends QaCorpusIndex {
        List<QaCorpusIndex.Entry> response;
        String lastQuery;
        int lastTopK;

        StubQaCorpusIndex(List<QaCorpusIndex.Entry> response) {
            this.response = response;
        }

        @Override
        public List<QaCorpusIndex.Entry> search(String query, int topK) {
            this.lastQuery = query;
            this.lastTopK = topK;
            return response;
        }
    }

    private StubQaCorpusIndex base;
    private TenantQaIndex tenants;

    @BeforeEach
    void setUp() {
        // Default: empty base; each test overrides if needed.
        base = new StubQaCorpusIndex(new ArrayList<>());
        tenants = new TenantQaIndex(base);
    }

    private QaCorpusIndex.Entry entry(int id) {
        return new QaCorpusIndex.Entry(id, "Q" + id, "A" + id, "cat", "src");
    }

    @Test
    void addAndSizeForTenant() {
        tenants.add("alice", entry(1));
        tenants.add("alice", entry(2));
        tenants.add("bob", entry(3));
        assertThat(tenants.sizeForTenant("alice")).isEqualTo(2);
        assertThat(tenants.sizeForTenant("bob")).isEqualTo(1);
        assertThat(tenants.sizeForTenant("eve")).isZero();
        assertThat(tenants.totalEntries()).isEqualTo(3);
        assertThat(tenants.tenantCount()).isEqualTo(2);
    }

    @Test
    void addRejectsInvalidArgs() {
        assertThat(tenants.add(null, entry(1))).isFalse();
        assertThat(tenants.add("", entry(1))).isFalse();
        assertThat(tenants.add("  ", entry(1))).isFalse();
        assertThat(tenants.add("alice", null)).isFalse();
        assertThat(tenants.totalEntries()).isZero();
    }

    @Test
    void filterByTenantReturnsOnlyTenantEntries() {
        tenants.add("alice", entry(1));
        tenants.add("alice", entry(2));
        tenants.add("bob", entry(3));

        // Mixed candidate list
        var candidates = List.of(entry(1), entry(3), entry(2), entry(4));
        var alice = tenants.filterByTenant("alice", candidates);
        var bob = tenants.filterByTenant("bob", candidates);
        var eve = tenants.filterByTenant("eve", candidates);

        assertThat(alice).extracting(QaCorpusIndex.Entry::id).containsExactly(1, 2);
        assertThat(bob).extracting(QaCorpusIndex.Entry::id).containsExactly(3);
        assertThat(eve).isEmpty();
    }

    @Test
    void filterByTenantRejectsNullOrBlankTenant() {
        tenants.add("alice", entry(1));
        var candidates = List.of(entry(1));
        assertThat(tenants.filterByTenant(null, candidates)).isEmpty();
        assertThat(tenants.filterByTenant("", candidates)).isEmpty();
        assertThat(tenants.filterByTenant("  ", candidates)).isEmpty();
    }

    /**
     * RUN 26: cross-tenant leakage is the security property we're
     * testing. Add entries to alice and bob, then verify that
     * searching as alice NEVER returns bob's entries, even when
     * the base index search returned both.
     */
    @Test
    void searchForTenantDoesNotLeak() {
        tenants.add("alice", entry(1));
        tenants.add("alice", entry(2));
        tenants.add("bob", entry(3));
        tenants.add("bob", entry(4));

        // Base index returns ALL 4 entries (no tenant awareness).
        base.response = new ArrayList<>(List.of(entry(1), entry(3), entry(2), entry(4)));

        // alice's search must return ONLY alice's entries.
        var aliceResults = tenants.searchForTenant("alice", "anything", 10);
        assertThat(aliceResults).extracting(QaCorpusIndex.Entry::id)
                .containsExactlyInAnyOrder(1, 2);

        // bob's search must return ONLY bob's entries.
        var bobResults = tenants.searchForTenant("bob", "anything", 10);
        assertThat(bobResults).extracting(QaCorpusIndex.Entry::id)
                .containsExactlyInAnyOrder(3, 4);

        // eve (no entries) gets nothing.
        var eveResults = tenants.searchForTenant("eve", "anything", 10);
        assertThat(eveResults).isEmpty();
    }

    @Test
    void searchForTenantRespectsTopK() {
        tenants.add("alice", entry(1));
        tenants.add("alice", entry(2));
        tenants.add("alice", entry(3));
        tenants.add("alice", entry(4));
        tenants.add("alice", entry(5));

        base.response = new ArrayList<>(List.of(entry(1), entry(2), entry(3), entry(4), entry(5)));

        var top2 = tenants.searchForTenant("alice", "x", 2);
        assertThat(top2).hasSize(2);
    }

    @Test
    void tenantSizesReportsAllTenants() {
        tenants.add("alice", entry(1));
        tenants.add("alice", entry(2));
        tenants.add("bob", entry(3));
        var sizes = tenants.tenantSizes();
        assertThat(sizes).containsEntry("alice", 2).containsEntry("bob", 1);
        assertThat(sizes.size()).isEqualTo(2);
    }

    @Test
    void tenantsAreCaseSensitive() {
        tenants.add("Alice", entry(1));
        tenants.add("alice", entry(2));
        // alice and Alice are different tenants.
        assertThat(tenants.sizeForTenant("Alice")).isEqualTo(1);
        assertThat(tenants.sizeForTenant("alice")).isEqualTo(1);
        assertThat(tenants.tenantCount()).isEqualTo(2);
    }

    @Test
    void tenantsSetIsIndependentCopy() {
        tenants.add("alice", entry(1));
        var ts = tenants.tenants();
        ts.add("evil");  // mutate the returned set
        // tenants() returned a new HashSet copy; the internal map is unaffected.
        assertThat(tenants.tenantCount()).isEqualTo(1);
    }
}
