package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.acquisition.DocumentImporter;
import io.matrix.knowledge.acquisition.SourceRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W51 §6 — the rules that decide what a claim may be cited for.
 *
 * <p>These tests pin the four rules the W50 evaluation measured as broken:
 * the status filter on the evidence, the separation of relevance from
 * epistemic support, the abstention boundary, and the immutability of a
 * source record.
 */
class EpistemicPolicyTest {

    /** A document whose every sentence is a claim. */
    private static final String LESSON = """
            Photosynthesis is the process by which green plants use light to convert carbon dioxide
            and water into sugar and oxygen.

            The light-dependent reactions happen in the thylakoid membrane. They produce ATP and NADPH.
            """;

    @Test
    @DisplayName("only an established claim may be cited as evidence")
    void onlyAnEstablishedClaimMayBeCited() {
        assertThat(EpistemicPolicy.CITABLE_STATUSES)
                .containsExactlyInAnyOrder(ClaimStatus.SUPPORTED, ClaimStatus.VERIFIED,
                        ClaimStatus.CONTESTED);
        for (ClaimStatus status : List.of(ClaimStatus.DRAFT, ClaimStatus.CANDIDATE,
                ClaimStatus.HYPOTHESIS, ClaimStatus.OBSERVED, ClaimStatus.UNKNOWN,
                ClaimStatus.INSUFFICIENT_EVIDENCE, ClaimStatus.SUPERSEDED, ClaimStatus.REFUTED,
                ClaimStatus.REFINED)) {
            assertThat(EpistemicPolicy.isCitable(status))
                    .as("%s is not citable", status)
                    .isFalse();
        }
        assertThat(EpistemicPolicy.isCitable(ClaimStatus.SUPPORTED)).isTrue();
        assertThat(EpistemicPolicy.isCitable(ClaimStatus.VERIFIED)).isTrue();
        assertThat(EpistemicPolicy.isCitable(ClaimStatus.CONTESTED)).isTrue();
    }

    @Test
    @DisplayName("a withdrawn claim is never in force and never carries weight")
    void aWithdrawnClaimIsNeverInForce() {
        assertThat(EpistemicPolicy.isWithdrawn(ClaimStatus.SUPERSEDED)).isTrue();
        assertThat(EpistemicPolicy.isWithdrawn(ClaimStatus.REFUTED)).isTrue();
        assertThat(EpistemicPolicy.isInForce(ClaimStatus.SUPERSEDED)).isFalse();
        assertThat(EpistemicPolicy.isInForce(ClaimStatus.REFUTED)).isFalse();
        assertThat(EpistemicPolicy.isInForce(ClaimStatus.CANDIDATE)).isFalse();
        assertThat(EpistemicPolicy.isInForce(ClaimStatus.SUPPORTED)).isTrue();
        // A contested claim is evidence, but it does not carry weight.
        assertThat(EpistemicPolicy.isEstablished(ClaimStatus.CONTESTED)).isFalse();
        assertThat(EpistemicPolicy.isEstablished(ClaimStatus.VERIFIED)).isTrue();
    }

    @Test
    @DisplayName("a claim with no authored confidence carries no numeric support")
    void aClaimWithNoAuthoredConfidenceCarriesNoNumericSupport() {
        Claim supported = new Claim("c", "w", "k", "p", "s", "ctx",
                ClaimStatus.SUPPORTED, 0.8, 1, "src", null, null, null, null, "now", "now");
        Claim unestablished = new Claim("c2", "w", "k", "p", "s", "ctx",
                ClaimStatus.SUPPORTED, 0.0, 1, "src", null, null, null, null, "now", "now");
        assertThat(EpistemicPolicy.carriesNumericSupport(supported)).isTrue();
        assertThat(EpistemicPolicy.carriesNumericSupport(unestablished)).isFalse();
    }

    @Test
    @DisplayName("the quantity boundary is named and it is one claim that states a number")
    void theQuantityBoundaryIsNamed() {
        assertThat(EpistemicPolicy.MINIMUM_QUANTITY_EVIDENCE).isEqualTo(1);
    }

    @Test
    @DisplayName("a question that asks for a number is a quantity demand")
    void aQuestionThatAsksForANumberIsAQuantityDemand() {
        assertThat(QuestionDemand.analyse("How many tonnes of basalt are produced each year?",
                java.util.Set.of(), java.util.Set.of("how", "many", "tonnes")).kind())
                .isEqualTo(QuestionDemand.Kind.QUANTITY);
        assertThat(QuestionDemand.analyse("What is the boiling point of water in degrees Celsius?",
                java.util.Set.of(), java.util.Set.of("boiling", "point", "degrees")).kind())
                .isEqualTo(QuestionDemand.Kind.QUANTITY);
        // And a question that asks for a description is not one.
        assertThat(QuestionDemand.analyse("How does subduction produce deep earthquakes?",
                java.util.Set.of(), java.util.Set.of("subduction", "produce")).kind())
                .isEqualTo(QuestionDemand.Kind.KNOWLEDGE);
        assertThat(QuestionDemand.analyse("How is the rate of plate movement measured?",
                java.util.Set.of(), java.util.Set.of("rate", "measured")).kind())
                .isEqualTo(QuestionDemand.Kind.KNOWLEDGE);
    }

    @Test
    @DisplayName("the evidence that states a number is counted, and a date is a number too")
    void theEvidenceThatStatesANumberIsCounted() {
        Claim withNumber = new Claim("c", "w", "k", "The rate is 65 millimetres per year.",
                null, null, ClaimStatus.SUPPORTED, 0.9, 1, "src", null, null, null, null, "n", "n");
        Claim withoutNumber = new Claim("c2", "w", "k", "The rate depends on the spreading.",
                null, null, ClaimStatus.SUPPORTED, 0.9, 1, "src", null, null, null, null, "n", "n");
        assertThat(QuestionDemand.quantityEvidence(List.of(withNumber, withoutNumber))).isEqualTo(1);
        assertThat(QuestionDemand.quantityEvidence(List.of(withoutNumber))).isZero();
        assertThat(QuestionDemand.quantityEvidence(List.of())).isZero();
    }

    @Test
    @DisplayName("an imported claim is SUPPORTED by the source that states it, and not VERIFIED")
    void anImportedClaimIsSupportedAndNotVerified(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("lesson.txt");
        Files.writeString(file, LESSON, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            new DocumentImporter(store).importDocument("ws", file, null);

            List<Claim> claims = store.claims("ws");
            assertThat(claims).isNotEmpty();
            assertThat(claims).allSatisfy(claim -> {
                // SUPPORTED: one source supports it. Not VERIFIED: that
                // needs a second INDEPENDENT source.
                assertThat(claim.status()).isEqualTo(ClaimStatus.SUPPORTED);
                // And the support was EARNED by the validation stage, not
                // assigned at extraction.
                assertThat(claim.validationMethod()).contains("the validation");
            });

            // Every claim has a SUPPORTING evidence link to ITS OWN source,
            // with the verbatim excerpt.
            for (Claim claim : claims) {
                List<Evidence> evidence = store.evidence("ws").stream()
                        .filter(item -> item.claimId().equals(claim.id()))
                        .toList();
                assertThat(evidence).as("the evidence of %s", claim.id()).isNotEmpty();
                assertThat(evidence).allSatisfy(item -> {
                    assertThat(item.relation()).isEqualTo(EvidenceRelation.SUPPORTS);
                    assertThat(item.sourceId()).isEqualTo(claim.introducedBySourceId());
                    assertThat(item.excerpt()).isNotBlank();
                });
            }

            // And no claim without an evidence link became SUPPORTED.
            assertThat(store.claims("ws")).allSatisfy(claim -> {
                boolean hasEvidence = store.evidence("ws").stream()
                        .anyMatch(item -> item.claimId().equals(claim.id())
                                && item.relation() == EvidenceRelation.SUPPORTS);
                assertThat(hasEvidence).isTrue();
            });
        }
    }

    @Test
    @DisplayName("two independent sources on one proposition verify it")
    void twoIndependentSourcesOnOnePropositionVerifyIt(@TempDir Path dir) throws IOException {
        Path first = dir.resolve("a.txt");
        Path second = dir.resolve("b.txt");
        // The same proposition, in two files with different bytes: two
        // INDEPENDENT sources, which is what verification needs.
        Files.writeString(first, LESSON, StandardCharsets.UTF_8);
        Files.writeString(second, "A second textbook says:\n\n" + LESSON, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            DocumentImporter importer = new DocumentImporter(store);
            importer.importDocument("ws", first, null);
            importer.importDocument("ws", second, null);

            // The two files are two sources, and the proposition that both
            // state is VERIFIED.
            List<Claim> verified = store.claims("ws").stream()
                    .filter(claim -> claim.status() == ClaimStatus.VERIFIED)
                    .toList();
            assertThat(verified).isNotEmpty();
            assertThat(verified).allSatisfy(claim ->
                    assertThat(claim.validationMethod()).contains("independent sources"));
        }
    }

    @Test
    @DisplayName("a source record is not rewritten when the same document is imported again")
    void aSourceRecordIsNotRewrittenWhenTheSameDocumentIsImportedAgain(@TempDir Path dir)
            throws IOException {
        Path file = dir.resolve("lesson.txt");
        Files.writeString(file, LESSON, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            DocumentImporter importer = new DocumentImporter(store);
            importer.importDocument("ws", file, null);
            Source before = store.sources("ws").get(0);

            importer.importDocument("ws", file, null);
            Source after = store.sources("ws").get(0);

            assertThat(store.sources("ws")).hasSize(1);
            assertThat(after.id()).isEqualTo(before.id());
            assertThat(after.addedAt()).isEqualTo(before.addedAt());
            assertThat(after.contentHash()).isEqualTo(before.contentHash());
        }
    }

    @Test
    @DisplayName("a claim carries the span it was extracted from")
    void aClaimCarriesTheSpanItWasExtractedFrom(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("lesson.md");
        Files.writeString(file, """
                # The light reactions

                Photosynthesis is the process by which green plants use light to make sugar.
                """, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("w.sqlite"))) {
            new DocumentImporter(store).importDocument("ws", file, null);

            Claim claim = store.claims("ws").get(0);
            // The place, and the span in the declared convention.
            assertThat(claim.context()).contains("lesson.md");
            assertThat(claim.context()).contains("[characters ");
            io.matrix.contract.Provenance.Span span = ClaimSpan.parse(claim.context());
            assertThat(span).isNotNull();
            assertThat(span.unit()).isEqualTo(io.matrix.contract.Provenance.Span.Unit.CHARACTERS);
            assertThat(span.start()).isNotNegative();
            // And the source says explicitly what its extent is.
            assertThat(store.sources("ws").get(0).locator())
                    .contains(SourceRecord.WHOLE_DOCUMENT);
        }
    }

    @Test
    @DisplayName("a context that carries no span says that the whole document was read")
    void aContextThatCarriesNoSpanSaysSo() {
        String context = ClaimSpan.format("lesson.txt", null);
        assertThat(context).contains(SourceRecord.WHOLE_DOCUMENT);
        assertThat(ClaimSpan.parse(context)).isNull();
        assertThat(ClaimSpan.isWholeDocument(context)).isTrue();
    }
}
