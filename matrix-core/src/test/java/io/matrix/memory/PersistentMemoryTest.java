package io.matrix.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 163 — PersistentMemory unit tests. */
class PersistentMemoryTest {

    @Test
    void saveAndLoadRoundtrip(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("mem.jsonl");
        PersistentMemory m1 = new PersistentMemory(file);
        m1.store("k1", new byte[]{1, 2, 3});
        m1.store("k2", "hello".getBytes());
        m1.save();

        PersistentMemory m2 = new PersistentMemory(file);
        m2.load();
        assertThat(m2.size()).isEqualTo(2);
        assertThat(m2.entries().get(0).key()).isEqualTo("k1");
        assertThat(m2.entries().get(0).payload()).isEqualTo(new byte[]{1, 2, 3});
        assertThat(m2.entries().get(1).payload()).isEqualTo("hello".getBytes());
    }

    @Test
    void loadNonExistentFileIsEmpty(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("does-not-exist.jsonl");
        PersistentMemory m = new PersistentMemory(file);
        m.load();
        assertThat(m.size()).isZero();
    }

    @Test
    void storeReplacesExisting(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("mem.jsonl");
        PersistentMemory m = new PersistentMemory(file);
        m.store("k", new byte[]{1});
        m.store("k", new byte[]{2, 3});
        assertThat(m.size()).isEqualTo(1);
        m.save();
        m.load();
        assertThat(m.size()).isEqualTo(1);
        assertThat(m.entries().get(0).payload()).isEqualTo(new byte[]{2, 3});
    }

    @Test
    void blankLinesIgnored(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("mem.jsonl");
        Files.writeString(file, "\n\n{\"key\":\"a\",\"payload\":\"YQ==\",\"accessCount\":0}\n\n");
        PersistentMemory m = new PersistentMemory(file);
        m.load();
        assertThat(m.size()).isEqualTo(1);
        // "a" base64 decoded
        assertThat(m.entries().get(0).key()).isEqualTo("a");
        assertThat(m.entries().get(0).payload()).isEqualTo(Base64.getDecoder().decode("YQ=="));
    }

    @Test
    void roundtripPreservesAccessCount(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("mem.jsonl");
        PersistentMemory m1 = new PersistentMemory(file);
        m1.store("k", new byte[]{1});
        m1.save();
        PersistentMemory m2 = new PersistentMemory(file);
        m2.load();
        assertThat(m2.entries().get(0).accessCount()).isZero();
    }
}
