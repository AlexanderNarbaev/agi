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
 * RECON-W42.6 — the schema of an EXISTING database is migrated.
 *
 * <p>The tombstone columns of the dialogue memory were added to the v2
 * block of the migration, where they only ever ran on a database that had
 * never had the asked-question table. On an existing database the columns
 * were never added, and the consolidation endpoint answered with "no such
 * column: forgotten_at" — the migration belongs to the version it belongs
 * to.
 *
 * <p>The test writes a v9 database by hand and opens it with the store.
 */
class SchemaMigrationTest {

    @Test
    @DisplayName("an existing v9 database gets the tombstone columns")
    void anExistingV9DatabaseGetsTheTombstoneColumns(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("w.sqlite");
        writeV9Database(db);

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            assertThat(store.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
            // The columns are there, and the query that reads them works.
            assertThat(store.askedQuestions("photosynthesis")).isEmpty();
        }
    }

    @Test
    @DisplayName("the migration is idempotent: opening it twice changes nothing")
    void theMigrationIsIdempotentOpeningItTwiceChangesNothing(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("w.sqlite");
        writeV9Database(db);

        try (KnowledgeStore first = new KnowledgeStore(db)) {
            assertThat(first.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
        }
        try (KnowledgeStore second = new KnowledgeStore(db)) {
            assertThat(second.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
            assertThat(second.askedQuestions("photosynthesis")).isEmpty();
        }
    }

    @Test
    @DisplayName("the consolidation works on a database that was migrated")
    void theConsolidationWorksOnADatabaseThatWasMigrated(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("w.sqlite");
        writeV9Database(db);

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.ask("photosynthesis", "What is quantum chromodynamics?",
                    AbstractionLevel.INTRODUCTORY);

            // The instant of the run is supplied by the caller - and it is
            // a minute ahead of the record, because a record asked in the
            // same millisecond is not old enough for any rule to name it.
            // The first version of this test used the current instant, and
            // it passed or failed depending on how fast the machine was.
            KnowledgeConsolidator.Report report = KnowledgeConsolidator.consolidate(workbench,
                    "photosynthesis", System.currentTimeMillis() + 60_000,
                    new KnowledgeConsolidator.Budget(10, 0));

            assertThat(report.examined()).isGreaterThan(0);
            assertThat(workbench.forgottenQuestions("photosynthesis")).isNotEmpty();
        }
    }

    /**
     * Writes a database with the v9 schema.
     *
     * <p>The database is first created by the store, so that it holds the
     * whole schema and not just the asked-question table; the tombstone
     * columns are then dropped and the version is put back to 9. That is
     * what an existing database looks like to the migration, and it is
     * the case the first version of the migration got wrong.
     */
    private void writeV9Database(Path db) throws Exception {
        try (KnowledgeStore store = new KnowledgeStore(db)) {
            assertThat(store.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE asked_question DROP COLUMN forgotten_at");
            st.execute("ALTER TABLE asked_question DROP COLUMN forgotten_kind");
            st.execute("ALTER TABLE asked_question DROP COLUMN forgotten_reason");
            st.execute("UPDATE schema_meta SET value = '9' WHERE key = 'version'");
        }
    }
}
