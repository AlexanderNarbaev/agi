package io.matrix.privacy.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TombstoneStorageFactoryTest {

    @Test
    void emptyBackendNameFallsBackToMemory() {
        TombstoneStorage s = TombstoneStorageFactory.build("", TombstoneStorageFactory.StorageContext.empty());
        assertThat(s.backendId()).isEqualTo("memory");
    }

    @Test
    void nullBackendNameFallsBackToMemory() {
        TombstoneStorage s = TombstoneStorageFactory.build(null, TombstoneStorageFactory.StorageContext.empty());
        assertThat(s.backendId()).isEqualTo("memory");
    }

    @Test
    void memoryBackendCanBeBuilt() {
        assertThat(TombstoneStorageFactory.build("memory", TombstoneStorageFactory.StorageContext.empty()).backendId())
                .isEqualTo("memory");
    }

    @Test
    void inMemoryAliasWorks() {
        assertThat(TombstoneStorageFactory.build("in-memory", TombstoneStorageFactory.StorageContext.empty()).backendId())
                .isEqualTo("memory");
        assertThat(TombstoneStorageFactory.build("inmemory", TombstoneStorageFactory.StorageContext.empty()).backendId())
                .isEqualTo("memory");
    }

    @Test
    void postgresWithoutDataSourceThrows() {
        assertThatThrownBy(() -> TombstoneStorageFactory.build("postgres",
                TombstoneStorageFactory.StorageContext.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DataSource");
    }

    @Test
    void s3WithoutEndpointThrows() {
        assertThatThrownBy(() -> TombstoneStorageFactory.build("s3",
                TombstoneStorageFactory.StorageContext.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void unknownBackendThrows() {
        assertThatThrownBy(() -> TombstoneStorageFactory.build("redis",
                TombstoneStorageFactory.StorageContext.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown backend");
    }

    @Test
    void compositeBuildsMultipleBackends() {
        TombstoneStorage s = TombstoneStorageFactory.buildComposite(
                "memory, memory", TombstoneStorageFactory.StorageContext.empty());
        assertThat(s.backendId()).isEqualTo("composite(memory+memory)");
        assertThat(s).isInstanceOf(CompositeTombstoneStorage.class);
    }

    @Test
    void compositeWithSingleBackendDelegatesCleanly() {
        TombstoneStorage s = TombstoneStorageFactory.buildComposite(
                "memory", TombstoneStorageFactory.StorageContext.empty());
        assertThat(s.backendId()).isEqualTo("composite(memory)");
    }

    @Test
    void compositeTrimsWhitespace() {
        TombstoneStorage s = TombstoneStorageFactory.buildComposite(
                "  memory  ,  memory  ", TombstoneStorageFactory.StorageContext.empty());
        assertThat(s.backendId()).isEqualTo("composite(memory+memory)");
    }

    @Test
    void autoBuildDefaultsToMemory() {
        // No system properties set → defaults to memory.
        TombstoneStorage s = TombstoneStorageFactory.autoBuild(TombstoneStorageFactory.StorageContext.empty());
        assertThat(s.backendId()).isEqualTo("memory");
    }

    @Test
    void autoBuildRespectsSystemProperty() {
        try {
            System.setProperty("matrix.tombstone.backend", "memory");
            TombstoneStorage s = TombstoneStorageFactory.autoBuild(TombstoneStorageFactory.StorageContext.empty());
            assertThat(s.backendId()).isEqualTo("memory");
        } finally {
            System.clearProperty("matrix.tombstone.backend");
        }
    }

    @Test
    void autoBuildRespectsComposite() {
        try {
            System.setProperty("matrix.tombstone.backend", "composite");
            System.setProperty("matrix.tombstone.composite.backends", "memory");
            TombstoneStorage s = TombstoneStorageFactory.autoBuild(TombstoneStorageFactory.StorageContext.empty());
            assertThat(s.backendId()).isEqualTo("composite(memory)");
        } finally {
            System.clearProperty("matrix.tombstone.backend");
            System.clearProperty("matrix.tombstone.composite.backends");
        }
    }
}
