package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W41 — what the store is asked to keep, it keeps.
 *
 * <p>This class exists because of a defect. The provenance fields were added
 * to the claim and to the reader, and the write paths were never updated, so
 * every claim's extraction method and validation method was silently NULL
 * after a write - while the commit that introduced them announced that the
 * store now kept the provenance. The test that would have caught it did not
 * exist; the counts still matched, so the suite was green.
 *
 * <p>So the rule of this class: <b>a round trip must preserve every field</b>.
 * Nothing is checked by count alone.
 */
class KnowledgeProvenanceTest {

    @Test
    @DisplayName("the provenance of every claim survives an import and a read")
    void theProvenanceOfEveryClaimSurvivesAnImportAndARead(@TempDir Path dir) {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            KnowledgeSnapshot imported =
                    new KnowledgeImporter().importResource("knowledge/photosynthesis.pack.json");
            store.importWorkspace(imported);

            KnowledgeSnapshot read = store.exportWorkspace("photosynthesis");

            assertThat(read.claims()).hasSize(17);
            assertThat(read.sources()).hasSize(4);

            for (Claim claim : read.claims()) {
                assertThat(claim.extractionMethod())
                        .as("the extraction method of %s", claim.proposition())
                        .isNotBlank();
                assertThat(claim.validationMethod())
                        .as("the validation method of %s", claim.proposition())
                        .isNotBlank();
                assertThat(claim.warrant())
                        .as("the warrant of %s", claim.proposition())
                        .isNotEqualTo(ClaimWarrant.NONE);
            }

            for (Source source : read.sources()) {
                assertThat(source.contentHash())
                        .as("the content hash of %s", source.title())
                        .isNotBlank();
                assertThat(source.publishedAt())
                        .as("the publication date of %s", source.title())
                        .isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("the epistemic axes survive an import and a read")
    void theEpistemicAxesSurviveAnImportAndARead(@TempDir Path dir) {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            store.importWorkspace(
                    new KnowledgeImporter().importResource("knowledge/photosynthesis.pack.json"));

            List<Claim> claims = store.claims("photosynthesis");

            assertThat(claims).allSatisfy(claim ->
                    assertThat(claim.derivationStatus()).isEqualTo(DerivationStatus.NOT_DERIVED));
            // The encyclopedia sources establish their claims by testimony,
            // not by perception and not by inference.
            assertThat(claims).allSatisfy(claim ->
                    assertThat(claim.warrant()).isIn(ClaimWarrant.TESTIMONY, ClaimWarrant.OBSERVED));
        }
    }

    @Test
    @DisplayName("a derivation is recorded, read back, and keeps its premise order")
    void aDerivationIsRecordedAndReadBack(@TempDir Path dir) {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            KnowledgeSnapshot imported =
                    new KnowledgeImporter().importResource("knowledge/photosynthesis.pack.json");
            store.importWorkspace(imported);

            List<Claim> claims = store.claims("photosynthesis");
            String first = claims.get(0).id();
            String second = claims.get(1).id();
            String third = claims.get(2).id();

            Derivation derivation = new Derivation("der-1", "photosynthesis", "rule-mp",
                    "modus ponens", Derivation.Connective.AND, third,
                    List.of(first, second), DerivationStatus.DERIVED_VALID,
                    "2026-11-01T00:00:00Z");
            store.recordDerivation(derivation);

            List<Derivation> read = store.derivations("photosynthesis");

            assertThat(read).hasSize(1);
            assertThat(read.get(0).premiseClaimIds()).containsExactly(first, second);
            assertThat(read.get(0).connective()).isEqualTo(Derivation.Connective.AND);
            assertThat(read.get(0).status()).isEqualTo(DerivationStatus.DERIVED_VALID);
        }
    }

    @Test
    @DisplayName("a derivation over claims the workspace does not hold is refused")
    void aDerivationOverClaimsTheWorkspaceDoesNotHoldIsRefused(@TempDir Path dir) {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            store.importWorkspace(
                    new KnowledgeImporter().importResource("knowledge/photosynthesis.pack.json"));
            List<Claim> claims = store.claims("photosynthesis");

            Derivation orphan = new Derivation("der-orphan", "photosynthesis", "rule",
                    "rule", Derivation.Connective.AND, "cl-not-here",
                    List.of(claims.get(0).id()), DerivationStatus.DERIVED_VALID,
                    "2026-11-01T00:00:00Z");

            assertThatThrownBy(() -> store.recordDerivation(orphan))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a claim of this workspace");
        }
    }

    @Test
    @DisplayName("a one-premise connective with several premises is refused")
    void aOnePremiseConnectiveWithSeveralPremisesIsRefused(@TempDir Path dir) {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            store.importWorkspace(
                    new KnowledgeImporter().importResource("knowledge/photosynthesis.pack.json"));
            List<Claim> claims = store.claims("photosynthesis");

            Derivation ambiguous = new Derivation("der-ambiguous", "photosynthesis", "rule",
                    "rule", Derivation.Connective.IMPLIES, claims.get(2).id(),
                    List.of(claims.get(0).id(), claims.get(1).id()),
                    DerivationStatus.DERIVED_VALID, "2026-11-01T00:00:00Z");

            assertThatThrownBy(() -> store.recordDerivation(ambiguous))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exactly one");
        }
    }
}
