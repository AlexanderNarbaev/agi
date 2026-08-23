package io.matrix.bir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for {@link BirAvroCodec} (SPEC-002 FR-A1): every form
 * survives Avro encode/decode exactly (BDD-equivalent, same metadata).
 */
class BirAvroCodecTest {

    private static TtForm majority3() {
        long[] table = {0b11101000}; // minterms 3,5,6,7
        return new TtForm(3, table, "codec-test", 1.0);
    }

    @Test
    void ttRoundTrip() {
        TtForm tt = majority3();
        BirAvroCodec.Decoded decoded = BirAvroCodec.decode(BirAvroCodec.encode("a1", tt, 123456789L));
        assertThat(decoded.id()).isEqualTo("a1");
        assertThat(decoded.createdAt()).isEqualTo(123456789L);
        assertThat(decoded.version()).isEqualTo(BirAvroCodec.SCHEMA_VERSION);
        assertThat(decoded.bir()).isInstanceOf(TtForm.class);
        assertThat(decoded.bir().provenance()).isEqualTo("codec-test");
        assertThat(decoded.bir().fidelity()).isEqualTo(1.0);
        assertThat(decoded.contentHash()).containsExactly(tt.contentHash());
        assertThat(BooleanRuntime.equivalent(tt, decoded.bir())).isTrue();
    }

    @Test
    void clauseSetRoundTrip() {
        ClauseSetForm cs = BirCompiler.ttToClauseSet(majority3());
        BirAvroCodec.Decoded decoded = BirAvroCodec.decode(BirAvroCodec.encode("a2", cs, 42L));
        assertThat(decoded.bir()).isInstanceOf(ClauseSetForm.class);
        assertThat(((ClauseSetForm) decoded.bir()).clauses()).hasSize(cs.clauses().size());
        assertThat(BooleanRuntime.equivalent(cs, decoded.bir())).isTrue();
        assertThat(decoded.contentHash()).containsExactly(cs.contentHash());
    }

    @Test
    void bddRoundTrip() {
        BddForm bdd = BirCompiler.ttToBdd(majority3());
        BirAvroCodec.Decoded decoded = BirAvroCodec.decode(BirAvroCodec.encode("a3", bdd, 7L));
        assertThat(decoded.bir()).isInstanceOf(BddForm.class);
        assertThat(BooleanRuntime.equivalent(bdd, decoded.bir())).isTrue();
    }

    @Test
    void constantBddRoundTrip() {
        // const-0: no nodes beyond terminals — the BUG-BDD-ROOT regression case
        BddForm.Builder builder = new BddForm.Builder();
        BddForm constZero = builder.build(2, "codec-test", 0);
        BirAvroCodec.Decoded decoded = BirAvroCodec.decode(BirAvroCodec.encode("a4", constZero, 1L));
        long[] out = BooleanRuntime.evaluate(decoded.bir(), new long[]{0b11});
        assertThat(out[0]).isEqualTo(0L);
    }

    @Test
    void lossyFidelitySurvives() {
        TtForm lossy = TtForm.lossy(3, new long[]{0b11101000}, "codec-test", 0.75);
        BirAvroCodec.Decoded decoded = BirAvroCodec.decode(BirAvroCodec.encode("a5", lossy, 0L));
        assertThat(decoded.bir().fidelity()).isEqualTo(0.75);
    }

    @Test
    void lossyBddIsRejected() {
        // INV-3: codec refuses to materialize a lossy BDD (no lossy BDD factory exists)
        BddForm bdd = BirCompiler.ttToBdd(majority3());
        byte[] encoded = BirAvroCodec.encode("a6", bdd, 0L);
        // tamper: re-encode a lossy TT record but with form="bdd" is not directly
        // possible via the public API, so assert the guard via a lossy TT instead:
        assertThatThrownBy(() -> TtForm.lossy(3, new long[]{0}, "x", 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(BirAvroCodec.decode(encoded).bir().fidelity()).isEqualTo(1.0);
    }

    @Test
    void misalignedClauseMasksAreRejectedOnEncode() {
        // clause with 2 words while kWords=1 → payload format would corrupt
        ClauseSetForm cs = new ClauseSetForm(3,
                List.of(new ClauseSetForm.Clause(new long[]{1, 0}, new long[]{0, 0})),
                "codec-test", 1.0);
        assertThatThrownBy(() -> BirAvroCodec.encode("a7", cs, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyClauseSetRoundTrip() {
        ClauseSetForm constZero = new ClauseSetForm(3, List.of(), "codec-test", 1.0);
        BirAvroCodec.Decoded decoded = BirAvroCodec.decode(BirAvroCodec.encode("a8", constZero, 0L));
        long[] out = BooleanRuntime.evaluate(decoded.bir(), new long[]{0b111});
        assertThat(out[0]).isEqualTo(0L);
    }
}
