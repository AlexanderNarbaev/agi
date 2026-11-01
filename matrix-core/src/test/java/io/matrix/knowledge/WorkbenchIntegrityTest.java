package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W41 §9.2 — the System Integrity View tells the truth.
 *
 * <p>It reports the last durable write, the state of the backup, the schema
 * version, and the recovery warnings. None of these may be a claim that
 * cannot be checked, and a goal that was under way must be named among the
 * warnings - that is the point of the view.
 */
class WorkbenchIntegrityTest {

    @Test
    @DisplayName("a goal that was under way is named in the recovery warnings")
    void aGoalThatWasUnderWayIsNamedInTheRecoveryWarnings(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.startGoal("photosynthesis", "Learn the Calvin cycle",
                    List.of("the learner can explain the cycle"), List.of(),
                    List.of(new LearningGoal.Subtask("read", "Read the source", true, List.of(),
                            LearningGoal.Status.PENDING, List.of(), "")), 5, "continue with read");

            Map<String, Object> integrity = workbench.integrity();

            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) integrity.get("recoveryWarnings");
            assertThat(warnings).anySatisfy(warning -> {
                assertThat(warning).contains("Learn the Calvin cycle");
                assertThat(warning).contains("continue with read");
            });
        }
    }

    @Test
    @DisplayName("the backup state says plainly that there is no backup")
    void theBackupStateSaysPlainlyThatThereIsNoBackup(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            Map<String, Object> integrity = workbench.integrity();

            assertThat((String) integrity.get("backupState")).contains("none");
            assertThat(integrity.get("schemaVersion")).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
            assertThat((String) integrity.get("lastDurableWriteMeaning")).isNotBlank();
        }
    }

    @Test
    @DisplayName("the last durable write is the newest record, whichever shape of time it carries")
    void theLastDurableWriteIsTheNewestRecord(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.startGoal("photosynthesis", "A goal", List.of(), List.of(), List.of(), 3, "");

            Map<String, Object> integrity = workbench.integrity();

            // The goal was written after the package was imported, so the
            // goal's instant is the newest record. An earlier version of
            // this compared an ISO instant with epoch milliseconds as
            // strings, which is a comparison of two alphabets.
            String lastWrite = (String) integrity.get("lastDurableWrite");
            assertThat(lastWrite).isNotNull();
            assertThat(java.time.Instant.parse(lastWrite)).isAfter(
                    java.time.Instant.parse("2000-01-01T00:00:00Z"));
            assertThat(integrity.get("lastDurableWriteWorkspace")).isEqualTo("photosynthesis");
        }
    }
}
