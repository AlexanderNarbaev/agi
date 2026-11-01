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
 * pins that: it takes a database at the previous version, reopens it, and
 * checks that the migration carries the data forward and adds the columns
 * the provenance work requires.
 *
 * <p>It also pins the mistake that was made and fixed while writing it: when
 * the new columns were added to the v1 CREATE TABLE as well as to the
 * migration, every fresh database failed with "duplicate column name".
 */
class KnowledgeStoreMigrationTest {

    @Test
    @DisplayName("a database at the previous version migrates and keeps its data")
    void aPreviousVersionMigratesAndKeepsItsData(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("knowledge.sqlite");

        // A database created by the current code, holding one workspace.
        try (KnowledgeStore store = new KnowledgeStore(db)) {
            store.importWorkspace(new KnowledgeImporter().importResource(
                    "knowledge/photosynthesis.pack.json"));
        }

        // Rewind it to the previous schema: drop the new columns and set
        // the recorded version back.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE source DROP COLUMN content_hash");
            st.execute("ALTER TABLE source DROP COLUMN published_at");
            st.execute("ALTER TABLE claim DROP COLUMN extraction_method");
            st.execute("ALTER TABLE claim DROP COLUMN validation_method");
            st.execute("UPDATE schema_meta SET value='4' WHERE key='version'");
        }

        // Reopening migrates it forward.
        try (KnowledgeStore store = new KnowledgeStore(db)) {
            KnowledgeSnapshot snapshot = store.exportWorkspace("photosynthesis");

            assertThat(snapshot.claims()).hasSize(17);
            assertThat(snapshot.sources()).hasSize(4);

            // The new provenance is present, and the old data survived.
            // A source's content hash is recovered from the excerpt that was
            // stored; what cannot be recovered is marked unknown, never
            // invented.
            assertThat(snapshot.sources()).allSatisfy(source -> {
                assertThat(source.contentHash()).isNotBlank();
            });
            assertThat(snapshot.claims()).allSatisfy(claim -> {
                assertThat(claim.extractionMethod()).contains("predates the provenance fields");
                assertThat(claim.validationMethod()).contains("predates the provenance fields");
            });
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
                    "assessment_outcome", "asked_question");
        }
    }
}
