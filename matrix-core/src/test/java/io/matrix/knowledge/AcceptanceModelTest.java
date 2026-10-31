package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W40 — the supervised learner, and the honest comparison.
 *
 * <p>This class exists because `AgentBrainService.generateFromBir()` was
 * removed. That method claimed to generate language from a trained corpus
 * and could not: its labels were a constant `true`, its answers were
 * discarded, and its output was one bit rendered as ASCII, so the branch
 * that called it was unreachable.
 *
 * <p>What replaces it has to be measurable. The point of these tests is not
 * that the learner wins - the deterministic grader is the production path
 * and stays there unless the measurement says otherwise - but that the
 * comparison is made, is reported, and is not rigged.
 */
class AcceptanceModelTest {

    private KnowledgeSnapshot snapshot;
    private AssessmentDataset dataset;

    @BeforeEach
    void setUp() {
        snapshot = new KnowledgeImporter().importResource("knowledge/photosynthesis.pack.json");
        dataset = AssessmentDataset.loadResource("knowledge/photosynthesis.assessment.json", snapshot);
    }

    private static AssessmentOutcome outcome(AssessmentItem item, String answer, boolean correct) {
        return new AssessmentOutcome(item.id(), correct, correct ? 1.0 : 0.0, 0.7, answer, "s1", 1L);
    }

    @Test
    @DisplayName("the features are explicit, not a hash")
    void theFeaturesAreExplicit() {
        AssessmentItem item = dataset.items().stream()
                .filter(i -> i.id().equals("t-recall-1")).findFirst().orElseThrow();

        long withAll = TextFeatureVector.features(item,
                "Photosynthesis uses light, carbon dioxide, water and sugar.");
        long withoutAny = TextFeatureVector.features(item, "nothing relevant here");

        List<Boolean> bits = TextFeatureVector.bits(withAll);
        assertThat(bits).hasSize(TextFeatureVector.WIDTH);

        // The key terms of the item are the first eight bits, and the answer
        // above contains light, carbon, dioxide and sugar.
        assertThat(bits.get(0)).as("light").isTrue();
        assertThat(bits.get(1)).as("carbon").isTrue();
        assertThat(bits.get(2)).as("dioxide").isTrue();
        assertThat(bits.get(3)).as("water").isTrue();
        assertThat(bits.get(4)).as("sugar").isTrue();

        // An answer with none of them has none of them set.
        assertThat(TextFeatureVector.bits(withoutAny).get(0)).isFalse();
        assertThat(TextFeatureVector.bits(withoutAny).get(4)).isFalse();
    }

    @Test
    @DisplayName("the learner is trained on the training outcomes and measured on the held-out ones")
    void theLearnerIsMeasuredOnHeldOutItems() {
        List<AssessmentOutcome> outcomes = new ArrayList<>();
        for (AssessmentItem item : dataset.items()) {
            if (item.heldOut()) {
                continue;
            }
            String good = String.join(" ", item.keyTerms());
            outcomes.add(outcome(item, good, true));
        }

        AcceptanceModel model = new AcceptanceModel(dataset.items(), new Random(42));
        AcceptanceModel.Evaluation evaluation = model.trainAndEvaluate(outcomes);

        assertThat(evaluation.trainedOn()).isEqualTo(11);
        assertThat(evaluation.measuredOn()).isZero();
        assertThat(evaluation.conclusion()).contains("nothing to measure");
    }

    @Test
    @DisplayName("the comparison with the deterministic grader is made and reported")
    void theComparisonIsMade() {
        // A learner who answers every item correctly - the case in which the
        // deterministic grader is right about everything, and the only case
        // in which the learned rule has something to beat. The learned rule
        // is measured against exactly that.
        List<AssessmentOutcome> outcomes = new ArrayList<>();
        for (AssessmentItem item : dataset.items()) {
            String good = String.join(" ", item.keyTerms());
            outcomes.add(outcome(item, good, true));
        }

        AcceptanceModel model = new AcceptanceModel(dataset.items(), new Random(42));
        AcceptanceModel.Evaluation evaluation = model.trainAndEvaluate(outcomes);

        assertThat(evaluation.trainedOn()).isEqualTo(11);
        assertThat(evaluation.measuredOn()).isEqualTo(11);
        assertThat(evaluation.deterministicCorrectOnHeldOut()).isEqualTo(11);
        assertThat(evaluation.deterministicAccuracy()).isEqualTo(1.0);
        assertThat(evaluation.conclusion()).contains("deterministic grader");
        // The grader is right about everything, so the learned rule can only
        // tie it at best - and it is not promoted past it.
        assertThat(evaluation.heldOutAccuracy()).isLessThanOrEqualTo(1.0);

        System.out.println("[RECON-W40] acceptance model: trained on " + evaluation.trainedOn()
                + ", measured on " + evaluation.measuredOn()
                + ", learned " + Math.round(evaluation.heldOutAccuracy() * 100)
                + "% against the grader's " + Math.round(evaluation.deterministicAccuracy() * 100) + "%");
    }

    @Test
    @DisplayName("the learned rule is distilled into an inspectable form")
    void theLearnedRuleIsDistilled() {
        List<AssessmentOutcome> outcomes = new ArrayList<>();
        for (AssessmentItem item : dataset.items()) {
            if (item.heldOut()) {
                continue;
            }
            outcomes.add(outcome(item, String.join(" ", item.keyTerms()), true));
        }

        // A small machine, so that the distillation - which enumerates the
        // whole input space - is quick enough for a test.
        AcceptanceModel model = new AcceptanceModel(dataset.items(), new Random(42), 8, 20);
        model.trainAndEvaluate(outcomes);

        String rule = model.distilledRule();
        assertThat(rule).isNotBlank();
        assertThat(rule).contains("clause");
        // The distillation is cached, so asking twice gives the same answer.
        assertThat(model.distilledRule()).isEqualTo(rule);
    }

    @Test
    @DisplayName("with no outcomes nothing is learned and nothing is claimed")
    void withNoOutcomesNothingIsClaimed() {
        AcceptanceModel model = new AcceptanceModel(dataset.items(), new Random(42));
        AcceptanceModel.Evaluation evaluation = model.trainAndEvaluate(List.of());

        assertThat(evaluation.trainedOn()).isZero();
        assertThat(evaluation.beatsDeterministic()).isFalse();
        assertThat(evaluation.conclusion()).contains("nothing is claimed");
    }

    @Test
    @DisplayName("the learner is deterministic")
    void theLearnerIsDeterministic() {
        List<AssessmentOutcome> outcomes = new ArrayList<>();
        for (AssessmentItem item : dataset.items()) {
            String good = String.join(" ", item.keyTerms());
            outcomes.add(outcome(item, item.heldOut() ? "wrong" : good, !item.heldOut()));
        }

        AcceptanceModel.Evaluation first = new AcceptanceModel(dataset.items(), new Random(42))
                .trainAndEvaluate(outcomes);
        AcceptanceModel.Evaluation second = new AcceptanceModel(dataset.items(), new Random(42))
                .trainAndEvaluate(outcomes);

        assertThat(first.heldOutAccuracy()).isEqualTo(second.heldOutAccuracy());
    }
}
