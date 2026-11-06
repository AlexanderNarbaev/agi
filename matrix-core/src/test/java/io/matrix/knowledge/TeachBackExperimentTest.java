package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W41 — the teach-back, measured properly.
 *
 * <p>Research stream A checked the RECON-W40 measurement against the
 * published validity requirements for teach-back and found it fails seven of
 * seven. The AHRQ's own guidance names the anti-pattern: "If they parrot your
 * words back to you, they may not have understood."
 *
 * <p>These tests hold the rebuilt experiment to the requirements it has to
 * meet to mean anything: disjoint phases, an independent learner, a leakage
 * audit, and a comparison against the legacy measurement on the same items.
 */
class TeachBackExperimentTest {

    private KnowledgeSnapshot photosynthesis;
    private AssessmentDataset dataset;

    @BeforeEach
    void setUp() {
        photosynthesis = new KnowledgeImporter().importResource("knowledge/photosynthesis.pack.json");
        dataset = AssessmentDataset.loadResource(
                "knowledge/photosynthesis.adversarial.json", photosynthesis);
    }

    private List<Claim> taughtClaims() {
        List<Claim> claims = new ArrayList<>();
        for (Claim claim : photosynthesis.claims()) {
            if (claim.conceptId().equals(conceptId("Photosynthesis"))) {
                claims.add(claim);
            }
        }
        return claims;
    }

    private String conceptId(String name) {
        return photosynthesis.concepts().stream()
                .filter(concept -> concept.name().equals(name))
                .map(Concept::id)
                .findFirst()
                .orElseThrow();
    }

    private String lessonText() {
        StringBuilder text = new StringBuilder();
        for (Representation representation : photosynthesis.representations()) {
            if (representation.conceptId().equals(conceptId("Photosynthesis"))) {
                text.append(representation.statement()).append(' ');
            }
        }
        return text.toString();
    }

    private TeachBackExperiment.Result run() {
        return TeachBackExperiment.run(conceptId("Photosynthesis"), photosynthesis, dataset,
                taughtClaims(), lessonText(), 0.2);
    }

    @Test
    @DisplayName("the phases are disjoint sets of items")
    void thePhasesAreDisjointSetsOfItems() {
        List<String> pre = dataset.items().stream()
                .filter(item -> item.variant().equals("pre")).map(AssessmentItem::id).toList();
        List<String> post = dataset.items().stream()
                .filter(item -> item.variant().equals("post")).map(AssessmentItem::id).toList();
        List<String> delayed = dataset.items().stream()
                .filter(item -> item.variant().equals("delayed")).map(AssessmentItem::id).toList();
        List<String> transfer = dataset.items().stream()
                .filter(item -> item.variant().equals("transfer")).map(AssessmentItem::id).toList();

        assertThat(pre).isNotEmpty();
        assertThat(post).isNotEmpty();
        assertThat(delayed).isNotEmpty();
        assertThat(transfer).isNotEmpty();
        assertThat(pre).doesNotContainAnyElementsOf(post);
        assertThat(pre).doesNotContainAnyElementsOf(delayed);
        assertThat(post).doesNotContainAnyElementsOf(delayed);
        assertThat(post).doesNotContainAnyElementsOf(transfer);
        assertThat(delayed).doesNotContainAnyElementsOf(transfer);
    }

    @Test
    @DisplayName("every item has a key and a misconception-bound alternative")
    void everyItemHasAKeyAndAMisconceptionBoundAlternative() {
        for (AssessmentItem item : dataset.items()) {
            assertThat(item.correctAlternative()).as("the key of %s", item.id()).isNotNull();
            assertThat(item.alternatives()).as("the alternatives of %s", item.id()).hasSizeGreaterThan(1);
            assertThat(item.alternatives()).as("the misconceptions of %s", item.id())
                    .anySatisfy(alternative ->
                            assertThat(alternative.misconceptionId()).isNotBlank());
        }
    }

    @Test
    @DisplayName("the learner knows nothing before the lesson")
    void theLearnerKnowsNothingBeforeTheLesson() {
        TeachBackExperiment.Result result = run();

        TeachBackExperiment.Phase pre = result.phases().stream()
                .filter(phase -> phase.name().equals("pre")).findFirst().orElseThrow();
        assertThat(pre.accuracy()).isZero();
    }

    @Test
    @DisplayName("the lesson moves the learner, and the gain is reported")
    void theLessonMovesTheLearnerAndTheGainIsReported() {
        TeachBackExperiment.Result result = run();

        TeachBackExperiment.Phase post = result.phases().stream()
                .filter(phase -> phase.name().equals("post")).findFirst().orElseThrow();
        assertThat(post.accuracy()).isGreaterThan(0.0);
        assertThat(result.metExpectation()).isTrue();
        assertThat(result.conclusion()).contains("machine-to-machine");
    }

    @Test
    @DisplayName("the legacy learner restates the lesson, and the audit says so")
    void theLegacyLearnerRestatesTheLesson() {
        TeachBackExperiment.Result result = run();

        assertThat(result.legacyRestatements()).isGreaterThan(0);
        assertThat(result.restatements()).isLessThan(result.legacyRestatements());
    }

    @Test
    @DisplayName("the structured learner beats the lexical baseline on the adversarial items")
    void theStructuredLearnerBeatsTheLexicalBaseline() {
        TeachBackExperiment.Result result = run();

        // Research stream A's prediction P1: the structured learner's
        // discrimination exceeds the lexical baseline's on the adversarial
        // subset.
        assertThat(result.structuredAccuracy())
                .as("the structured learner against the lexical baseline on adversarial items")
                .isGreaterThan(result.lexicalBaselineAccuracy());
    }

    @Test
    @DisplayName("the calibration of the stated confidence is measured")
    void theCalibrationOfTheStatedConfidenceIsMeasured() {
        TeachBackExperiment.Result result = run();

        assertThat(result.calibration().count()).isGreaterThan(0);
        assertThat(result.calibration().brier()).isBetween(0.0, 1.0);
        assertThat(result.calibration().meaning()).contains("demonstration");
    }

    @Test
    @DisplayName("the experiment reports what it does not claim")
    void theExperimentReportsWhatItDoesNotClaim() {
        TeachBackExperiment.Result result = run();

        assertThat(result.conclusion())
                .contains("not evidence about human learning");
    }
}
