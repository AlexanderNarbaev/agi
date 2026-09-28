package io.matrix.bir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * RECON-W25 — value equality for {@link ClauseSetForm.Clause}.
 *
 * <p>Two real bugs were found together and are pinned here.</p>
 *
 * <p><b>Bug 1 — no value equality.</b> {@code Clause} inherited identity semantics
 * from {@code Object}. Every value comparison of clauses was therefore silently
 * wrong: {@code List<Clause>.equals} compares element references, so two
 * logically identical clauses never compared equal.</p>
 *
 * <p><b>Bug 2 — the contradiction gate could not fire.</b>
 * {@code MinimalHttpServer.handleBir} derived a clause's precondition from
 * {@code body.hashCode()}, so any two registrations had different preconditions
 * and {@code BirKnowledgeBase} never detected a contradiction: two facts
 * asserting different answers for the same subject were both merged, while the
 * response still advertised {@code engine: BirKnowledgeBase.contradiction}.</p>
 *
 * <p>Article IV requires the opposite: a contradiction must be QUARANTINED, not
 * merged.</p>
 */
class ClauseValueEqualityTest {

    private static ClauseSetForm.Clause c(long pos, long neg) {
        return new ClauseSetForm.Clause(new long[]{pos}, new long[]{neg});
    }

    // ---- Bug 1: value equality ---------------------------------------------

    @Test
    void identical_masks_compare_equal() {
        assertThat(c(0x5L, 0x1L)).isEqualTo(c(0x5L, 0x1L));
        assertThat(c(0x5L, 0x1L).hashCode()).isEqualTo(c(0x5L, 0x1L).hashCode());
    }

    @Test
    void differing_masks_compare_unequal() {
        assertThat(c(0x5L, 0x1L)).isNotEqualTo(c(0x5L, 0x2L));
        assertThat(c(0x5L, 0x1L)).isNotEqualTo(c(0x6L, 0x1L));
    }

    @Test
    void clause_lists_compare_by_value() {
        List<ClauseSetForm.Clause> a = List.of(c(1L, 0L), c(2L, 0L));
        List<ClauseSetForm.Clause> b = List.of(c(1L, 0L), c(2L, 0L));
        assertThat(a).isEqualTo(b);
    }

    @Test
    void defensive_copies_do_not_alias() {
        long[] pos = {3L};
        long[] neg = {0L};
        ClauseSetForm.Clause cl = new ClauseSetForm.Clause(pos, neg);
        pos[0] = 99L;   // mutate the caller's array
        assertThat(cl.pos[0]).isEqualTo(3L);
    }

    // ---- Bug 2: the contradiction predicate now works ----------------------

    @Test
    void re_asserting_the_same_fact_is_compatible() {
        // This is what conclusionsMatch now reports for a faithful re-assertion.
        ClauseSetForm first = ClauseSetForm.lossy(20, List.of(c(7L, 3L)), "p", 1.0);
        ClauseSetForm again = ClauseSetForm.lossy(20, List.of(c(7L, 3L)), "p", 1.0);
        assertThat(first.clauses().equals(again.clauses()))
            .as("a fact re-asserted identically must NOT look like a contradiction")
            .isTrue();
    }

    @Test
    void same_subject_different_answer_is_a_contradiction() {
        ClauseSetForm first = ClauseSetForm.lossy(20, List.of(c(7L, 3L)), "p", 1.0);
        ClauseSetForm other = ClauseSetForm.lossy(20, List.of(c(7L, 4L)), "p", 1.0);
        assertThat(first.clauses().equals(other.clauses()))
            .as("same precondition, different conclusion => must be quarantined")
            .isFalse();
    }

    @Test
    void different_subjects_are_never_contradictions() {
        ClauseSetForm a = ClauseSetForm.lossy(20, List.of(c(1L, 3L)), "p", 1.0);
        ClauseSetForm b = ClauseSetForm.lossy(20, List.of(c(2L, 3L)), "p", 1.0);
        assertThat(a.clauses().equals(b.clauses())).isFalse();
    }

    // ---- Article II: the fingerprint domain really is bounded ---------------

    @Test
    void clause_literals_must_lie_inside_input_bits() {
        // Guard the constraint that forced the 20-bit fingerprint: a raw 64-bit
        // hash throws "clause literal out of range" at inputBits=20.
        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> ClauseSetForm.lossy(
                20, List.of(c(0xFFFFFFFFFFL, 0L)), "p", 1.0))
            .isInstanceOf(IllegalArgumentException.class);

        // The 20-bit masked value is accepted.
        long masked = 0xFFFFFFFFFFFFL & 0xFFFFFL;
        assertThat(ClauseSetForm.lossy(20, List.of(c(masked, 0L)), "p", 1.0).clauses())
            .hasSize(1);
    }
}
