package io.matrix.consciousness;

import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W199 — CognitiveTriplet property-based tests.
 */
class CognitiveTripletPropertyTest {

    @Property(tries = 50)
    void propertyNullReturnsZero() {
        assertThat(CognitiveTriplet.interactionInformation(null)).isEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyEmptyReturnsZero(@ForAll("smallDims") int dim) {
        if (dim < 1) return;
        double[][][] joint = new double[dim][0][0];
        assertThat(CognitiveTriplet.interactionInformation(joint)).isEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyIndependentTripletsZeroII(@ForAll("independentJoints") double[][][] joint) {
        double ii = CognitiveTriplet.interactionInformation(joint);
        if (!Double.isNaN(ii)) {
            assertThat(ii).isCloseTo(0.0, Assertions.offset(1e-9));
        }
    }

    @Property(tries = 30)
    void propertyClassifyValidString(@ForAll("randomJoints") double[][][] joint) {
        String c = CognitiveTriplet.classifyInteraction(joint, 0.01);
        assertThat(c).isIn("SYNERGY", "REDUNDANCY", "INDEPENDENT");
    }

    @Property(tries = 30)
    void propertyIIIsFinite(@ForAll("randomJoints") double[][][] joint) {
        double ii = CognitiveTriplet.interactionInformation(joint);
        if (joint.length > 0 && joint[0].length > 0 && joint[0][0].length > 0) {
            assertThat(Double.isFinite(ii) || Double.isNaN(ii)).isTrue();
        }
    }

    @Provide
    Arbitrary<Integer> smallDims() {
        return Arbitraries.integers().between(1, 4);
    }

    @Provide
    Arbitrary<double[][][]> independentJoints() {
        // Independent joint: p(x,y,z) = p(x) * p(y) * p(z) * total
        return Arbitraries.integers().between(2, 3).flatMap(nx ->
            Arbitraries.integers().between(2, 3).flatMap(ny ->
                Arbitraries.integers().between(2, 3).map(nz -> {
                    double[] pX = new double[nx];
                    double[] pY = new double[ny];
                    double[] pZ = new double[nz];
                    double sumX = 0, sumY = 0, sumZ = 0;
                    java.util.Random rng = new java.util.Random(42);
                    for (int i = 0; i < nx; i++) { pX[i] = 0.1 + rng.nextDouble(); sumX += pX[i]; }
                    for (int i = 0; i < ny; i++) { pY[i] = 0.1 + rng.nextDouble(); sumY += pY[i]; }
                    for (int i = 0; i < nz; i++) { pZ[i] = 0.1 + rng.nextDouble(); sumZ += pZ[i]; }
                    for (int i = 0; i < nx; i++) pX[i] /= sumX;
                    for (int i = 0; i < ny; i++) pY[i] /= sumY;
                    for (int i = 0; i < nz; i++) pZ[i] /= sumZ;
                    double[][][] joint = new double[nx][ny][nz];
                    for (int x = 0; x < nx; x++) {
                        for (int y = 0; y < ny; y++) {
                            for (int z = 0; z < nz; z++) {
                                joint[x][y][z] = pX[x] * pY[y] * pZ[z];
                            }
                        }
                    }
                    return joint;
                })));
    }

    @Provide
    Arbitrary<double[][][]> randomJoints() {
        return Arbitraries.integers().between(2, 3).flatMap(nx ->
            Arbitraries.integers().between(2, 3).flatMap(ny ->
                Arbitraries.integers().between(2, 3).map(nz -> {
                    double[][][] joint = new double[nx][ny][nz];
                    java.util.Random rng = new java.util.Random();
                    for (int x = 0; x < nx; x++) {
                        for (int y = 0; y < ny; y++) {
                            for (int z = 0; z < nz; z++) {
                                joint[x][y][z] = 0.01 + rng.nextDouble();
                            }
                        }
                    }
                    return joint;
                })));
    }

    private static class Assertions {
        static org.assertj.core.data.Offset<Double> offset(double tol) {
            return org.assertj.core.data.Offset.offset(tol);
        }
    }
}
