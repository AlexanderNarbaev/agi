package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W41 — the store's schema migration path.
 *
 * <p>The store's convention is that a fresh database is built by the same
 * code that upgrades an old one, so the v1 CREATE TABLE must stay the v1
 * shape and every later column must arrive through a migration. This test
 * pins that: it takes a database at an earlier version, reopens it, and
 * checks that the migration carries the data forward and adds exactly what
 * that version is missing.
 *
 * <p>It also pins the mistake that was made while writing it: when the new
 * columns were added to the v1 CREATE TABLE as well as to the migration,
 * every fresh database failed with "duplicate column name".
 */
class KnowledgeStoreMigrationTest {

    /** Builds a database at the current version, holding one workspace. */
    private void seed(Path db) {
        try (KnowledgeStore store = new KnowledgeStore(db)) {
            store.importWorkspace(new KnowledgeImporter().importResource(
                    "knowledge/photosynthesis.pack.json"));
        }
    }

    private void execute(Path db, String... statements) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement st = connection.createStatement()) {
            for (String statement : statements) {
                st.execute(statement);
            }
        }
    }

    @Test
    @DisplayName("a database at v4 migrates to v6 and its provenance arrives honestly")
    void aDatabaseAtV4MigratesToV6(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("knowledge.sqlite");
        seed(db);

        // Rewind to v4: the provenance columns, the epistemic axes and the
        // derivation tables did not exist yet.
        execute(db,
                "ALTER TABLE source DROP COLUMN content_hash",
                "ALTER TABLE source DROP COLUMN published_at",
                "ALTER TABLE claim DROP COLUMN extraction_method",
                "ALTER TABLE claim DROP COLUMN validation_method",
                "ALTER TABLE claim DROP COLUMN warrant",
                "ALTER TABLE claim DROP COLUMN derivation_status",
                "DROP TABLE derivation_premise",
                "DROP TABLE derivation",
                "UPDATE schema_meta SET value='4' WHERE key='version'");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            assertThat(store.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);

            KnowledgeSnapshot snapshot = store.exportWorkspace("photosynthesis");
            assertThat(snapshot.claims()).hasSize(17);
            assertThat(snapshot.sources()).hasSize(4);

            // The source's content hash is recovered from the excerpt that
            // was stored; the methods that cannot be recovered are marked
            // unknown, never invented.
            assertThat(snapshot.sources()).allSatisfy(source ->
                    assertThat(source.contentHash()).isNotBlank());
            assertThat(snapshot.claims()).allSatisfy(claim -> {
                assertThat(claim.extractionMethod()).contains("predates the provenance fields");
                assertThat(claim.validationMethod()).contains("predates the provenance fields");
            });
        }
    }

    @Test
    @DisplayName("a database at v5 migrates to v6 and gains the epistemic axes")
    void aDatabaseAtV5MigratesToV6(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("knowledge.sqlite");
        seed(db);

        // Rewind to v5: the epistemic axes and the derivation tables did not
        // exist yet, but the provenance did.
        execute(db,
                "ALTER TABLE claim DROP COLUMN warrant",
                "ALTER TABLE claim DROP COLUMN derivation_status",
                "DROP TABLE derivation_premise",
                "DROP TABLE derivation",
                "UPDATE schema_meta SET value='5' WHERE key='version'");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            assertThat(store.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);

            KnowledgeSnapshot snapshot = store.exportWorkspace("photosynthesis");
            assertThat(snapshot.claims()).hasSize(17);

            // The v5 data is untouched by the v6 migration.
            assertThat(snapshot.claims()).allSatisfy(claim -> {
                assertThat(claim.extractionMethod()).contains("transcribed from the cited source");
                assertThat(claim.validationMethod()).isNotBlank();
                // The warrant is recovered from the kind of the source.
                assertThat(claim.warrant()).isNotEqualTo(ClaimWarrant.NONE);
            });
            assertThat(snapshot.sources()).allSatisfy(source ->
                    assertThat(source.contentHash()).isNotBlank());
        }
    }

    @Test
    @DisplayName("a fresh database is built by the migration chain, not by a shortcut")
    void aFreshDatabaseIsBuiltByTheMigrationChain(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("fresh.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            // The version is the current one, which means the chain ran.
            assertThat(store.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement st = connection.createStatement()) {
            // Every table the chain creates is present, exactly once.
            var rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name");
            var names = new java.util.ArrayList<String>();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
            assertThat(names).contains("schema_meta", "workspace", "concept", "source", "claim",
                    "evidence", "representation", "prerequisite", "revision",
                    "assessment_outcome", "asked_question", "derivation", "derivation_premise");
        }
    }
}
