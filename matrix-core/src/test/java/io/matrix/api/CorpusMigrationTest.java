package io.matrix.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CorpusMigration} — RUN 25 schema migration.
 */
class CorpusMigrationTest {

    @TempDir
    Path tmpDir;

    private Path input;
    private Path output;
    private Path logFile;
    private CorpusMigration migration;

    @BeforeEach
    void setUp() throws Exception {
        input = tmpDir.resolve("qa_pairs.json");
        output = tmpDir.resolve("qa_pairs_v2.json");
        logFile = tmpDir.resolve("migrations.log");
        // Bare array of 3 records (v1 format).
        Files.writeString(input, """
                [
                  {"question": "Q1", "answer": "A1", "category": "cat1", "source": "src1"},
                  {"question": "Q2", "answer": "A2", "category": "cat2"},
                  {"question": "Q3", "answer": "A3"}
                ]
                """);
        migration = new CorpusMigration(logFile);
    }

    @Test
    void detectVersionReturnsOneForBareArray() {
        assertThat(migration.detectVersion(input)).isEqualTo(CorpusMigration.LEGACY_VERSION);
    }

    @Test
    void detectVersionReturnsTwoForEnvelope() throws Exception {
        Files.writeString(output, """
                {"version": 2, "migratedAt": "2026-09-05", "pairs": []}
                """);
        assertThat(migration.detectVersion(output)).isEqualTo(2);
    }

    @Test
    void migrateWrapsInEnvelope() throws Exception {
        var result = migration.migrate(input, output);
        assertThat(result.fromVersion()).isEqualTo(CorpusMigration.LEGACY_VERSION);
        assertThat(result.toVersion()).isEqualTo(CorpusMigration.CURRENT_VERSION);
        assertThat(result.sourceCount()).isEqualTo(3);
        assertThat(result.migratedCount()).isEqualTo(3);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.isComplete()).isTrue();

        // Verify envelope structure
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(output.toFile());
        assertThat(root.get("version").asInt()).isEqualTo(2);
        assertThat(root.get("migratedCount").asInt()).isEqualTo(3);
        assertThat(root.get("pairs").size()).isEqualTo(3);

        // Verify each record has an id and the original fields
        for (JsonNode p : root.get("pairs")) {
            assertThat(p.has("id")).isTrue();
            assertThat(p.get("id").asLong()).isGreaterThan(0);
            assertThat(p.has("question")).isTrue();
            assertThat(p.has("answer")).isTrue();
            assertThat(p.has("category")).isTrue();
        }
    }

    @Test
    void migrateSkipsBlankEntries() throws Exception {
        Files.writeString(input, """
                [
                  {"question": "Q1", "answer": "A1"},
                  {"question": "", "answer": "A2"},
                  {"question": "Q3", "answer": ""}
                ]
                """);
        var result = migration.migrate(input, output);
        assertThat(result.sourceCount()).isEqualTo(3);
        assertThat(result.migratedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(2);
        assertThat(result.isComplete()).isTrue();
    }

    @Test
    void migrateAppendsLog() throws Exception {
        migration.migrate(input, output);
        assertThat(migration.migrationCount()).isEqualTo(1);
        // Run again with different output to verify log append
        migration.migrate(input, tmpDir.resolve("qa_v2_again.json"));
        assertThat(migration.migrationCount()).isEqualTo(2);
    }

    @Test
    void migrateIsDeterministic() throws Exception {
        var r1 = migration.migrate(input, tmpDir.resolve("v1.json"));
        var r2 = migration.migrate(input, tmpDir.resolve("v2.json"));
        // source counts are deterministic
        assertThat(r1.sourceCount()).isEqualTo(r2.sourceCount());
        assertThat(r1.migratedCount()).isEqualTo(r2.migratedCount());
    }
}
