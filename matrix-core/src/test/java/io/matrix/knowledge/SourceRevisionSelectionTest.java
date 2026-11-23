package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceRevisionSelectionTest {

    @TempDir
    Path directory;

    @Test
    void migratesV21AndKeepsLocalSelectionHistoryAfterRestart() throws Exception {
        Path database = directory.resolve("source-revisions.sqlite");
        Path document = directory.resolve("sample.txt");
        Files.writeString(document, "The station has two temperature monitors.\n");
        SourceRevisionSelection first;
        SourceRevisionSelection second;
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            var importV1 = new io.matrix.knowledge.acquisition.DocumentImporter(store)
                    .importDocument("workspace-a", document, "en").source();
            first = new SourceRevisionSelection("event-v1", "workspace-a", "station",
                    importV1.sourceId(), importV1.contentHash(), null);
            Files.writeString(document, "The station has three temperature monitors.\n");
            var importV2 = new io.matrix.knowledge.acquisition.DocumentImporter(store)
                    .importDocument("workspace-a", document, "en").source();
            second = new SourceRevisionSelection("event-v2", "workspace-a", "station",
                    importV2.sourceId(), importV2.contentHash(), first.eventId());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement()) {
            statement.execute("DROP TABLE source_revision_selection");
            statement.execute("UPDATE schema_meta SET value = '21' WHERE key = 'version'");
        }
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.schemaVersion()).isEqualTo(22);
            assertThat(store.exportWorkspace("workspace-a").sources()).hasSize(2);
            assertThat(store.sourceRevisionSelections("workspace-a")).isEmpty();
            assertThat(store.selectSourceRevision(first)).isEqualTo(first);
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                    var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TRIGGER refuse_revision BEFORE INSERT ON source_revision_selection
                        WHEN NEW.event_id = 'event-v2'
                        BEGIN SELECT RAISE(ABORT, 'synthetic interruption'); END
                        """);
                assertThatThrownBy(() -> store.selectSourceRevision(second))
                        .isInstanceOf(IllegalStateException.class);
                assertThat(store.sourceRevisionSelections("workspace-a")).containsExactly(first);
                statement.execute("DROP TRIGGER refuse_revision");
            }
            assertThat(store.selectSourceRevision(second)).isEqualTo(second);
            assertThat(store.selectSourceRevision(first)).isEqualTo(first);
            assertThatThrownBy(() -> store.selectSourceRevision(new SourceRevisionSelection(
                    "fork", "workspace-a", "station", first.sourceId(), first.contentHash(), first.eventId())))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> store.selectSourceRevision(new SourceRevisionSelection(
                    "foreign", "workspace-b", "station", first.sourceId(), first.contentHash(), null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        try (KnowledgeStore reopened = new KnowledgeStore(database)) {
            assertThat(reopened.exportWorkspace("workspace-a").sourceRevisionSelections())
                    .containsExactly(first, second);
            assertThat(reopened.sourceRevisionSelections("workspace-b")).isEmpty();
            KnowledgeSnapshot archive = reopened.exportWorkspace("workspace-a");
            assertThat(archive.sources()).hasSize(2);
            KnowledgeSnapshot current = SourceRevisionView.project(archive, null);
            KnowledgeSnapshot history = SourceRevisionView.project(archive, first.eventId());
            assertThat(current.sources()).extracting(Source::id).containsExactly(second.sourceId());
            assertThat(history.sources()).extracting(Source::id).containsExactly(first.sourceId());
            assertThat(current.evidence()).isNotEmpty().allMatch(row -> row.sourceId().equals(second.sourceId()));
            assertThat(history.evidence()).isNotEmpty().allMatch(row -> row.sourceId().equals(first.sourceId()));
            assertThatThrownBy(() -> SourceRevisionView.project(
                    reopened.exportWorkspace("workspace-b"), first.eventId()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
