package io.matrix.noosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * RECON-W39 — the epoch-aware causal CRDT that corresponds to
 * {@code formal/MemoryM4Causal.tla}.
 *
 * <p>The class itself was added because the Java the specification originally
 * named — {@link GrowOnlySet} — cannot express the model's semantics: it
 * stores tombstone keys with no epoch and filters them unconditionally.
 * See {@code docs-v2/architecture/ADR-2026-10-30-004-noosphere-crdt-semantics.md}.
 *
 * <p>The semantics under test:
 *
 * <ul>
 *   <li>each key carries an <b>insert epoch</b> — the logical clock of its
 *       last insert;</li>
 *   <li>each tombstoned key carries a <b>tombstone epoch</b>;</li>
 *   <li>a key is <b>live</b> iff its insert epoch is strictly greater than
 *       its tombstone epoch;</li>
 *   <li>a tombstone therefore blocks every insert at or below its epoch, and
 *       a <i>strictly newer</i> insert resurrects the key;</li>
 *   <li><b>frozen keys</b> may never be inserted at all.</li>
 * </ul>
 *
 * <p>These tests are property-based: the CRDT algebra is checked over
 * generated replica states and message orders, not over a handful of
 * hand-written examples.
 */
class EpochAwareCausalSetTest {

    private static final String K1 = "k1";
    private static final String K2 = "k2";
    private static final String FROZEN = "kf";

    // ── the CRDT algebra ───────────────────────────────────────────────────────

    @Property
    void mergeIsCommutative(@ForAll("replicas") EpochAwareCausalSet a,
                            @ForAll("replicas") EpochAwareCausalSet b) {
        assertThat(a.merge(b)).isEqualTo(b.merge(a));
    }

    @Property
    void mergeIsAssociative(@ForAll("replicas") EpochAwareCausalSet a,
                            @ForAll("replicas") EpochAwareCausalSet b,
                            @ForAll("replicas") EpochAwareCausalSet c) {
        assertThat(a.merge(b).merge(c)).isEqualTo(a.merge(b.merge(c)));
    }

    @Property
    void mergeIsIdempotent(@ForAll("replicas") EpochAwareCausalSet a) {
        assertThat(a.merge(a)).isEqualTo(a);
    }

    @Property
    void everyReplicaConvergesToTheSameState(@ForAll("replicas") EpochAwareCausalSet a,
                                             @ForAll("replicas") EpochAwareCausalSet b) {
        // The classic CRDT property: the order of delivery does not matter.
        assertThat(a.merge(b).merge(a)).isEqualTo(a.merge(b));
    }

    // ── tombstone semantics ────────────────────────────────────────────────────

    @Property
    void aTombstoneBlocksEveryInsertAtOrBelowItsEpoch(
            @ForAll("freshReplicas") EpochAwareCausalSet a,
            @ForAll @IntRange(min = 0, max = 5) int epoch) {
        EpochAwareCausalSet tombstoned = a.tombstoneAt(K1, epoch);
        for (long candidate = 0; candidate <= epoch; candidate++) {
            assertThat(tombstoned.canInsert(K1, candidate))
                    .as("insert at epoch %d must be blocked by a tombstone at epoch %d",
                            candidate, epoch)
                    .isFalse();
        }
    }

    @Property
    void anInsertNewerThanTheTombstoneIsAccepted(
            @ForAll("freshReplicas") EpochAwareCausalSet a,
            @ForAll @IntRange(min = 0, max = 5) int epoch) {
        EpochAwareCausalSet tombstoned = a.tombstoneAt(K1, epoch);
        long current = tombstoned.insertEpoch(K1);
        boolean accepted = tombstoned.canInsert(K1, epoch + 1L);
        // A strictly newer epoch is accepted, unless it is stale relative to
        // an insert epoch the replica already holds.
        assertThat(accepted).isEqualTo(epoch + 1L >= current);
        if (accepted) {
            assertThat(tombstoned.insert(K1, epoch + 1L).keys()).contains(K1);
        }
    }

    @Property
    void aTombstonedKeyComesBackOnlyThroughAStrictlyNewerInsert(
            @ForAll("freshReplicas") EpochAwareCausalSet a,
            @ForAll("replicas") EpochAwareCausalSet b,
            @ForAll @IntRange(min = 0, max = 5) int epoch) {
        EpochAwareCausalSet tombstoned = a.tombstoneAt(K1, epoch);
        EpochAwareCausalSet merged = tombstoned.merge(b);
        // The tombstone never weakens through a merge.
        assertThat(merged.tombstoneEpoch(K1)).isGreaterThanOrEqualTo(epoch);
        // The merge can only resurrect the key when the combined insert epoch
        // is strictly greater than the combined tombstone epoch.
        assertThat(merged.keys().contains(K1))
                .isEqualTo(merged.insertEpoch(K1) > merged.tombstoneEpoch(K1));
    }

    @Property
    void aStaleInsertIsIgnored(@ForAll("freshReplicas") EpochAwareCausalSet a,
                                @ForAll @IntRange(min = 1, max = 5) int epoch) {
        EpochAwareCausalSet inserted = a.insert(K1, epoch);
        assertThat(inserted.insert(K1, epoch - 1)).isEqualTo(inserted);
    }

    @Property
    void anOlderTombstoneDoesNotWeakenANewerOne(
            @ForAll("freshReplicas") EpochAwareCausalSet a,
            @ForAll @IntRange(min = 1, max = 5) int epoch) {
        EpochAwareCausalSet newer = a.tombstoneAt(K1, epoch);
        EpochAwareCausalSet older = newer.tombstoneAt(K1, epoch - 1);
        assertThat(older.tombstoneEpoch(K1)).isEqualTo(epoch);
        assertThat(older).isEqualTo(newer);
    }

    // ── frozen keys ────────────────────────────────────────────────────────────

    @Property
    void aFrozenKeyIsNeverInserted(@ForAll("frozenReplicas") EpochAwareCausalSet a,
                                   @ForAll @IntRange(min = 0, max = 5) int epoch) {
        assertThat(a.insert(FROZEN, epoch).keys()).doesNotContain(FROZEN);
        assertThat(a.canInsert(FROZEN, epoch)).isFalse();
    }

    @Property
    void aFrozenKeyStaysFrozenAcrossMerges(@ForAll("frozenReplicas") EpochAwareCausalSet a,
                                           @ForAll("replicas") EpochAwareCausalSet b,
                                           @ForAll @IntRange(min = 0, max = 5) int epoch) {
        EpochAwareCausalSet merged = a.merge(b);
        assertThat(merged.insert(FROZEN, epoch).keys()).doesNotContain(FROZEN);
        assertThat(merged.isFrozen(FROZEN)).isTrue();
    }

    // ── message delivery ───────────────────────────────────────────────────────

    @Property
    void outOfOrderDeliveryConverges(@ForAll("replicas") EpochAwareCausalSet a,
                                     @ForAll("replicas") EpochAwareCausalSet b) {
        assertThat(a.merge(b).merge(a)).isEqualTo(b.merge(a).merge(b));
    }

    @Property
    void duplicateDeliveryIsHarmless(@ForAll("replicas") EpochAwareCausalSet a) {
        assertThat(a.merge(a).merge(a)).isEqualTo(a);
    }

    @Property
    void partialSynchronisationConvergesWhenBothSidesSeeEverything(
            @ForAll("replicas") EpochAwareCausalSet a,
            @ForAll("replicas") EpochAwareCausalSet b) {
        EpochAwareCausalSet ab = a.merge(b);
        EpochAwareCausalSet ba = b.merge(a);
        assertThat(ab.merge(ba)).isEqualTo(ba.merge(ab));
    }

    // ── persistence ────────────────────────────────────────────────────────────

    @Property
    void theDeletionHistorySurvivesARestart(@ForAll("freshReplicas") EpochAwareCausalSet a,
                                            @ForAll @IntRange(min = 0, max = 5) int epoch) {
        EpochAwareCausalSet tombstoned = a.tombstoneAt(K1, epoch);
        EpochAwareCausalSet restarted = EpochAwareCausalSet.fromJson(tombstoned.toJson());
        assertThat(restarted).isEqualTo(tombstoned);
        assertThat(restarted.isTombstoned(K1)).isTrue();
        assertThat(restarted.tombstoneEpoch(K1)).isEqualTo(epoch);
        // A restart must not make the key insertable again at an older epoch.
        assertThat(restarted.canInsert(K1, epoch)).isFalse();
    }

    @Property
    void serialisationIsDeterministic(@ForAll("replicas") EpochAwareCausalSet a) {
        assertThat(a.toJson()).isEqualTo(a.toJson());
    }

    // ── generators ─────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<EpochAwareCausalSet> replicas() {
        return Combinators.combine(frozenSets(), epochMaps(), epochMaps())
                .as(EpochAwareCausalSet::new);
    }

    /** Replicas with no tombstones yet — the starting point of a lifecycle. */
    @Provide
    Arbitrary<EpochAwareCausalSet> freshReplicas() {
        return Combinators.combine(frozenSets(), epochMaps(), emptyEpochMaps())
                .as(EpochAwareCausalSet::new);
    }

    /** Replicas in which the frozen key is frozen. */
    @Provide
    Arbitrary<EpochAwareCausalSet> frozenReplicas() {
        return Combinators.combine(frozenKeySets(), epochMaps(), epochMaps())
                .as(EpochAwareCausalSet::new);
    }

    private Arbitrary<Set<String>> frozenSets() {
        return Arbitraries.of(Set.of(), Set.of(FROZEN));
    }

    private Arbitrary<Set<String>> frozenKeySets() {
        // Not Arbitraries.of(Set.of(FROZEN)): jqwik's of() has a Collection
        // overload that would infer the element type instead of the set type.
        return Arbitraries.of(FROZEN).map(Set::of);
    }

    private Arbitrary<Map<String, Long>> emptyEpochMaps() {
        return Arbitraries.just(Map.<String, Long>of());
    }

    private Arbitrary<Map<String, Long>> epochMaps() {
        Arbitrary<String> keys = Arbitraries.of(K1, K2);
        Arbitrary<Long> epochs = Arbitraries.longs().between(0L, 5L);
        return Combinators.combine(keys, epochs)
                .as((k, e) -> Map.of(k, e))
                .list().ofMinSize(0).ofMaxSize(2)
                .map(list -> {
                    // Merge the generated pairs into ONE map: a replica holds
                    // at most one insert epoch and one tombstone epoch per key.
                    Map<String, Long> out = new TreeMap<>();
                    list.forEach(out::putAll);
                    return out;
                });
    }

    // ── explicit examples ──────────────────────────────────────────────────────

    @org.junit.jupiter.api.Test
    void anEmptySetIsEmpty() {
        EpochAwareCausalSet set = new EpochAwareCausalSet();
        assertThat(set.keys()).isEmpty();
        assertThat(set.insert(K1, 0).keys()).containsExactly(K1);
    }

    @org.junit.jupiter.api.Test
    void aTombstoneAtTheSameEpochBlocksTheKey() {
        EpochAwareCausalSet set = new EpochAwareCausalSet();
        set = set.insert(K1, 3);
        assertThat(set.keys()).containsExactly(K1);
        set = set.tombstoneAt(K1, 3);
        assertThat(set.keys()).isEmpty();
        assertThat(set.insert(K1, 3).keys()).isEmpty();
        assertThat(set.insert(K1, 4).keys()).containsExactly(K1);
    }

    @org.junit.jupiter.api.Test
    void aMalformedPayloadIsRejected() {
        assertThatThrownBy(() -> EpochAwareCausalSet.fromJson("not json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EpochAwareCausalSet.fromJson("{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
        assertThatThrownBy(() -> EpochAwareCausalSet.fromJson("{\"schemaVersion\":99}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }

    @org.junit.jupiter.api.Test
    void aContradictoryPayloadIsRejected() {
        // An element whose insert epoch is covered by a tombstone is a state
        // the structure can never produce.
        String json = "{\"schemaVersion\":2,\"elements\":[\"k1\"],"
                + "\"tombstoneEpochs\":{\"k1\":5},\"insertEpochs\":{\"k1\":1}}";
        assertThatThrownBy(() -> EpochAwareCausalSet.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Contradictory");
    }
}
