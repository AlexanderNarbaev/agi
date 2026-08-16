package io.matrix.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BooleanIndexPersistenceTest {

    @TempDir
    Path tempDir;

    // --- Full Save/Load ---

    @Test
    void shouldSaveAndLoadFullIndex() throws Exception {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        index.add("doc1", new long[]{0xABCDEF1234567890L});
        index.add("doc2", new long[]{0x1234567890ABCDEL});

        Path file = tempDir.resolve("index.bin");
        BooleanIndexPersistence.save(index, file);

        assertThat(Files.exists(file)).isTrue();
        assertThat(BooleanIndexPersistence.fileSize(file)).isGreaterThan(0);

        BooleanIndex loaded = BooleanIndexPersistence.load(file);

        assertThat(loaded.size()).isEqualTo(2);
        assertThat(loaded.dimensions()).isEqualTo(64);
        assertThat(loaded.get("doc1")).isEqualTo(new long[]{0xABCDEF1234567890L});
        assertThat(loaded.get("doc2")).isEqualTo(new long[]{0x1234567890ABCDEL});
    }

    @Test
    void shouldSaveAndLoad128BitIndex() throws Exception {
        BooleanIndex index = BooleanIndex.builder().dimensions(128).build();
        index.add("wide", new long[]{0xAAAAL, 0xBBBBL});

        Path file = tempDir.resolve("index128.bin");
        BooleanIndexPersistence.save(index, file);

        BooleanIndex loaded = BooleanIndexPersistence.load(file);

        assertThat(loaded.dimensions()).isEqualTo(128);
        assertThat(loaded.get("wide")).isEqualTo(new long[]{0xAAAAL, 0xBBBBL});
    }

    @Test
    void shouldSaveEmptyIndex() throws Exception {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();

        Path file = tempDir.resolve("empty.bin");
        BooleanIndexPersistence.save(index, file);

        BooleanIndex loaded = BooleanIndexPersistence.load(file);

        assertThat(loaded.size()).isZero();
    }

    @Test
    void shouldRejectNullIndex() {
        assertThatThrownBy(() -> BooleanIndexPersistence.save(null, tempDir.resolve("x.bin")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullPath() {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        assertThatThrownBy(() -> BooleanIndexPersistence.save(index, null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- Incremental Save ---

    @Test
    void shouldAppendVectorsToExistingFile() throws Exception {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        index.add("doc1", new long[]{0x01L});
        index.add("doc2", new long[]{0x02L});
        index.add("doc3", new long[]{0x03L});

        Path file = tempDir.resolve("append.bin");

        // Save initial
        BooleanIndexPersistence.save(index, file);

        // Append only doc3
        BooleanIndexPersistence.append(index, file, List.of("doc3"));

        // Load with appends
        BooleanIndex loaded = BooleanIndexPersistence.loadWithAppends(file);

        assertThat(loaded.size()).isEqualTo(3);
        assertThat(loaded.get("doc1")).isEqualTo(new long[]{0x01L});
        assertThat(loaded.get("doc3")).isEqualTo(new long[]{0x03L});
    }

    @Test
    void shouldDoFullSaveWhenFileDoesNotExist() throws Exception {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        index.add("new", new long[]{0xFFL});

        Path file = tempDir.resolve("nonexistent.bin");
        BooleanIndexPersistence.append(index, file, List.of("new"));

        BooleanIndex loaded = BooleanIndexPersistence.load(file);

        assertThat(loaded.size()).isEqualTo(1);
        assertThat(loaded.get("new")).isEqualTo(new long[]{0xFFL});
    }

    @Test
    void shouldHandleMultipleAppends() throws Exception {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        Path file = tempDir.resolve("multi.bin");

        // First batch
        index.add("batch1_doc1", new long[]{0x01L});
        BooleanIndexPersistence.save(index, file);

        // Second batch
        index.add("batch2_doc1", new long[]{0x02L});
        BooleanIndexPersistence.append(index, file, List.of("batch2_doc1"));

        // Third batch
        index.add("batch3_doc1", new long[]{0x03L});
        BooleanIndexPersistence.append(index, file, List.of("batch3_doc1"));

        BooleanIndex loaded = BooleanIndexPersistence.loadWithAppends(file);

        assertThat(loaded.size()).isEqualTo(3);
        assertThat(loaded.get("batch1_doc1")).isEqualTo(new long[]{0x01L});
        assertThat(loaded.get("batch2_doc1")).isEqualTo(new long[]{0x02L});
        assertThat(loaded.get("batch3_doc1")).isEqualTo(new long[]{0x03L});
    }

    @Test
    void shouldSkipNonexistentIdsOnAppend() throws Exception {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        index.add("exists", new long[]{0x01L});

        Path file = tempDir.resolve("skip.bin");
        BooleanIndexPersistence.save(index, file);

        // Try to append non-existent ID
        BooleanIndexPersistence.append(index, file, List.of("missing"));

        BooleanIndex loaded = BooleanIndexPersistence.loadWithAppends(file);
        assertThat(loaded.size()).isEqualTo(1);
    }

    // --- File Size ---

    @Test
    void shouldReturnFileSize() throws Exception {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        index.add("doc", new long[]{0xFFL});

        Path file = tempDir.resolve("size.bin");
        BooleanIndexPersistence.save(index, file);

        assertThat(BooleanIndexPersistence.fileSize(file)).isGreaterThan(0);
    }

    @Test
    void shouldReturnMinusOneForMissingFile() {
        assertThat(BooleanIndexPersistence.fileSize(tempDir.resolve("missing.bin"))).isEqualTo(-1);
    }

    // --- Round-trip with search ---

    @Test
    void shouldPreserveSearchAfterPersistence() throws Exception {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        index.add("close", new long[]{0x01L});
        index.add("far", new long[]{0xFFL});

        Path file = tempDir.resolve("search.bin");
        BooleanIndexPersistence.save(index, file);

        BooleanIndex loaded = BooleanIndexPersistence.load(file);

        var results = loaded.search(new long[]{0x00L}, 1);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("close");
    }
}
