package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.acquisition.DocumentImporter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W48.06 — the instants the knowledge layer DECIDES with are injected.
 *
 * <p>CONSTITUTION Article I forbids the wall clock in a decision path, and
 * the decision-path auditor treats a wall-clock read that nobody has
 * adjudicated as a blocking finding. The twenty-one sites it surfaced in this
 * package were adjudicated one by one: the ones that only record WHEN
 * something happened are annotated with {@code @ArticleIPermitted}, and the
 * ones whose value reaches a comparison are read from an injected
 * {@link Clock} instead.
 *
 * <p>These tests are the other half of that adjudication. An injected clock
 * that is never asserted is a clock nobody checked: a fixed instant must
 * REACH the record it stamps, and the default must still be the system clock
 * so that nothing changes for a caller that asks for nothing.
 */
class KnowledgeClockInjectionTest {

    /** The instant the tests hold still. */
    private static final Instant FIXED = Instant.parse("2026-03-04T05:06:07Z");

    private static Clock fixed() {
        return Clock.fixed(FIXED, ZoneOffset.UTC);
    }

    @Test
    @DisplayName("the importer stamps its records with the injected clock")
    void theImporterStampsItsRecordsWithTheInjectedClock() {
        KnowledgeSnapshot snapshot = new KnowledgeImporter(fixed())
                .importResource("knowledge/photosynthesis.pack.json");

        assertThat(snapshot.concepts()).isNotEmpty();
        assertThat(snapshot.concepts()).allSatisfy(concept ->
                assertThat(concept.createdAt()).isEqualTo(FIXED.toString()));
        assertThat(snapshot.representations()).isNotEmpty();
        assertThat(snapshot.representations()).allSatisfy(representation ->
                assertThat(representation.createdAt()).isEqualTo(FIXED.toString()));
    }

    @Test
    @DisplayName("the workbench stamps the outcome and the asked question with the injected clock")
    void theWorkbenchStampsTheOutcomeAndTheAskedQuestionWithTheInjectedClock(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString(), fixed())) {
            workbench.loadBundledPackage("photosynthesis");

            AssessmentItem item = workbench.dataset("photosynthesis").items().get(0);
            AssessmentOutcome outcome = workbench.assess("photosynthesis", item.id(),
                    String.join(" ", item.keyTerms()), 0.9, "session-1");
            assertThat(outcome.answeredAt()).isEqualTo(FIXED.toEpochMilli());

            workbench.ask("photosynthesis", "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY);
            List<AskedQuestion> asked = workbench.askedQuestions("photosynthesis");
            assertThat(asked).isNotEmpty();
            assertThat(asked).allSatisfy(question ->
                    assertThat(question.lastAskedAt()).isEqualTo(FIXED.toString()));
        }
    }

    @Test
    @DisplayName("the goal manager stamps the goal with the injected clock")
    void theGoalManagerStampsTheGoalWithTheInjectedClock(@TempDir Path dir) {
        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("knowledge.sqlite"))) {
            GoalManager manager = new GoalManager(store, fixed());

            LearningGoal goal = manager.start("photosynthesis", "Learn photosynthesis",
                    List.of("the learner can answer the held-out items"), List.of(), List.of(), 10,
                    "reopen the workspace and continue");

            assertThat(goal.createdAt()).isEqualTo(FIXED.toString());
            assertThat(goal.updatedAt()).isEqualTo(FIXED.toString());
        }
    }

    @Test
    @DisplayName("the refiner stamps the refinement with the injected clock")
    void theRefinerStampsTheRefinementWithTheInjectedClock(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString(), fixed())) {
            workbench.loadBundledPackage("photosynthesis");
            KnowledgeSnapshot snapshot = workbench.workspace("photosynthesis");
            String conceptId = snapshot.concepts().stream()
                    .filter(c -> c.name().equals("Photosynthesis")).findFirst().orElseThrow().id();
            Claim superseded = snapshot.claims().stream()
                    .filter(c -> c.proposition().contains("Oxygenic photosynthesis releases oxygen"))
                    .findFirst().orElseThrow();

            KnowledgeChanges changes = new KnowledgeRefiner(fixed()).refine("photosynthesis", snapshot,
                    new KnowledgeRefiner.Refinement(conceptId, superseded.id(),
                            "Oxygenic photosynthesis releases oxygen at a rate that falls as the "
                                    + "light level falls",
                            "In green plants, in light", "general biology",
                            ClaimStatus.SUPPORTED, 0.8, SourceKind.HUMAN_ASSERTION,
                            "A measured outcome from the greenhouse",
                            "urn:experiment:greenhouse-2026-10-31",
                            "The absorption peaks were measured at 450 and 650 nanometres.",
                            60, 95, RevisionKind.CORRECTION,
                            "The correction names the wavelengths the pigment absorbs.",
                            List.of("the rate falls with the light level"),
                            List.of("plants still photosynthesise in light"),
                            List.of("the earlier claim held without mentioning the light level"),
                            List.of("why a dimmer day lowers the measured oxygen release"),
                            "Oxygenic photosynthesis releases oxygen at a rate that falls as the "
                                    + "light level falls",
                            AbstractionLevel.INTRODUCTORY));

            assertThat(changes.representations()).isNotEmpty();
            assertThat(changes.representations()).allSatisfy(representation ->
                    assertThat(representation.createdAt()).isEqualTo(FIXED.toString()));
            assertThat(changes.claims()).allSatisfy(claim ->
                    assertThat(claim.updatedAt()).isEqualTo(FIXED.toString()));
        }
    }

    @Test
    @DisplayName("the acquisition stamps the imported document with the injected clock")
    void theAcquisitionStampsTheImportedDocumentWithTheInjectedClock(@TempDir Path dir)
            throws IOException {
        Path file = dir.resolve("lesson.txt");
        Files.writeString(file, """
                Photosynthesis is the process by which green plants use light to convert carbon
                dioxide and water into sugar and oxygen.

                The light-dependent reactions happen in the thylakoid membrane. They produce ATP
                and NADPH.

                The Calvin cycle happens in the stroma. It uses ATP and NADPH to build sugar from
                carbon dioxide.
                """, StandardCharsets.UTF_8);

        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("knowledge.sqlite"))) {
            DocumentImporter.ImportResult result = new DocumentImporter(store, fixed())
                    .importDocument("ws", file, null);

            assertThat(result.source().importedAt()).isEqualTo(FIXED.toString());
            assertThat(store.sources("ws")).allSatisfy(source ->
                    assertThat(source.addedAt()).isEqualTo(FIXED.toString()));
        }
    }

    @Test
    @DisplayName("the consolidation reads the injected clock, and the age window decides")
    void theConsolidationReadsTheInjectedClock(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString(), fixed())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.ask("photosynthesis", "What is quantum chromodynamics?",
                    AbstractionLevel.INTRODUCTORY);

            LearningWorkbenchResource resource = new LearningWorkbenchResource();
            resource.workbench = workbench;

            // The instant of the run is the injected one, so the record that
            // was asked about at FIXED is a day old and not a month old: the
            // thirty-day window must not forget it.
            resource.setClock(fixed());
            Map<String, Object> fresh = resource.consolidate("photosynthesis", 10, 30, null);
            assertThat(fresh.get("forgotten")).isEqualTo(0);

            // Move the clock past the window and the same record decays.
            resource.setClock(Clock.fixed(FIXED.plusSeconds(60L * 24 * 60 * 60), ZoneOffset.UTC));
            Map<String, Object> stale = resource.consolidate("photosynthesis", 10, 30, null);
            assertThat(stale.get("forgotten")).isNotEqualTo(0);
        }
    }

    @Test
    @DisplayName("the default clock is still the system clock")
    void theDefaultClockIsStillTheSystemClock(@TempDir Path dir) {
        long before = System.currentTimeMillis();
        try (KnowledgeStore store = new KnowledgeStore(dir.resolve("knowledge.sqlite"))) {
            GoalManager manager = new GoalManager(store);
            LearningGoal goal = manager.start("photosynthesis", "Learn photosynthesis",
                    List.of("the learner can answer the held-out items"), List.of(), List.of(), 10,
                    "reopen the workspace and continue");

            long stamped = Instant.parse(goal.updatedAt()).toEpochMilli();
            assertThat(stamped).isGreaterThanOrEqualTo(before);
            assertThat(stamped).isLessThanOrEqualTo(System.currentTimeMillis());
        }
    }
}
