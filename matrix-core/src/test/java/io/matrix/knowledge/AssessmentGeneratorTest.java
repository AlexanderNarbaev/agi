package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the concept the workspace acquired is practicable.
 *
 * <p>The mission's practical goal says the system must "practise applying
 * the concept" — and the assessment dataset came from the knowledge
 * package, so a concept acquired from a document had nothing to practise
 * with.
 */
class AssessmentGeneratorTest {

    private static final String DOCUMENT = """
            The thylakoid membrane is the site of the light-dependent reactions of photosynthesis.

            ATP synthase is the enzyme that uses the proton gradient to make ATP.
            """;

    @Test
    @DisplayName("the imported concept gets items")
    void theImportedConceptGetsItems(@TempDir Path dir) throws Exception {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            Path file = dir.resolve("notes.txt");
            Files.writeString(file, DOCUMENT, StandardCharsets.UTF_8);
            workbench.importDocument("photosynthesis", file, null);

            AssessmentDataset dataset = workbench.dataset("photosynthesis");

            // The imported concepts are now practisable.
            assertThat(dataset.items()).anySatisfy(item -> {
                assertThat(item.conceptId()).isEqualTo("383871d0");
                assertThat(item.dimension()).isEqualTo(LearningDimension.RECALL);
            });
            assertThat(dataset.items()).anySatisfy(item -> {
                assertThat(item.conceptId()).isEqualTo("6a6bcda1");
                assertThat(item.dimension()).isEqualTo(LearningDimension.RECALL);
            });
        }
    }

    @Test
    @DisplayName("the package's own concepts are not generated again")
    void thePackagesOwnConceptsAreNotGeneratedAgain(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            AssessmentDataset dataset = workbench.dataset("photosynthesis");

            // The package's photosynthesis concept has its own items; the
            // generator must not add a second set beside them.
            assertThat(dataset.items()).filteredOn(item ->
                    item.conceptId().equals("c-photosynthesis-4d4bb673"))
                    .hasSize(14);
            assertThat(dataset.items()).filteredOn(item -> item.id().startsWith("gen-"))
                    .noneSatisfy(item ->
                            assertThat(item.conceptId()).isEqualTo("c-photosynthesis-4d4bb673"));
        }
    }

    @Test
    @DisplayName("every dimension comes as a practice and an independent test")
    void everyDimensionComesAsAPracticeAndAnIndependentTest(@TempDir Path dir) throws Exception {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            Path file = dir.resolve("notes.txt");
            Files.writeString(file, DOCUMENT, StandardCharsets.UTF_8);
            workbench.importDocument("photosynthesis", file, null);

            List<AssessmentItem> items = workbench.dataset("photosynthesis").items().stream()
                    .filter(item -> item.conceptId().equals("383871d0"))
                    .toList();

            assertThat(items).isNotEmpty();
            for (LearningDimension dimension : LearningDimension.values()) {
                List<AssessmentItem> ofDimension = items.stream()
                        .filter(item -> item.dimension() == dimension)
                        .toList();
                if (ofDimension.isEmpty()) {
                    continue;
                }
                // One practice and one independent test, with the same key
                // terms and a different wording: the learner cannot pass
                // the test by remembering the practice.
                assertThat(ofDimension).hasSize(2);
                AssessmentItem practice = ofDimension.stream()
                        .filter(item -> !item.heldOut()).findFirst().orElseThrow();
                AssessmentItem heldOut = ofDimension.stream()
                        .filter(AssessmentItem::heldOut).findFirst().orElseThrow();
                assertThat(heldOut.keyTerms()).isEqualTo(practice.keyTerms());
                assertThat(heldOut.prompt()).isNotEqualTo(practice.prompt());
            }
        }
    }

    @Test
    @DisplayName("the key terms come from the workspace's own claims")
    void theKeyTermsComeFromTheWorkspacesOwnClaims(@TempDir Path dir) throws Exception {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            Path file = dir.resolve("notes.txt");
            Files.writeString(file, DOCUMENT, StandardCharsets.UTF_8);
            workbench.importDocument("photosynthesis", file, null);

            List<AssessmentItem> items = workbench.dataset("photosynthesis").items().stream()
                    .filter(item -> item.conceptId().equals("383871d0"))
                    .toList();

            List<String> claimTerms = workbench.workspace("photosynthesis").claims().stream()
                    .filter(claim -> claim.conceptId().equals("383871d0"))
                    .flatMap(claim -> TextFeatures.tokens(claim.proposition()).stream())
                    .distinct()
                    .toList();
            List<String> evidenceTerms = workbench.workspace("photosynthesis").sources().stream()
                    .flatMap(source -> TextFeatures.tokens(source.excerpt() == null ? "" : source.excerpt()).stream())
                    .distinct()
                    .toList();

            for (AssessmentItem item : items) {
                // The grounding item's terms come from the EXCERPTS of the
                // sources behind the claims; the others come from the
                // claims themselves. Neither invents a term.
                List<String> allowed = item.dimension() == LearningDimension.GROUNDING
                        ? evidenceTerms : claimTerms;
                for (String term : item.keyTerms()) {
                    assertThat(allowed).contains(term);
                }
            }
            // The claim says "thylakoid membrane ... light-dependent reactions
            // of photosynthesis", so those are the terms.
            assertThat(items.get(0).keyTerms()).contains("thylakoid");
        }
    }

    @Test
    @DisplayName("the generated items survive a restart")
    void theGeneratedItemsSurviveARestart(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("w.sqlite");
        Path file = dir.resolve("notes.txt");
        Files.writeString(file, DOCUMENT, StandardCharsets.UTF_8);

        int before;
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.importDocument("photosynthesis", file, null);
            before = workbench.dataset("photosynthesis").items().size();
        }

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            assertThat(workbench.dataset("photosynthesis").items()).hasSize(before);
            assertThat(workbench.dataset("photosynthesis").items()).anySatisfy(item -> {
                assertThat(item.conceptId()).isEqualTo("383871d0");
                assertThat(item.id()).startsWith("gen-");
            });
        }
    }

    @Test
    @DisplayName("the learner can practise the imported concept, and it is scored")
    void theLearnerCanPractiseTheImportedConcept(@TempDir Path dir) throws Exception {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            Path file = dir.resolve("notes.txt");
            Files.writeString(file, DOCUMENT, StandardCharsets.UTF_8);
            workbench.importDocument("photosynthesis", file, null);

            AssessmentItem item = workbench.dataset("photosynthesis").items().stream()
                    .filter(candidate -> candidate.conceptId().equals("383871d0"))
                    .filter(candidate -> candidate.dimension() == LearningDimension.RECALL)
                    .filter(candidate -> !candidate.heldOut())
                    .findFirst()
                    .orElseThrow();

            // The learner answers with the workspace's own content.
            AssessmentOutcome outcome = workbench.assess("photosynthesis", item.id(),
                    "The thylakoid membrane is the site of the light-dependent reactions of "
                            + "photosynthesis.",
                    0.8, "session-1");

            assertThat(outcome.correct()).isTrue();
            assertThat(outcome.score()).isGreaterThan(0.5);
        }
    }

    @Test
    @DisplayName("the generation is deterministic")
    void theGenerationIsDeterministic(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("notes.txt");
        Files.writeString(file, DOCUMENT, StandardCharsets.UTF_8);

        List<String> first;
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("a.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.importDocument("photosynthesis", file, null);
            first = workbench.dataset("photosynthesis").items().stream()
                    .map(item -> item.id() + "|" + item.prompt() + "|" + item.keyTerms())
                    .toList();
        }
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("b.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.importDocument("photosynthesis", file, null);
            List<String> second = workbench.dataset("photosynthesis").items().stream()
                    .map(item -> item.id() + "|" + item.prompt() + "|" + item.keyTerms())
                    .toList();
            assertThat(second).isEqualTo(first);
        }
    }
}
