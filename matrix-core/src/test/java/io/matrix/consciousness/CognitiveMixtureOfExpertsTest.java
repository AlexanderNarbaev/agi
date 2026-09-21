package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveMixtureOfExpertsTest {

    @Test
    void nullProfileReturnsEmpty() {
        CognitiveMixtureOfExperts.MoEResult r =
            CognitiveMixtureOfExperts.route(null, new ArrayList<>(), 2, 1L);
        assertThat(r.selectedExperts()).isEmpty();
    }

    @Test
    void emptyExpertsReturnsEmpty() {
        CognitiveMixtureOfExperts.MoEResult r =
            CognitiveMixtureOfExperts.route(makeProfile(0.5), new ArrayList<>(), 2, 1L);
        assertThat(r.selectedExperts()).isEmpty();
    }

    @Test
    void routeReturnsValidSelection() {
        List<CognitiveMixtureOfExperts.Expert> experts = new ArrayList<>();
        for (int i = 0; i < 8; i++) experts.add(new CognitiveMixtureOfExperts.Expert("expert" + i));
        CognitiveMixtureOfExperts.MoEResult r =
            CognitiveMixtureOfExperts.route(makeProfile(0.5), experts, 2, 1L);
        assertThat(r.selectedExperts().length).isEqualTo(2);
        assertThat(r.weights().length).isEqualTo(2);
    }

    @Test
    void weightsSumToOne() {
        List<CognitiveMixtureOfExperts.Expert> experts = new ArrayList<>();
        for (int i = 0; i < 8; i++) experts.add(new CognitiveMixtureOfExperts.Expert("expert" + i));
        CognitiveMixtureOfExperts.MoEResult r =
            CognitiveMixtureOfExperts.route(makeProfile(0.5), experts, 3, 1L);
        double sum = 0;
        for (double w : r.weights()) sum += w;
        assertThat(sum).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void routingEntropyBounded() {
        double[] w = {0.5, 0.5};
        assertThat(CognitiveMixtureOfExperts.routingEntropy(w)).isCloseTo(1.0, offset(1e-9));
        double[] w2 = {1.0, 0.0};
        assertThat(CognitiveMixtureOfExperts.routingEntropy(w2)).isEqualTo(0.0);
    }

    @Test
    void classifyConcentrated() {
        assertThat(CognitiveMixtureOfExperts.classifyRouting(new double[]{1.0, 0.0, 0.0}))
            .isEqualTo("CONCENTRATED");
    }

    @Test
    void classifyUniform() {
        assertThat(CognitiveMixtureOfExperts.classifyRouting(new double[]{0.33, 0.33, 0.33}))
            .isEqualTo("UNIFORM");
    }

    @Test
    void sameSeedDeterministic() {
        List<CognitiveMixtureOfExperts.Expert> experts = new ArrayList<>();
        for (int i = 0; i < 8; i++) experts.add(new CognitiveMixtureOfExperts.Expert("e" + i));
        CognitiveMixtureOfExperts.MoEResult r1 =
            CognitiveMixtureOfExperts.route(makeProfile(0.5), experts, 3, 42L);
        CognitiveMixtureOfExperts.MoEResult r2 =
            CognitiveMixtureOfExperts.route(makeProfile(0.5), experts, 3, 42L);
        for (int i = 0; i < r1.selectedExperts().length; i++) {
            assertThat(r1.selectedExperts()[i]).isEqualTo(r2.selectedExperts()[i]);
            assertThat(r1.weights()[i]).isCloseTo(r2.weights()[i], offset(1e-9));
        }
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
