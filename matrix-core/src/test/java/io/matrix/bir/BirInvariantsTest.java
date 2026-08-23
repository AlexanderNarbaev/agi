package io.matrix.bir;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SPEC-002 invariants enforced in bir/:
 * INV-2 (literal limit via config + clause range validation + metric),
 * INV-3 (measured fidelity for lossy forms), INV-4 (mandatory provenance),
 * and FR-A1 (espresso-type minimization in TT→CLAUSESET, exact).
 */
class BirInvariantsTest {

    private static TtForm ttOf(int k, long... table) {
        return new TtForm(k, table, "test", 1.0);
    }

    // ─── INV-3: fidelity validation ───

    @Test
    void fidelityOutOfRangeRejected() {
        long[] table = {0b1000L};
        assertThrows(IllegalArgumentException.class,
                () -> new TtForm(2, table, "test", Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new TtForm(2, table, "test", -0.1));
        assertThrows(IllegalArgumentException.class,
                () -> new TtForm(2, table, "test", 1.1));
    }

    @Test
    void unmeasuredLossyRejected() {
        long[] table = {0b1000L};
        assertThrows(IllegalArgumentException.class,
                () -> new TtForm(2, table, "test", 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new ClauseSetForm(2, List.of(), "test", 0.9));
    }

    @Test
    void lossyFactoryAcceptsMeasuredFidelity() {
        long[] table = {0b1000L};
        TtForm tt = TtForm.lossy(2, table, "measured-roundtrip", 0.5);
        assertEquals(0.5, tt.fidelity());
        ClauseSetForm cs = ClauseSetForm.lossy(2, List.of(), "measured-roundtrip", 0.75);
        assertEquals(0.75, cs.fidelity());
    }

    @Test
    void exactFormsUnaffected() {
        long[] table = {0b1000L};
        assertEquals(1.0, new TtForm(2, table, "test", 1.0).fidelity());
    }

    // ─── INV-4: provenance required at registration ───

    @Test
    void registryRejectsMissingProvenance() {
        BirRegistry registry = new BirRegistry();
        long[] table = {0b1000L};
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("bir:a", new TtForm(2, table, null, 1.0), "a", 0.0, null));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("bir:b", new TtForm(2, table, "  ", 1.0), "b", 0.0, null));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("bir:c", new TtForm(2, table, "unknown", 1.0), "c", 0.0, null));
    }

    @Test
    void registryAcceptsRealProvenance() {
        BirRegistry registry = new BirRegistry();
        long[] table = {0b1000L};
        BirRegistry.Entry e = registry.register(
                "bir:ok", new TtForm(2, table, "test-genesis", 1.0), "ok", 0.0, null);
        assertEquals("test-genesis", e.provenance());
        assertEquals(1, registry.size());
    }

    // ─── INV-2: literal limit config + clause range validation ───

    @Test
    void maxLiteralsDefault() {
        System.clearProperty(BirLimits.MAX_LITERALS_PROPERTY);
        assertEquals(BirLimits.DEFAULT_MAX_LITERALS, BirLimits.maxLiterals());
    }

    @Test
    void maxLiteralsFromProperty() {
        try {
            System.setProperty(BirLimits.MAX_LITERALS_PROPERTY, "64");
            assertEquals(64, BirLimits.maxLiterals());
            // inputBits beyond the configured limit is rejected
            assertThrows(IllegalArgumentException.class,
                    () -> new ClauseSetForm(65, List.of(), "test", 1.0));
        } finally {
            System.clearProperty(BirLimits.MAX_LITERALS_PROPERTY);
        }
    }

    @Test
    void clauseLiteralOutOfRangeRejected() {
        // inputBits=3 but clause references x5
        var clause = new ClauseSetForm.Clause(new long[]{1L << 5}, new long[]{0L});
        assertThrows(IllegalArgumentException.class,
                () -> new ClauseSetForm(3, List.of(clause), "test", 1.0));
        // Same bit inside range is fine
        var ok = new ClauseSetForm.Clause(new long[]{1L << 2}, new long[]{0L});
        assertDoesNotThrow(() -> new ClauseSetForm(3, List.of(ok), "test", 1.0));
    }

    @Test
    void clauseSetMetricRecordedWhenAttached() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BirMetrics.attach(registry);
        try {
            var clause = new ClauseSetForm.Clause(new long[]{1L}, new long[]{2L});
            new ClauseSetForm(2, List.of(clause), "test", 1.0);
            assertEquals(1.0, registry.counter("matrix.bir.clauseset.created").count());
            assertEquals(1, registry.summary("matrix.bir.clauseset.clauses").count());
            assertEquals(2.0, registry.summary("matrix.bir.clauseset.literals").totalAmount());
        } finally {
            BirMetrics.detach();
        }
    }

    // ─── FR-A1: espresso-type minimization in TT→CLAUSESET ───

    private static void assertExact(TtForm tt, ClauseSetForm cs) {
        int k = tt.k();
        long[] in = new long[1];
        long[] outTt = new long[1];
        long[] outCs = new long[1];
        for (int i = 0; i < (1 << k); i++) {
            in[0] = i;
            tt.eval(in, outTt);
            cs.eval(in, outCs);
            assertEquals(outTt[0], outCs[0], "mismatch at input " + i);
        }
    }

    @Test
    void ttToClauseSetMinimizesMajority3() {
        // majority-3: minterms 3,5,6,7 → minimal DNF has 3 clauses (raw would be 4)
        long[] table = {(1L << 3) | (1L << 5) | (1L << 6) | (1L << 7)};
        var tt = ttOf(3, table);
        ClauseSetForm cs = BirCompiler.ttToClauseSet(tt);
        assertEquals(3, cs.clauses().size());
        assertExact(tt, cs);
    }

    @Test
    void ttToClauseSetConstants() {
        var zero = BirCompiler.ttToClauseSet(ttOf(2, 0L));
        assertTrue(zero.clauses().isEmpty());
        assertExact(ttOf(2, 0L), zero);

        var one = BirCompiler.ttToClauseSet(ttOf(2, 0b1111L));
        assertEquals(1, one.clauses().size());
        assertExact(ttOf(2, 0b1111L), one);
    }

    @Test
    void ttToClauseSetEspressoPathExact() {
        // k=13 forces the Espresso path (k > 12); f = 1 iff bits 0..2 all set.
        int k = 13;
        int size = 1 << k;
        long[] table = new long[(size + 63) / 64];
        int minterms = 0;
        for (int i = 0; i < size; i++) {
            if ((i & 0b111) == 0b111) {
                table[i >>> 6] |= (1L << (i & 63));
                minterms++;
            }
        }
        var tt = new TtForm(k, table, "test", 1.0);
        ClauseSetForm cs = BirCompiler.ttToClauseSet(tt);
        assertExact(tt, cs);
        // Minimized: fewer clauses than raw minterm expansion
        assertTrue(cs.clauses().size() < minterms,
                "expected minimization: " + cs.clauses().size() + " clauses vs " + minterms + " minterms");
    }
}
