package io.matrix.bir;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boolean algebra over {@link BddForm} (SPEC-002 WAL items #2+#3): the seven
 * binary connectives via {@link BddForm#apply}, complement via
 * {@link BddForm#not}, constants, and construction-order-independent
 * {@link BddForm#equivalentTo}.
 *
 * <p>All generators are deterministic (fixed seeds / integer arithmetic), no
 * unseeded {@code Random} and no wall-clock.
 */
class BirBooleanAlgebraTest {

    // ─── generators ───

    record TtPair(TtForm a, TtForm b) {}

    @Provide
    Arbitrary<TtForm> ttK1To7() {
        return Arbitraries.integers().between(1, 7).flatMap(k -> {
            int words = ((1 << k) + 63) / 64;
            return Arbitraries.longs().array(long[].class).ofSize(words)
                    .map(w -> randomTt(k, w));
        });
    }

    @Provide
    Arbitrary<TtPair> ttPairK1To7() {
        return Arbitraries.integers().between(1, 7).flatMap(k -> {
            int words = ((1 << k) + 63) / 64;
            Arbitrary<long[]> wa = Arbitraries.longs().array(long[].class).ofSize(words);
            Arbitrary<long[]> wb = Arbitraries.longs().array(long[].class).ofSize(words);
            return Combinators.combine(wa, wb)
                    .as((x, y) -> new TtPair(randomTt(k, x), randomTt(k, y)));
        });
    }

    @Provide
    Arbitrary<BddForm.Op> ops() {
        return Arbitraries.of(BddForm.Op.values());
    }

    private static TtForm randomTt(int k, long[] words) {
        long[] masked = words.clone();
        int bits = (1 << k) & 63;
        if (bits != 0) masked[masked.length - 1] &= (1L << bits) - 1;
        return new TtForm(k, masked, "jqwik-bool-alg", 1.0);
    }

    // ─── helpers ───

    private static long evalBdd(BddForm bdd, int input) {
        long[] in = {input};
        long[] out = new long[1];
        bdd.eval(in, out);
        return out[0];
    }

    private static boolean ttBit(TtForm tt, int i) {
        long[] t = tt.table();
        return ((t[i >>> 6] >>> (i & 63)) & 1L) == 1L;
    }

    /** BDD of {@code x_var == bit} as a k-ary function (single decision node). */
    private static BddForm literalBdd(int k, int var, boolean bit) {
        BddForm.Builder b = new BddForm.Builder();
        int root = b.mk(var, bit ? 0 : 1, bit ? 1 : 0);
        return b.build(k, "literal", root);
    }

    /** Single minterm BDD: AND of all k literals. */
    private static BddForm mintermBdd(int k, int minterm) {
        BddForm acc = BddForm.constant(k, true, "minterm-acc");
        for (int v = 0; v < k; v++) {
            boolean bit = ((minterm >> v) & 1) == 1;
            acc = acc.apply(literalBdd(k, v, bit), BddForm.Op.AND);
        }
        return acc;
    }

    /** Same function built via OR-composition of its 1-minterms (different node ids). */
    private static BddForm composeFromMinterms(TtForm tt) {
        int k = tt.k();
        BddForm acc = BddForm.constant(k, false, "or-compose");
        for (int i = 0; i < (1 << k); i++) {
            if (ttBit(tt, i)) acc = acc.apply(mintermBdd(k, i), BddForm.Op.OR);
        }
        return acc;
    }

    private static BddForm andGate() {
        BddForm.Builder b = new BddForm.Builder();
        int x1 = b.mk(1, 0, 1);
        int root = b.mk(0, 0, x1); // x0 ? x1 : 0
        return b.build(2, "and-gate", root);
    }

    private static BddForm orGate() {
        BddForm.Builder b = new BddForm.Builder();
        int x1 = b.mk(1, 0, 1);
        int root = b.mk(0, x1, 1); // x0 ? 1 : x1
        return b.build(2, "or-gate", root);
    }

    // ─── unit tests ───

    @Test
    void applyAllOpsMatchTruthTableSemantics() {
        BddForm andB = andGate();
        BddForm orB = orGate();
        for (BddForm.Op op : BddForm.Op.values()) {
            BddForm r = andB.apply(orB, op);
            for (int i = 0; i < 4; i++) {
                boolean a = (i == 3);          // x0 ∧ x1
                boolean b = (i >= 1);          // x0 ∨ x1
                boolean expected = op.eval(a, b);
                assertThat(evalBdd(r, i))
                        .as("op=%s input=%d", op, i)
                        .isEqualTo(expected ? 1L : 0L);
            }
        }
    }

    @Test
    void notIsInvolutionAndFlipsEval() {
        BddForm f = andGate();
        BddForm nf = f.not();
        for (int i = 0; i < 4; i++) {
            long fe = evalBdd(f, i);
            assertThat(evalBdd(nf, i)).as("not eval flip at %d", i)
                    .isEqualTo(fe == 1 ? 0L : 1L);
        }
        assertThat(nf.not().equivalentTo(f)).as("double negation").isTrue();
    }

    @Test
    void constantEvaluatesCorrectly() {
        BddForm one = BddForm.constant(3, true, "one");
        BddForm zero = BddForm.constant(3, false, "zero");
        for (int i = 0; i < 8; i++) {
            assertThat(evalBdd(one, i)).isEqualTo(1L);
            assertThat(evalBdd(zero, i)).isEqualTo(0L);
        }
        assertThatThrownBy(() -> BddForm.constant(0, true, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applyWithConstantIsIdentityOrComplement() {
        BddForm f = andGate();
        BddForm one = BddForm.constant(2, true, "one");
        BddForm zero = BddForm.constant(2, false, "zero");
        assertThat(f.apply(one, BddForm.Op.AND).equivalentTo(f)).isTrue();    // f ∧ 1 = f
        assertThat(f.apply(zero, BddForm.Op.OR).equivalentTo(f)).isTrue();    // f ∨ 0 = f
        assertThat(f.apply(one, BddForm.Op.XOR).equivalentTo(f.not())).isTrue(); // f ⊕ 1 = ¬f
        assertThat(f.apply(f, BddForm.Op.XOR).equivalentTo(zero)).isTrue();   // f ⊕ f = 0
        assertThat(f.apply(f, BddForm.Op.IMPLIES).equivalentTo(one)).isTrue(); // f → f = 1
    }

    @Test
    void equivalentToOrderIndependence() {
        // f = x0 ∧ (x1 ∨ x2), built bottom-up (Shannon, deepest var first) …
        BddForm.Builder bottomUp = new BddForm.Builder();
        int x2 = bottomUp.mk(2, 0, 1);
        int or12 = bottomUp.mk(1, x2, 1);
        int rootA = bottomUp.mk(0, 0, or12);
        BddForm a = bottomUp.build(3, "bottom-up", rootA);

        // … and top-first with forward child references → different node ids.
        BddForm.Builder topFirst = new BddForm.Builder();
        int rootB = topFirst.mk(0, 0, 4); // node2 = (var0, low=0, high=node4)
        topFirst.mk(2, 0, 1);             // node3 = x2
        topFirst.mk(1, 3, 1);             // node4 = x1 ∨ x2
        BddForm b = topFirst.build(3, "top-first", rootB);

        assertThat(a.equivalentTo(b)).as("same function, different construction order").isTrue();
        assertThat(b.equivalentTo(a)).isTrue();
        // Prove the byte encodings really differ (old Arrays.equals impl would fail here).
        assertThat(a.contentHash()).isNotEqualTo(b.contentHash());
    }

    @Test
    void equivalentToRejectsDifferentFunctions() {
        BddForm andB = andGate();
        BddForm orB = orGate();
        assertThat(andB.equivalentTo(orB)).isFalse();
        assertThat(andB.equivalentTo(null)).isFalse();
        assertThat(andB.equivalentTo(BddForm.constant(3, true, "other-arity"))).isFalse();
    }

    @Test
    void applyRejectsNullAndMismatchedArity() {
        BddForm f = andGate();
        assertThatThrownBy(() -> f.apply(null, BddForm.Op.AND))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.apply(BddForm.constant(3, true, "wide"), BddForm.Op.AND))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── jqwik properties ───

    @Property(tries = 20, seed = "20260830")
    void applyMatchesTruthTableSemantics(@ForAll("ttPairK1To7") TtPair pair,
                                         @ForAll("ops") BddForm.Op op) {
        TtForm a = pair.a();
        TtForm b = pair.b();
        int k = a.k();
        BddForm r = BirCompiler.ttToBdd(a).apply(BirCompiler.ttToBdd(b), op);
        long[] in = new long[1];
        long[] oa = new long[1];
        long[] ob = new long[1];
        long[] out = new long[1];
        for (int i = 0; i < (1 << k); i++) {
            in[0] = i;
            a.eval(in, oa);
            b.eval(in, ob);
            r.eval(in, out);
            boolean expected = op.eval(oa[0] == 1, ob[0] == 1);
            assertThat(out[0]).as("op=%s k=%d input=%d", op, k, i)
                    .isEqualTo(expected ? 1L : 0L);
        }
    }

    @Property(tries = 20, seed = "20260831")
    void notFlipsEveryInput(@ForAll("ttK1To7") TtForm tt) {
        BddForm b = BirCompiler.ttToBdd(tt);
        BddForm nb = b.not();
        long[] in = new long[1];
        long[] ot = new long[1];
        long[] out = new long[1];
        for (int i = 0; i < (1 << tt.k()); i++) {
            in[0] = i;
            tt.eval(in, ot);
            nb.eval(in, out);
            assertThat(out[0]).as("not flip k=%d input=%d", tt.k(), i)
                    .isEqualTo(ot[0] == 1 ? 0L : 1L);
        }
    }

    @Property(tries = 15, seed = "20260832")
    void equivalenceIndependentOfConstructionOrder(@ForAll("ttK1To7") TtForm tt) {
        BddForm direct = BirCompiler.ttToBdd(tt);
        BddForm composed = composeFromMinterms(tt);
        assertThat(direct.equivalentTo(composed))
                .as("direct vs minterm-composed BDD must be equivalent (k=%d)", tt.k())
                .isTrue();
        assertThat(composed.equivalentTo(direct)).isTrue();
    }
}
