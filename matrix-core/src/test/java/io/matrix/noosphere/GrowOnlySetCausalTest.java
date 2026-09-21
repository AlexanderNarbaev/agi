package io.matrix.noosphere;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W-C tests for the M4 Causal CRDT layer: {@link Crdt#mergeCausal} and
 * {@link Crdt#tombstoneAt} on {@link GrowOnlySet}. Covers the four TLA+
 * invariants: Monotonicity, TombstoneIrreversible, EventualConsistency,
 * FrozenImmutability (the latter is N/A for G-Set, asserted by omission).
 */
class GrowOnlySetCausalTest {

    @Test
    void mergeCausalIsIdempotentCommutativeAndAssociative() {
        GrowOnlySet a = new GrowOnlySet(setOf("x", "y"));
        GrowOnlySet b = new GrowOnlySet(setOf("y", "z"));
        GrowOnlySet c = new GrowOnlySet(setOf("z", "w"));

        // idempotent
        assertThat(a.mergeCausal(a).elements()).isEqualTo(a.elements());
        // commutative
        assertThat(a.mergeCausal(b).elements()).isEqualTo(b.mergeCausal(a).elements());
        // associative
        assertThat(a.mergeCausal(b).mergeCausal(c).elements())
                .isEqualTo(a.mergeCausal(b.mergeCausal(c)).elements());
    }

    @Test
    void tombstoneAtRemovesElementAndIsIrreversible() {
        GrowOnlySet a = new GrowOnlySet(setOf("k"));
        GrowOnlySet tombstoned = a.tombstoneAt("k", 5L);
        // element is gone
        assertThat(tombstoned.elements()).doesNotContain("k");
        // tombstone marker recorded
        assertThat(tombstoned.tombstones()).contains("k");
        // subsequent merge with a replica that still has "k" must NOT re-introduce it
        GrowOnlySet b = new GrowOnlySet(setOf("k", "x"));
        GrowOnlySet merged = tombstoned.merge(b);
        assertThat(merged.elements()).doesNotContain("k");
        assertThat(merged.elements()).contains("x");
        // and the tombstone survives the merge
        assertThat(merged.tombstones()).contains("k");
    }

    @Test
    void tombstoneAtAppliesAcrossBidirectionalMerges() {
        // tombstone on side B, merge both ways, key never returns
        GrowOnlySet a = new GrowOnlySet(setOf("k"));
        GrowOnlySet b = new GrowOnlySet(setOf("k")).tombstoneAt("k", 7L);

        GrowOnlySet ab = a.merge(b);
        GrowOnlySet ba = b.merge(a);

        assertThat(ab.elements()).doesNotContain("k");
        assertThat(ba.elements()).doesNotContain("k");
        // both sides carry the tombstone
        assertThat(ab.tombstones()).contains("k");
        assertThat(ba.tombstones()).contains("k");
    }

    @Test
    void eventualConsistencyAcrossReplicas() {
        // Three replicas converge after exchanging state in different orders.
        GrowOnlySet r1 = new GrowOnlySet(setOf("a", "b"));
        GrowOnlySet r2 = new GrowOnlySet(setOf("b", "c"));
        GrowOnlySet r3 = new GrowOnlySet(setOf("c", "d"));

        // Gossip: r1 ← r2 ← r3, then r1 ← r3
        GrowOnlySet after1 = r1.mergeCausal(r2).mergeCausal(r3).mergeCausal(r3);
        // Same gossip in reverse order
        GrowOnlySet after2 = r3.mergeCausal(r2).mergeCausal(r1).mergeCausal(r1);

        assertThat(after1.elements()).isEqualTo(after2.elements());
        assertThat(after1.elements()).containsExactlyInAnyOrder("a", "b", "c", "d");
    }

    @Test
    void monotonicityOfElementsSet() {
        // once an element is added, no future merge can remove it (modulo tombstone)
        GrowOnlySet base = new GrowOnlySet(setOf("stable"));
        GrowOnlySet other = new GrowOnlySet(setOf("stable", "added"));
        GrowOnlySet merged = base.mergeCausal(other).mergeCausal(new GrowOnlySet(setOf()));
        assertThat(merged.elements()).contains("stable");
    }

    @Test
    void tombstoneAtRejectsNullKey() {
        GrowOnlySet base = new GrowOnlySet(setOf());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> base.tombstoneAt(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultTombstoneAtThrowsForNonDeletionCrdts() {
        // Default implementation of tombstoneAt in Crdt throws
        // UnsupportedOperationException. GrowOnlySet overrides; for any
        // other Crdt<T> that does not, calling it must fail loudly.
        // Verifying via a minimal Crdt<String> impl that omits the override.
        Crdt<String> minimal = new Crdt<>() {
            @Override public String merge(String other) { return other; }
            @Override public String toJson() { return "\"\""; }
        };
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> minimal.tombstoneAt("k", 1L))
                .isInstanceOf(UnsupportedOperationException.class);
        // and mergeCausal still falls back to merge
        assertThat(minimal.mergeCausal("z")).isEqualTo("z");
    }

    private static Set<String> setOf(String... xs) {
        return new HashSet<>(java.util.Arrays.asList(xs));
    }
}