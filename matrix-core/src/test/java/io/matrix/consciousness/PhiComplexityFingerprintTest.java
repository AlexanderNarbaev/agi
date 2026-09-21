package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PhiComplexityFingerprintTest {

    @Test
    void fingerprintOfNullReturnsZeros() {
        double[] fp = PhiComplexityFingerprint.fingerprint(null);
        for (double v : fp) assertThat(v).isEqualTo(0.0);
    }

    @Test
    void fingerprintHasFourComponents() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        double[] fp = PhiComplexityFingerprint.fingerprint(p);
        assertThat(fp.length).isEqualTo(4);
    }

    @Test
    void fingerprintBoundedByOne() {
        CognitiveGenesisProfile p = makeProfile(0.7);
        double[] fp = PhiComplexityFingerprint.fingerprint(p);
        for (double v : fp) {
            assertThat(v).isBetween(0.0, 1.0);
        }
    }

    @Test
    void identicalFingerprintsHaveZeroDistance() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        double[] fp1 = PhiComplexityFingerprint.fingerprint(p);
        double[] fp2 = PhiComplexityFingerprint.fingerprint(p);
        double d = PhiComplexityFingerprint.fingerprintDistance(fp1, fp2);
        assertThat(d).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void sequenceFingerprintForEmptyReturnsZeros() {
        double[] fp = PhiComplexityFingerprint.sequenceFingerprint(new ArrayList<>());
        for (double v : fp) assertThat(v).isEqualTo(0.0);
    }

    @Test
    void sequenceFingerprintLengthFour() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        double[] fp = PhiComplexityFingerprint.sequenceFingerprint(profiles);
        assertThat(fp.length).isEqualTo(4);
    }

    @Test
    void sequenceFingerprintBounded() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) profiles.add(makeProfile(i / 10.0));
        double[] fp = PhiComplexityFingerprint.sequenceFingerprint(profiles);
        for (double v : fp) {
            assertThat(v).isBetween(0.0, 1.5);
        }
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
