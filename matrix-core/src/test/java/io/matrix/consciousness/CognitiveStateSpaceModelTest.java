package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveStateSpaceModelTest {

    @Test
    void constructValid() {
        CognitiveStateSpaceModel ssm = new CognitiveStateSpaceModel(32, 16, 42L);
        assertThat(ssm.dim()).isEqualTo(32);
        assertThat(ssm.stateDim()).isEqualTo(16);
        assertThat(ssm.seed()).isEqualTo(42L);
    }

    @Test
    void invalidDimsThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveStateSpaceModel(0, 16, 1L)
        );
    }

    @Test
    void stepPreservesDimensions() {
        CognitiveStateSpaceModel ssm = new CognitiveStateSpaceModel(32, 16, 42L);
        double[] x = new double[32];
        for (int i = 0; i < 32; i++) x[i] = i / 32.0;
        double[][] B = new double[16][32];
        double[] C = new double[16];
        double[] result = ssm.step(x, B, C);
        assertThat(result.length).isEqualTo(32);
    }

    @Test
    void nullStepReturnsNull() {
        CognitiveStateSpaceModel ssm = new CognitiveStateSpaceModel(32, 16, 42L);
        assertThat(ssm.step(null, new double[16][32], new double[16])).isNull();
    }

    @Test
    void wrongDimReturnsInput() {
        CognitiveStateSpaceModel ssm = new CognitiveStateSpaceModel(32, 16, 42L);
        double[] x = new double[16];
        double[][] B = new double[16][32];
        double[] C = new double[16];
        assertThat(ssm.step(x, B, C)).isSameAs(x);
    }

    @Test
    void resetClearsHiddenState() {
        CognitiveStateSpaceModel ssm = new CognitiveStateSpaceModel(32, 16, 42L);
        double[] x = new double[32];
        for (int i = 0; i < 32; i++) x[i] = 1.0;
        double[][] B = new double[16][32];
        double[] C = new double[16];
        for (int i = 0; i < 16; i++) B[i][0] = 1.0;
        ssm.step(x, B, C);
        ssm.reset();
        double[] state = ssm.hiddenState();
        for (double v : state) assertThat(v).isEqualTo(0.0);
    }

    @Test
    void hiddenStateAfterStep() {
        CognitiveStateSpaceModel ssm = new CognitiveStateSpaceModel(32, 16, 42L);
        double[] x = new double[32];
        for (int i = 0; i < 32; i++) x[i] = 1.0;
        double[][] B = new double[16][32];
        double[] C = new double[16];
        for (int i = 0; i < 16; i++) B[i][0] = 1.0;
        ssm.step(x, B, C);
        double[] state = ssm.hiddenState();
        // State should be non-zero after step
        double sum = 0;
        for (double v : state) sum += Math.abs(v);
        assertThat(sum).isGreaterThan(0.0);
    }

    @Test
    void processSequenceReturnsCorrectCount() {
        CognitiveStateSpaceModel ssm = new CognitiveStateSpaceModel(32, 16, 42L);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        double[][] result = ssm.processSequence(profiles);
        assertThat(result.length).isEqualTo(5);
        for (double[] r : result) assertThat(r.length).isEqualTo(32);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
