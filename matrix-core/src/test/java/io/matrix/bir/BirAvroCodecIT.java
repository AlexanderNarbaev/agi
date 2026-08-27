package io.matrix.bir;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link BirAvroCodec} (SPEC-002 FR-A1): the codec must
 * survive a real file-system round-trip (encode → bytes → file → bytes →
 * decode), not just an in-memory one, because that is the path used by the
 * federation channel and the BIR registry persistence.
 */
class BirAvroCodecIT {

    private static TtForm majority3() {
        long[] table = {0b11101000}; // minterms 3,5,6,7
        return new TtForm(3, table, "codec-it", 1.0);
    }

    @Test
    void fileSystemRoundTripPreservesAllForms(@TempDir Path tmp) throws Exception {
        TtForm tt = majority3();
        ClauseSetForm cs = BirCompiler.ttToClauseSet(tt);
        BddForm bdd = BirCompiler.ttToBdd(tt);

        Path artifact = tmp.resolve("bir_artifact.avro");

        // encode → write → read → decode, for every form
        for (var pair : java.util.List.of(
                java.util.Map.entry("tt-1", (Bir) tt),
                java.util.Map.entry("cs-1", (Bir) cs),
                java.util.Map.entry("bdd-1", (Bir) bdd))) {
            byte[] payload = BirAvroCodec.encode(pair.getKey(), pair.getValue(), 1L);
            Files.write(artifact, payload);

            byte[] readBack = Files.readAllBytes(artifact);
            BirAvroCodec.Decoded decoded = BirAvroCodec.decode(readBack);
            assertThat(decoded.id()).isEqualTo(pair.getKey());
            assertThat(BooleanRuntime.equivalent(decoded.bir(), pair.getValue())).isTrue();
            assertThat(decoded.version()).isEqualTo(BirAvroCodec.SCHEMA_VERSION);
        }
    }

    @Test
    void largePayloadRoundTripsThroughFileSystem(@TempDir Path tmp) throws Exception {
        // 18 inputs, 262144-cell TT — exercises the binary payload path
        int n = 18;
        int cells = 1 << n;
        long[] table = new long[cells / 64];
        for (int i = 0; i < table.length; i++) {
            table[i] = (i % 2 == 0) ? 0xF0F0F0F0F0F0F0F0L : 0x0F0F0F0F0F0F0F0FL;
        }
        TtForm large = new TtForm(n, table, "codec-it-large", 1.0);

        Path artifact = tmp.resolve("large.avro");
        Files.write(artifact, BirAvroCodec.encode("large-1", large, 0L));

        BirAvroCodec.Decoded decoded = BirAvroCodec.decode(Files.readAllBytes(artifact));
        assertThat(decoded.bir()).isInstanceOf(TtForm.class);
        assertThat(decoded.contentHash()).containsExactly(large.contentHash());
        assertThat(BooleanRuntime.equivalent(decoded.bir(), large)).isTrue();
    }
}
