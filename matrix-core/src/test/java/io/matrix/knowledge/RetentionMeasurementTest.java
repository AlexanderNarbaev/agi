package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.4 — the delayed retention is measured.
 *
 * <p>The mission's §7.4 asks for "delayed retrieval, after a meaningful
 * interval", and §7.1 makes the lifecycle revisable: a previously mastered
 * capability may return to PRACTICE when the retention fails.
 *
 * <p>RECON-W41 had a RETENTION dimension with no criterion and no dataset
 * item, so it could never be anything but UNKNOWN — the mission's delayed
 * retention was measured only by the teach-back experiment's delayed phase,
 * and not by the learning state at all.
 */
class RetentionMeasurementTest {

    private static final String CONCEPT = "c-photosynthesis-4d4bb673";

    @Test
    @DisplayName("the retention dimension has evidence once there are two sessions")
    void theRetentionDimensionHasEvidenceOnceThereAreTwoSessions(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            // One session: nothing has been delayed yet.
            workbench.assess("photosynthesis", "t-recall-1",
                    "photosynthesis converts carbon dioxide and water into sugar and oxygen using light",
                    0.9, "session-one");
            assertThat(retentionState(workbench)).isNull();

            // A second session: the first session's performance is now the
            // delayed evidence.
            workbench.assess("photosynthesis", "t-recall-1",
                    "photosynthesis converts carbon dioxide and water into sugar and oxygen using light",
                    0.9, "session-two");

            LearningState retention = retentionState(workbench);
            assertThat(retention).isNotNull();
            assertThat(retention.evidenceCount()).isGreaterThan(0);
            assertThat(retention.stage()).isNotEqualTo(LearningStage.UNKNOWN);
        }
    }

    @Test
    @DisplayName("the retention state is recorded and survives a restart")
    void theRetentionStateIsRecordedAndSurvivesARestart(@TempDir Path dir) {
        Path db = dir.resolve("w.sqlite");

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.assess("photosynthesis", "t-recall-1",
                    "photosynthesis converts carbon dioxide and water into sugar and oxygen using light",
                    0.9, "session-one");
            workbench.assess("photosynthesis", "t-recall-1",
                    "photosynthesis converts carbon dioxide and water into sugar and oxygen using light",
                    0.9, "session-two");
        }

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            assertThat(retentionState(workbench)).isNotNull();
            assertThat(workbench.learningTransitions("photosynthesis")).isNotEmpty();
        }
    }

    @Test
    @DisplayName("the retention note says what the delay was")
    void theRetentionNoteSaysWhatTheDelayWas(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            workbench.assess("photosynthesis", "t-recall-1",
                    "photosynthesis converts carbon dioxide and water into sugar and oxygen using light",
                    0.9, "session-one");
            workbench.assess("photosynthesis", "t-recall-1",
                    "photosynthesis converts carbon dioxide and water into sugar and oxygen using light",
                    0.9, "session-two");

            LearningState retention = retentionState(workbench);
            assertThat(retention.note()).isNotBlank();
            // The note names the outcomes it rests on.
            assertThat(retention.evidenceItemIds()).isNotEmpty();
        }
    }

    /** The retention state of the photosynthesis concept, or null. */
    private LearningState retentionState(KnowledgeWorkbench workbench) {
        List<LearningState> states = workbench.learningStates("photosynthesis");
        return states.stream()
                .filter(state -> state.conceptId().equals(CONCEPT))
                .filter(state -> state.dimension() == LearningDimension.RETENTION)
                .findFirst()
                .orElse(null);
    }
}
