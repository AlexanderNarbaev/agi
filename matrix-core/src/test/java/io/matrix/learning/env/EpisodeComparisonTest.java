package io.matrix.learning.env;

import io.matrix.cognitive.microworld.LampEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W51 §7 — comparing one learner's episode with another's.
 *
 * <p>The comparison is only worth having if it can say that one learner did BETTER, so
 * these tests build two episodes that differ in a known way and check that the comparison
 * reports the difference in the right direction.</p>
 */
class EpisodeComparisonTest {

    /** The seed of the episodes; its value is arbitrary and fixed. */
    private static final long SEED = 42L;

    @Test
    @DisplayName("a truncated episode is compared against a complete one")
    void aTruncatedEpisodeIsComparedAgainstACompleteOne() {
        EpisodeReport complete = completeEpisode();
        EpisodeReport truncated = truncatedEpisode();

        EpisodeComparison comparison = EpisodeComparison.between(truncated, complete);

        assertThat(comparison.candidateSteps()).isEqualTo(2);
        assertThat(comparison.baselineSteps()).isEqualTo(4);
        assertThat(comparison.stepsSaved()).isEqualTo(2);
        assertThat(comparison.candidateLearned()).isFalse();
        assertThat(comparison.baselineLearned()).isTrue();
        assertThat(comparison.candidateFinalSurvivors())
                .isGreaterThan(comparison.baselineFinalSurvivors());
        assertThat(comparison.candidateUtility()).isLessThan(comparison.baselineUtility());
        assertThat(comparison.utilityGain()).isNegative();
        assertThat(comparison.improvementOver(complete)).isEqualTo(comparison.utilityGain());
    }

    @Test
    @DisplayName("the improvement is positive when the candidate learned more")
    void theImprovementIsPositiveWhenTheCandidateLearnedMore() {
        EpisodeReport complete = completeEpisode();
        EpisodeReport truncated = truncatedEpisode();

        EpisodeComparison comparison = EpisodeComparison.between(complete, truncated);

        assertThat(comparison.stepsSaved()).isNegative();
        assertThat(comparison.utilityGain()).isPositive();
        assertThat(comparison.improvementOver(truncated)).isPositive();
        assertThat(comparison.candidateLearned()).isTrue();
        assertThat(comparison.baselineLearned()).isFalse();
    }

    @Test
    @DisplayName("two identical episodes compare to no improvement at all")
    void twoIdenticalEpisodesCompareToNoImprovementAtAll() {
        EpisodeReport first = completeEpisode();
        EpisodeReport second = completeEpisode();

        EpisodeComparison comparison = EpisodeComparison.between(first, second);

        assertThat(comparison.utilityGain()).isZero();
        assertThat(comparison.stepsSaved()).isZero();
        assertThat(comparison.improvementOver(second)).isZero();
        assertThat(comparison.candidateLearned()).isEqualTo(comparison.baselineLearned());
    }

    private static EpisodeReport completeEpisode() {
        return new MicroWorldEpisodeRunner(new LampEnvironment(), "ep-w51-complete", SEED).run();
    }

    private static EpisodeReport truncatedEpisode() {
        return new MicroWorldEpisodeRunner(new LampEnvironment(), "ep-w51-truncated", SEED, 2).run();
    }
}
