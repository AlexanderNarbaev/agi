package io.matrix.knowledge.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.KnowledgeWorkbench;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the validation stage of the acquisition.
 *
 * <p>The mission's §5.5: "Ten documents copying the same source do not
 * constitute ten independent confirmations." The validator measures a
 * group of claims by the DISTINCT content hashes of the sources behind it,
 * and it promotes only a group that has at least two.
 */
class ClaimValidatorTest {

    @Test
    @DisplayName("a document that states what the package states promotes the claim")
    void aDocumentThatStatesWhatThePackageStatesPromotesTheClaim(@TempDir Path dir)
            throws Exception {
        Path file = dir.resolve("textbook.txt");
        Files.writeString(file, """
                Oxygenic photosynthesis releases oxygen as a byproduct of splitting water.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            // The validation is a STAGE OF THE IMPORT since W42.7, so the
            // import itself promotes the claim. The explicit call below
            // then finds the work already done and must report nothing -
            // that is the idempotence.
            DocumentImporter.ImportResult imported =
                    workbench.importDocument("photosynthesis", file, null);

            assertThat(imported.validation().promoted()).isGreaterThan(0);
            assertThat(imported.validation().superseded()).isGreaterThan(0);
            assertThat(imported.validation().refusals()).isEmpty();

            ClaimValidator.Result result = ClaimValidator.validate(workbench, "photosynthesis");
            assertThat(result.promoted()).isEqualTo(0);
            assertThat(result.refusals()).isEmpty();

            // The canonical claim is VERIFIED and carries the evidence of
            // both sources.
            ClaimValidator.Promotion promotion = imported.validation().promotions().get(0);
            assertThat(promotion.to()).isEqualTo(ClaimStatus.VERIFIED);
            assertThat(promotion.independentSources()).isEqualTo(2);

            Claim canonical = workbench.workspace("photosynthesis").claims().stream()
                    .filter(claim -> claim.id().equals(promotion.claimId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(canonical.status()).isEqualTo(ClaimStatus.VERIFIED);
            assertThat(workbench.workspace("photosynthesis").evidence().stream()
                    .filter(evidence -> evidence.claimId().equals(canonical.id())).count())
                    .isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("the same document twice is one source, and nothing is promoted")
    void theSameDocumentTwiceIsOneSourceAndNothingIsPromoted(@TempDir Path dir) throws Exception {
        // A proposition the package does NOT state: the two copies are the
        // only sources, so there is nothing to confirm.
        Path file = dir.resolve("notes.txt");
        Files.writeString(file, """
                Some laboratory reports a peculiar property of the chloroplast envelope.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.importDocument("photosynthesis", file, null);
            workbench.importDocument("photosynthesis", file, null);

            ClaimValidator.Result result = ClaimValidator.validate(workbench, "photosynthesis");

            // The content hash is the same, so the two copies are ONE
            // source: "ten documents copying the same source do not
            // constitute ten independent confirmations".
            assertThat(result.promoted()).isEqualTo(0);
            assertThat(result.superseded()).isEqualTo(0);
            assertThat(result.changedAnything()).isFalse();
        }
    }

    @Test
    @DisplayName("a claim that only one source states is not promoted")
    void aClaimThatOnlyOneSourceStatesIsNotPromoted(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("notes.txt");
        Files.writeString(file, """
                Some laboratory reports a peculiar property of the chloroplast envelope.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.importDocument("photosynthesis", file, null);

            ClaimValidator.Result result = ClaimValidator.validate(workbench, "photosynthesis");

            assertThat(result.promoted()).isEqualTo(0);
            assertThat(result.superseded()).isEqualTo(0);
            assertThat(result.changedAnything()).isFalse();
        }
    }

    @Test
    @DisplayName("the promotion does not invent a confidence")
    void thePromotionDoesNotInventAConfidence(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("textbook.txt");
        Files.writeString(file, """
                Oxygenic photosynthesis releases oxygen as a byproduct of splitting water.
                """, StandardCharsets.UTF_8);

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            DocumentImporter.ImportResult imported =
                    workbench.importDocument("photosynthesis", file, null);

            Claim canonical = workbench.workspace("photosynthesis").claims().stream()
                    .filter(claim -> claim.id().equals(
                            imported.validation().promotions().get(0).claimId()))
                    .findFirst()
                    .orElseThrow();

            // The confidence is an authored field. The system has no
            // calibrated way to compute one, and the mission's §16 forbids
            // presenting a heuristic as a probability without calibration.
            // The promoted claim keeps the confidence it was authored with.
            assertThat(canonical.confidence()).isBetween(0.0, 1.0);
            assertThat(canonical.confidence()).isGreaterThan(0.0);
        }
    }

    @Test
    @DisplayName("the validation survives a restart")
    void theValidationSurvivesARestart(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("w.sqlite");
        Path file = dir.resolve("textbook.txt");
        Files.writeString(file, """
                Oxygenic photosynthesis releases oxygen as a byproduct of splitting water.
                """, StandardCharsets.UTF_8);

        String promotedId;
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            workbench.loadBundledPackage("photosynthesis");
            DocumentImporter.ImportResult imported =
                    workbench.importDocument("photosynthesis", file, null);
            promotedId = imported.validation().promotions().get(0).claimId();
        }

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            List<Claim> claims = workbench.workspace("photosynthesis").claims();
            assertThat(claims).anySatisfy(claim -> {
                assertThat(claim.id()).isEqualTo(promotedId);
                assertThat(claim.status()).isEqualTo(ClaimStatus.VERIFIED);
            });
        }
    }
}
