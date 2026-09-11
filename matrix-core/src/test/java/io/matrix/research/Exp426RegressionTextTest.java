package io.matrix.research;

import io.matrix.neuron.LinearRegression;
import io.matrix.neuron.LogisticRegression;
import io.matrix.neuron.TfIdf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 426 — Coverage for LinearRegression + LogisticRegression + TfIdf.
 */
class Exp426RegressionTextTest {

    @Test
    void linearRegressionRecoversLineOfBestFit() {
        // Generate y = 2*x + 1 with small noise
        double[] xs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        double[] ys = new double[10];
        for (int i = 0; i < 10; i++) ys[i] = 2.0 * xs[i] + 1.0 + (i % 2 == 0 ? 0.01 : -0.01);
        double[] betas = LinearRegression.fitSimple(xs, ys);
        assertThat(betas[0]).isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.05));
        assertThat(betas[1]).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    void linearRegressionRidgeFitsMultivariate() {
        // 4 examples, 2 features
        double[][] X = {
                {1.0, 1.0},
                {1.0, 2.0},
                {2.0, 1.0},
                {2.0, 2.0}
        };
        double[] y = {2.0, 3.0, 3.0, 4.0};  // y = x0 + x1
        double[] betas = LinearRegression.fitRidge(X, y, 0.01);
        assertThat(betas.length).isEqualTo(2);
        assertThat(betas[0]).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-3));
        assertThat(betas[1]).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-3));
    }

    @Test
    void logisticRegressionSeparatesLinearClasses() {
        // Class 0: points near (0,0). Class 1: points near (5,5)
        Random rng = new Random(0xCAFE);
        double[][] X = new double[100][2];
        int[] y = new int[100];
        for (int i = 0; i < 100; i++) {
            int cls = i < 50 ? 0 : 1;
            y[i] = cls;
            X[i][0] = cls * 5 + rng.nextGaussian() * 0.5;
            X[i][1] = cls * 5 + rng.nextGaussian() * 0.5;
        }
        double[] w = LogisticRegression.fit(X, y, 0.5, 500, 0.01, rng);
        // Predict on a clear example — class 1 (high probability)
        double prob = LogisticRegression.predictProbability(w, new double[]{5.0, 5.0});
        assertThat(prob).isGreaterThan(0.7);
        // Predict on clearly class 0 (low probability)
        double prob2 = LogisticRegression.predictProbability(w, new double[]{-2.0, -2.0});
        assertThat(prob2).isLessThan(0.2);
    }

    @Test
    void logisticRegressionSigmoidIsMonotone() {
        assertThat(LogisticRegression.sigmoid(0.0)).isCloseTo(0.5,
                org.assertj.core.data.Offset.offset(1e-9));
        assertThat(LogisticRegression.sigmoid(1.0)).isGreaterThan(0.7);
        assertThat(LogisticRegression.sigmoid(-1.0)).isLessThan(0.3);
    }

    @Test
    void tfIdfProducesNormalisedVectors() {
        List<String> docs = List.of(
                "the quick brown fox jumps over the lazy dog",
                "the lazy dog sleeps under the table",
                "quick brown fox runs through the forest");
        TfIdf.Vocab v = TfIdf.fit(docs);
        // "fox" appears in 0 and 2
        // "the" appears in all 3 → low idf
        // Check vectors are L2-normalised
        for (double[] docVec : v.docVectors()) {
            double n2 = 0;
            for (double x : docVec) n2 += x * x;
            assertThat(Math.sqrt(n2)).isCloseTo(1.0,
                    org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    @Test
    void tfIdfCosineSimilarityRanksSimilarDocumentsFirst() {
        // doc0 == doc1 must have higher similarity than doc0 vs doc2
        List<String> docs = List.of(
                "java is a statically typed object oriented language",
                "java is statically typed and object oriented",
                "python is dynamically typed and interpreted");
        TfIdf.Vocab v = TfIdf.fit(docs);
        double sim01 = TfIdf.cosine(v.docVectors()[0], v.docVectors()[1]);
        double sim02 = TfIdf.cosine(v.docVectors()[0], v.docVectors()[2]);
        assertThat(sim01).isGreaterThan(sim02);
    }
}
