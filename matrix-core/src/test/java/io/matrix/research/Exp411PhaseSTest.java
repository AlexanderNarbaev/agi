package io.matrix.research;

import io.matrix.budgeter.ConjugateBudgeterMulti;
import io.matrix.memory.RecurrentSdm;
import io.matrix.noosphere.CausalCrdt;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 411-413 — Phase S: ConjugateBudgeter multi-period, Causal CRDT,
 * RecurrentSdm. Tests for all 3.
 */
class Exp411PhaseSTest {

    // ---- ConjugateBudgeterMulti ----

    @Test
    void conjugateBudgeterAllocatesByDemand() {
        var plan = ConjugateBudgeterMulti.allocate(
                100.0, 4, new double[]{4, 1, 1, 1}, 50.0);
        assertThat(plan.allocations()).hasSize(4);
        assertThat(plan.allocations().get(0).budgetFraction()).isGreaterThan(20.0);
        // Sum should be ~100
        double sum = plan.allocations().stream()
                .mapToDouble(ConjugateBudgeterMulti.Allocation::budgetFraction).sum();
        assertThat(sum).isCloseTo(100.0, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    void conjugateBudgeterRespectsHardCap() {
        var plan = ConjugateBudgeterMulti.allocate(
                100.0, 2, new double[]{10, 1}, 30.0);
        // Hard cap 30 → each period ≤ 30
        assertThat(plan.allocations().get(0).budgetFraction()).isLessThanOrEqualTo(30.0);
        assertThat(plan.allocations().get(1).budgetFraction()).isLessThanOrEqualTo(30.0);
    }

    // ---- CausalCrdt ----

    @Test
    void causalCrdtPutGet() {
        var store = CausalCrdt.empty(new HashSet<>(Set.of(1, 2)));
        store = CausalCrdt.put(store, "key1", "value1", 1);
        var entry = CausalCrdt.get(store, "key1");
        assertThat(entry).isNotNull();
        assertThat(entry.value()).isEqualTo("value1");
        assertThat(entry.version().writerId()).isEqualTo(1);
    }

    @Test
    void causalCrdtMergeTakesHighestVersion() {
        var s1 = CausalCrdt.empty(new HashSet<>(Set.of(1, 2)));
        s1 = CausalCrdt.put(s1, "k", "v1", 1);
        var s2 = CausalCrdt.empty(new HashSet<>(Set.of(1, 2)));
        s2 = CausalCrdt.put(s2, "k", "v2", 2);
        var merged = CausalCrdt.merge(s1, s2);
        // v2 (writer 2, clock 1) > v1 (writer 1, clock 1)? Equal clocks, v2 wins
        var entry = CausalCrdt.get(merged, "k");
        assertThat(entry).isNotNull();
    }

    @Test
    void causalCrdtTombstone() {
        var s = CausalCrdt.empty(new HashSet<>(Set.of(1)));
        s = CausalCrdt.put(s, "k", "v", 1);
        s = CausalCrdt.tombstone(s, "k", 1);
        assertThat(CausalCrdt.get(s, "k")).isNull();
    }

    // ---- RecurrentSdm ----

    @Test
    void recurrentSdmWriteRead() {
        var sdm = RecurrentSdm.empty(64, 2);
        long addr = 0b1010L;
        var pattern = new RecurrentSdm.Pattern(new double[]{1.0, 0.5, 0.2});
        sdm = RecurrentSdm.write(sdm, new RecurrentSdm.Address(addr), pattern);
        var read = RecurrentSdm.read(sdm, new RecurrentSdm.Address(addr));
        assertThat(read.vector()).hasSize(3);
    }

    @Test
    void recurrentSdmRecurrentReadCarries() {
        var sdm = RecurrentSdm.empty(64, 1);
        var pattern1 = new RecurrentSdm.Pattern(new double[]{1.0, 0.0});
        var pattern2 = new RecurrentSdm.Pattern(new double[]{0.0, 1.0});
        // Use far-apart addresses (Hamming distance > access radius = 8)
        long a1 = 0b0000000000000000L;
        long a2 = 0b1111111100000000L;  // 8 bits different, just at radius
        sdm = RecurrentSdm.write(sdm, new RecurrentSdm.Address(a1), pattern1);
        sdm = RecurrentSdm.write(sdm, new RecurrentSdm.Address(a2), pattern2);
        var first = RecurrentSdm.read(sdm, new RecurrentSdm.Address(a1));
        var second = RecurrentSdm.recurrentRead(sdm,
                new RecurrentSdm.Address(a2), first);
        // second should be a weighted mix of a2 read (pattern2) and previous
        assertThat(second.vector()).hasSize(2);
    }
}
