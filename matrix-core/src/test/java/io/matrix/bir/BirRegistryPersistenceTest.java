package io.matrix.bir;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BirRegistryPersistenceTest {

    @Test
    void register_append_persistence_replay_roundtrip(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("bir-registry.ndjson");
        BirRegistryPersistence pers = new BirRegistryPersistence(file);

        // Register 3 BIRs into the first registry, persisting each.
        BirRegistry r1 = new BirRegistry();
        var c1 = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0x1L});
        var c2 = new ClauseSetForm.Clause(new long[]{0x3L}, new long[]{0x2L});
        var c3 = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0x3L});
        Bir b1 = ClauseSetForm.lossy(4, List.of(c1), "src-A", 0.8);
        Bir b2 = ClauseSetForm.lossy(4, List.of(c2), "src-B", 0.9);
        Bir b3 = ClauseSetForm.lossy(4, List.of(c3), "src-C", 0.7);
        BirRegistry.Entry e1 = r1.register("rule-1", b1, "rule-A", 0.8, "hello".getBytes());
        BirRegistry.Entry e2 = r1.register("rule-2", b2, "rule-B", 0.9, "world".getBytes());
        BirRegistry.Entry e3 = r1.register("rule-3", b3, "rule-C", 0.7, "x".getBytes());
        pers.appendRegister(e1);
        pers.appendRegister(e2);
        pers.appendRegister(e3);
        assertThat(pers.lineCount()).isEqualTo(3);

        // Replay into a fresh registry and verify size + first ID.
        BirRegistry r2 = new BirRegistry();
        int replayed = pers.replayInto(r2);
        assertThat(replayed).isEqualTo(3);
        assertThat(r2.size()).isEqualTo(3);
        assertThat(r2.get("rule-1") != null);
        assertThat(r2.get("rule-3") != null);
    }

    @Test
    void empty_file_replays_zero_entries(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("empty.ndjson");
        BirRegistryPersistence pers = new BirRegistryPersistence(file);
        BirRegistry r = new BirRegistry();
        assertThat(pers.replayInto(r)).isEqualTo(0);
    }
}
