package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EntropyDecompositionTest {

    @Test
    void entropyOfUniformIsLogN() {
        double[] uniform = {0.25, 0.25, 0.25, 0.25};
        assertThat(EntropyDecomposition.entropy(uniform)).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void entropyOfDeltaIsZero() {
        double[] delta = {1.0, 0.0, 0.0, 0.0};
        assertThat(EntropyDecomposition.entropy(delta)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void jointEntropyOfIndependentIsSumOfMarginals() {
        double[][] joint = {{0.25, 0.25}, {0.25, 0.25}};  // p(X,Y) = 0.5 * 0.5
        double hXY = EntropyDecomposition.jointEntropy(joint);
        double hX = EntropyDecomposition.entropy(new double[]{0.5, 0.5});  // = 1
        double hY = EntropyDecomposition.entropy(new double[]{0.5, 0.5});  // = 1
        assertThat(hXY).isCloseTo(hX + hY, within(1e-9));
    }

    @Test
    void mutualInformationOfIndependentIsZero() {
        double[] p = {0.5, 0.5};
        double[] q = {0.5, 0.5};
        double[][] joint = {{0.25, 0.25}, {0.25, 0.25}};
        double mi = EntropyDecomposition.mutualInformation(p, q, joint);
        assertThat(mi).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void mutualInformationOfIdenticalIsEntropy() {
        double[] p = {0.5, 0.3, 0.2};
        double[][] joint = {{0.5, 0.0, 0.0}, {0.0, 0.3, 0.0}, {0.0, 0.0, 0.2}};  // diagonal
        double mi = EntropyDecomposition.mutualInformation(p, p, joint);
        // I(X; X) = H(X)
        assertThat(mi).isCloseTo(EntropyDecomposition.entropy(p), within(1e-9));
    }

    @Test
    void decomposeComponentsAreConsistent() {
        double[] p = {0.5, 0.5};
        double[] q = {0.5, 0.5};
        double[][] joint = {{0.3, 0.2}, {0.2, 0.3}};  // correlated
        EntropyDecomposition.EntropyComponents comp = EntropyDecomposition.decompose(p, q, joint);
        // H(X, Y) = H(X) + H(Y) - I(X; Y)
        assertThat(comp.hXY()).isCloseTo(comp.hX() + comp.hY() - comp.mutualInformation(), within(1e-9));
    }

    @Test
    void marginalsAreCorrect() {
        double[][] joint = {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}};
        double[] margX = EntropyDecomposition.marginalX(joint);
        assertThat(margX).containsExactly(6.0, 15.0);
        double[] margY = EntropyDecomposition.marginalY(joint);
        assertThat(margY).containsExactly(5.0, 7.0, 9.0);
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
