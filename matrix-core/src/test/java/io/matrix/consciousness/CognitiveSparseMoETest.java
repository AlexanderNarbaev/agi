package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveSparseMoETest {

    @Test
    void emptyReturnsEmpty() {
        CognitiveSparseMoE.SparseMoEResult r =
            CognitiveSparseMoE.route(new ArrayList<>(), new ArrayList<>(), 2, 1L);
        assertThat(r.selectedExperts()).isEmpty();
    }

    @Test
    void nullProfilesReturnsEmpty() {
        CognitiveSparseMoE.SparseMoEResult r =
            CognitiveSparseMoE.route(null, new ArrayList<>(), 2, 1L);
        assertThat(r.selectedExperts()).isEmpty();
    }

    @Test
    void invalidKReturnsEmpty() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        CognitiveSparseMoE.SparseMoEResult r =
            CognitiveSparseMoE.route(profiles, new ArrayList<>(), 0, 1L);
        assertThat(r.selectedExperts()).isEmpty();
    }

    @Test
    void routeReturnsValid() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        List<String> experts = new ArrayList<>();
        for (int i = 0; i < 8; i++) experts.add("e" + i);
        CognitiveSparseMoE.SparseMoEResult r =
            CognitiveSparseMoE.route(profiles, experts, 2, 1L);
        assertThat(r.selectedExperts().length).isEqualTo(2);
    }

    @Test
    void weightsSumToOne() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        List<String> experts = new ArrayList<>();
        for (int i = 0; i < 8; i++) experts.add("e" + i);
        CognitiveSparseMoE.SparseMoEResult r =
            CognitiveSparseMoE.route(profiles, experts, 3, 1L);
        double sum = 0;
        for (double w : r.weights()) sum += w;
        assertThat(sum).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void loadBalancingLossBounded() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        List<String> experts = new ArrayList<>();
        for (int i = 0; i < 8; i++) experts.add("e" + i);
        CognitiveSparseMoE.SparseMoEResult r =
            CognitiveSparseMoE.route(profiles, experts, 2, 1L);
        assertThat(r.loadBalancingLoss()).isBetween(0.0, 8.0);
    }

    @Test
    void expertUtilizationBounded() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        List<String> experts = new ArrayList<>();
        for (int i = 0; i < 8; i++) experts.add("e" + i);
        CognitiveSparseMoE.SparseMoEResult r =
            CognitiveSparseMoE.route(profiles, experts, 2, 1L);
        double util = CognitiveSparseMoE.expertUtilization(r, 8);
        assertThat(util).isBetween(0.0, 1.0 + 1e-9);
    }

    @Test
    void sameSeedDeterministic() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        List<String> experts = new ArrayList<>();
        for (int i = 0; i < 8; i++) experts.add("e" + i);
        CognitiveSparseMoE.SparseMoEResult r1 =
            CognitiveSparseMoE.route(profiles, experts, 3, 42L);
        CognitiveSparseMoE.SparseMoEResult r2 =
            CognitiveSparseMoE.route(profiles, experts, 3, 42L);
        for (int i = 0; i < r1.selectedExperts().length; i++) {
            assertThat(r1.selectedExperts()[i]).isEqualTo(r2.selectedExperts()[i]);
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
