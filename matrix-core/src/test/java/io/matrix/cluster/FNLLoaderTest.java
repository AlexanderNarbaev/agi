package io.matrix.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.matrix.snapshot.ClusterSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FNLLoaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @TempDir
    Path tempDir;

    private Path writeSnapshotFile(String name, int neuronCount) throws Exception {
        ClusterSnapshot snapshot = new ClusterSnapshot(
                UUID.randomUUID().toString(),
                "test-instance",
                System.currentTimeMillis(),
                0L,
                neuronCount,
                List.of());
        Path file = tempDir.resolve(name + ".ldn");
        MAPPER.writeValue(file.toFile(), snapshot);
        return file;
    }

    @Test
    void loadShouldRegisterFNLFromLdnFile() throws Exception {
        var loader = new FNLLoader();
        Path ldn = writeSnapshotFile("vision", 42);

        FNLMetadata metadata = loader.load(ldn);

        assertThat(metadata.name()).isEqualTo("vision");
        assertThat(metadata.neuronCount()).isEqualTo(42);
        assertThat(metadata.fnlId()).isNotNull();
        assertThat(loader.isLoaded(metadata.fnlId())).isTrue();
    }

    @Test
    void unloadShouldRemoveFNL() throws Exception {
        var loader = new FNLLoader();
        Path ldn = writeSnapshotFile("motor", 10);
        FNLMetadata metadata = loader.load(ldn);

        loader.unload(metadata.fnlId());

        assertThat(loader.isLoaded(metadata.fnlId())).isFalse();
        assertThat(loader.listLoaded()).isEmpty();
    }

    @Test
    void listLoadedShouldReturnAllLoadedFNLs() throws Exception {
        var loader = new FNLLoader();
        FNLMetadata m1 = loader.load(writeSnapshotFile("vision", 5));
        FNLMetadata m2 = loader.load(writeSnapshotFile("motor", 8));

        List<FNLMetadata> loaded = loader.listLoaded();

        assertThat(loaded).hasSize(2);
        assertThat(loaded).extracting(FNLMetadata::name)
                .containsExactlyInAnyOrder("vision", "motor");
    }

    @Test
    void jitLoadShouldCacheOnFirstAccess() {
        var loader = new FNLLoader(tempDir);

        FNLMetadata first = loader.jitLoad("language");
        FNLMetadata second = loader.jitLoad("language");

        assertThat(first).isEqualTo(second);
        assertThat(loader.listLoaded()).hasSize(1);
    }

    @Test
    void jitLoadShouldLoadFromFileWhenAvailable() throws Exception {
        writeSnapshotFile("vision", 15);
        var loader = new FNLLoader(tempDir);

        FNLMetadata metadata = loader.jitLoad("vision");

        assertThat(metadata.name()).isEqualTo("vision");
        assertThat(metadata.neuronCount()).isEqualTo(15);
        assertThat(loader.isLoaded(metadata.fnlId())).isTrue();
    }

    @Test
    void jitLoadShouldCreateSyntheticWhenFileNotFound() {
        var loader = new FNLLoader(tempDir);

        FNLMetadata metadata = loader.jitLoad("nonexistent");

        assertThat(metadata.name()).isEqualTo("nonexistent");
        assertThat(metadata.fnlId()).isNotNull();
        assertThat(loader.isLoaded(metadata.fnlId())).isTrue();
    }

    @Test
    void unloadShouldAlsoClearJitCache() {
        var loader = new FNLLoader();
        FNLMetadata metadata = loader.jitLoad("test_fnl");

        loader.unload(metadata.fnlId());

        FNLMetadata reloaded = loader.jitLoad("test_fnl");
        assertThat(reloaded.fnlId()).isNotEqualTo(metadata.fnlId());
    }

    @Test
    void loadUnloadRoundtripShouldLeaveEmptyState() throws Exception {
        var loader = new FNLLoader();
        Path ldn = writeSnapshotFile("roundtrip", 3);
        FNLMetadata metadata = loader.load(ldn);

        assertThat(loader.listLoaded()).hasSize(1);

        loader.unload(metadata.fnlId());

        assertThat(loader.listLoaded()).isEmpty();
    }
}
