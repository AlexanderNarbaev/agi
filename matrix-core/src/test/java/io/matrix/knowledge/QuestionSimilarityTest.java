package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W40 — does the HDC earn its place in the question log?
 *
 * <p>The rule of this wave is that a component is integrated when it is
 * justified, and not because it exists. This test measures the two ways of
 * deciding whether two questions are the same question, on labelled pairs,
 * and reports which one separates them.
 */
class QuestionSimilarityTest {

    private static final List<String[]> SAME = List.of(
            new String[] {"What is photosynthesis?", "photosynthesis is what"},
            new String[] {"What is photosynthesis?", "what is photosynthesis in green plants"},
            new String[] {"How does a plant make sugar from light?",
                    "how does a plant make sugar from light, exactly"});

    private static final List<String[]> DIFFERENT = List.of(
            new String[] {"What is photosynthesis?", "What is cellular respiration?"},
            new String[] {"How does a plant make sugar from light?",
                    "What controls stomatal conductance?"},
            new String[] {"What is RuBisCO?", "What is the Calvin-Benson cycle?"});

    @Test
    @DisplayName("the two methods are measured on the same labelled pairs")
    void theTwoMethodsAreMeasured() {
        QuestionSimilarity similarity = new QuestionSimilarity(1024, 42L);

        double exactSame = SAME.stream()
                .mapToDouble(p -> similarity.exactOverlap(p[0], p[1]))
                .min().orElse(0);
        double exactDifferent = DIFFERENT.stream()
                .mapToDouble(p -> similarity.exactOverlap(p[0], p[1]))
                .max().orElse(1);

        double hdcSame = SAME.stream()
                .mapToDouble(p -> similarity.hyperdimensionalSimilarity(p[0], p[1]))
                .min().orElse(0);
        double hdcDifferent = DIFFERENT.stream()
                .mapToDouble(p -> similarity.hyperdimensionalSimilarity(p[0], p[1]))
                .max().orElse(1);

        System.out.println("[RECON-W40] question similarity, threshold "
                + QuestionSimilarity.DEFAULT_THRESHOLD);
        System.out.println("  exact overlap      : same-question pairs from " + fmt(exactSame)
                + ", different-question pairs up to " + fmt(exactDifferent));
        System.out.println("  hyperdimensional   : same-question pairs from " + fmt(hdcSame)
                + ", different-question pairs up to " + fmt(hdcDifferent));

        int exactErrors = misclassifications(similarity, true);
        int hdcErrors = misclassifications(similarity, false);

        System.out.println("  misclassifications : exact " + exactErrors + ", hyperdimensional " + hdcErrors
                + ", out of " + (SAME.size() + DIFFERENT.size()) + " pairs");

        assertThat(exactSame).isGreaterThanOrEqualTo(exactDifferent);
        assertThat(hdcSame).isGreaterThanOrEqualTo(hdcDifferent);

        // The verdict is recorded either way: the component that separates
        // the pairs better is the one that is used.
        if (hdcErrors < exactErrors) {
            System.out.println("  verdict: the hyperdimensional method separates the pairs better.");
        } else if (exactErrors < hdcErrors) {
            System.out.println("  verdict: the exact overlap separates the pairs better, so the "
                    + "question log keeps using it.");
        } else {
            System.out.println("  verdict: the two methods separate the pairs equally well.");
        }
    }

    @Test
    @DisplayName("the exact overlap separates the labelled pairs")
    void theExactOverlapSeparatesThePairs() {
        double lowestSame = 1.0;
        double highestDifferent = 0.0;
        for (String[] pair : SAME) {
            lowestSame = Math.min(lowestSame, QuestionSimilarity.exactOverlap(pair[0], pair[1]));
        }
        for (String[] pair : DIFFERENT) {
            highestDifferent = Math.max(highestDifferent, QuestionSimilarity.exactOverlap(pair[0], pair[1]));
        }
        // The separation, not the threshold, is what matters here: every
        // same-question pair scores above every different-question pair.
        assertThat(lowestSame).as("the lowest same-question overlap").isGreaterThan(highestDifferent);
        // The threshold is not perfect, and that is measured rather than
        // asserted away: the pair that differs only by extra words scores
        // 1/3, below the 0.5 threshold.
        assertThat(lowestSame).isLessThan(0.5);
    }

    @Test
    @DisplayName("the hyperdimensional encoding is deterministic")
    void theEncodingIsDeterministic() {
        QuestionSimilarity first = new QuestionSimilarity(1024, 42L);
        QuestionSimilarity second = new QuestionSimilarity(1024, 42L);

        assertThat(first.hyperdimensionalSimilarity("What is photosynthesis?", "photosynthesis is what"))
                .isEqualTo(second.hyperdimensionalSimilarity("What is photosynthesis?", "photosynthesis is what"));
    }

    private static int misclassifications(QuestionSimilarity similarity, boolean useExact) {
        int errors = 0;
        for (String[] pair : SAME) {
            double v = useExact ? similarity.exactOverlap(pair[0], pair[1])
                    : similarity.hyperdimensionalSimilarity(pair[0], pair[1]);
            if (v < QuestionSimilarity.DEFAULT_THRESHOLD) {
                errors++;
            }
        }
        for (String[] pair : DIFFERENT) {
            double v = useExact ? similarity.exactOverlap(pair[0], pair[1])
                    : similarity.hyperdimensionalSimilarity(pair[0], pair[1]);
            if (v >= QuestionSimilarity.DEFAULT_THRESHOLD) {
                errors++;
            }
        }
        return errors;
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
