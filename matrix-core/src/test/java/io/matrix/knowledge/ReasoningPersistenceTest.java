package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.1 — what was checked is recorded.
 *
 * <p>RECON-W41 built the contradiction detector and the entailment checker
 * and wired neither to the store. The `contradiction` table was therefore
 * always empty, every stored derivation kept the `UNDECIDED` status it was
 * imported with, and `GET /contradictions` always answered `[]`.
 *
 * <p>These tests are the two features' first production callers.
 */
class ReasoningPersistenceTest {

    @Test
    @DisplayName("reasoning records the conflicts it finds")
    void reasoningRecordsTheConflictsItFinds(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("logic");

            // Before any reasoning, the table is empty - as it was in every
            // production run of W41.
            assertThat(workbench.contradictions("logic")).isEmpty();

            workbench.reason("logic", "Does q follow from p and p implies q?",
                    AbstractionLevel.INTRODUCTORY);

            List<Contradiction> conflicts = workbench.contradictions("logic");
            assertThat(conflicts).isNotEmpty();
            assertThat(conflicts).allSatisfy(conflict -> {
                assertThat(conflict.claimAId()).isNotBlank();
                assertThat(conflict.claimBId()).isNotBlank();
                assertThat(conflict.claimAId()).isNotEqualTo(conflict.claimBId());
                assertThat(conflict.kind()).isNotNull();
                assertThat(conflict.detectedBy()).isNotBlank();
            });
        }
    }

    @Test
    @DisplayName("reasoning records the verdict it reached on each rule")
    void reasoningRecordsTheVerdictItReachedOnEachRule(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("logic");

            // The imported rules carry UNDECIDED: nobody had checked them.
            assertThat(workbench.derivations("logic"))
                    .allSatisfy(derivation ->
                            assertThat(derivation.status()).isEqualTo(DerivationStatus.UNDECIDED));

            workbench.reason("logic", "Does q follow from p and p implies q?",
                    AbstractionLevel.INTRODUCTORY);

            List<Derivation> recorded = workbench.derivations("logic");
            assertThat(recorded).isNotEmpty();
            // The checked rules now carry a verdict that is not UNDECIDED.
            assertThat(recorded).anySatisfy(derivation ->
                    assertThat(derivation.status()).isEqualTo(DerivationStatus.DERIVED_VALID));
        }
    }

    @Test
    @DisplayName("the recorded verdict survives a restart")
    void theRecordedVerdictSurvivesARestart(@TempDir Path dir) {
        Path db = dir.resolve("w.sqlite");

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            workbench.loadBundledPackage("logic");
            workbench.reason("logic", "Does q follow from p and p implies q?",
                    AbstractionLevel.INTRODUCTORY);
        }

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            assertThat(workbench.contradictions("logic")).isNotEmpty();
            assertThat(workbench.derivations("logic")).anySatisfy(derivation ->
                    assertThat(derivation.status()).isEqualTo(DerivationStatus.DERIVED_VALID));
        }
    }

    @Test
    @DisplayName("the recorded conflict names both sides and prefers neither")
    void theRecordedConflictNamesBothSidesAndPrefersNeither(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("logic");
            workbench.reason("logic", "Is p true or false?", AbstractionLevel.INTRODUCTORY);

            List<Contradiction> conflicts = workbench.contradictions("logic");
            assertThat(conflicts).isNotEmpty();
            for (Contradiction conflict : conflicts) {
                // The record names the two claims and nothing else. There is
                // no field for "how many sources agree with this side",
                // because a count of agreeing records is not a truth
                // mechanism.
                assertThat(conflict.kind()).isEqualTo(Contradiction.Kind.OPPOSITE_POLARITY);
            }
        }
    }
}
