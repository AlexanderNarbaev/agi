package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveMixtureOfDepthsTest {

    @Test
    void emptyReturnsEmpty() {
        List<CognitiveMixtureOfDepths.DepthDecision> r =
            CognitiveMixtureOfDepths.routeDepth(new ArrayList<>(), 50.0, 4, 1L);
        assertThat(r).isEmpty();
    }

    @Test
    void nullReturnsEmpty() {
        List<CognitiveMixtureOfDepths.DepthDecision> r =
            CognitiveMixtureOfDepths.routeDepth(null, 50.0, 4, 1L);
        assertThat(r).isEmpty();
    }

    @Test
    void returnsDecisionPerProfile() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) profiles.add(makeProfile(i / 10.0));
        List<CognitiveMixtureOfDepths.DepthDecision> decisions =
            CognitiveMixtureOfDepths.routeDepth(profiles, 50.0, 4, 42L);
        assertThat(decisions.size()).isEqualTo(10);
    }

    @Test
    void decisionsHaveValidDepth() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        List<CognitiveMixtureOfDepths.DepthDecision> decisions =
            CognitiveMixtureOfDepths.routeDepth(profiles, 50.0, 4, 42L);
        for (CognitiveMixtureOfDepths.DepthDecision d : decisions) {
            // depth should be 0 (skip) or maxDepth
            assertThat(d.depth() == 0 || d.depth() == 4).isTrue();
        }
    }

    @Test
    void computeSavingsBounded() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) profiles.add(makeProfile(i / 10.0));
        List<CognitiveMixtureOfDepths.DepthDecision> decisions =
            CognitiveMixtureOfDepths.routeDepth(profiles, 50.0, 4, 42L);
        double savings = CognitiveMixtureOfDepths.computeSavings(decisions);
        assertThat(savings).isBetween(0.0, 1.0);
    }

    @Test
    void topK100NoSkipping() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        List<CognitiveMixtureOfDepths.DepthDecision> decisions =
            CognitiveMixtureOfDepths.routeDepth(profiles, 100.0, 4, 42L);
        // All should have full depth
        for (CognitiveMixtureOfDepths.DepthDecision d : decisions) {
            assertThat(d.depth()).isEqualTo(4);
        }
    }

    @Test
    void topK0AllSkipped() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        List<CognitiveMixtureOfDepths.DepthDecision> decisions =
            CognitiveMixtureOfDepths.routeDepth(profiles, 0.0, 4, 42L);
        // All should be skipped
        for (CognitiveMixtureOfDepths.DepthDecision d : decisions) {
            assertThat(d.skip()).isTrue();
        }
    }

    @Test
    void invalidPercentClamped() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        // Should not throw on invalid percent
        List<CognitiveMixtureOfDepths.DepthDecision> decisions =
            CognitiveMixtureOfDepths.routeDepth(profiles, 200.0, 4, 1L);
        assertThat(decisions).isNotEmpty();
    }

    @Test
    void sameSeedDeterministic() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        List<CognitiveMixtureOfDepths.DepthDecision> r1 =
            CognitiveMixtureOfDepths.routeDepth(profiles, 50.0, 4, 42L);
        List<CognitiveMixtureOfDepths.DepthDecision> r2 =
            CognitiveMixtureOfDepths.routeDepth(profiles, 50.0, 4, 42L);
        for (int i = 0; i < r1.size(); i++) {
            assertThat(r1.get(i).depth()).isEqualTo(r2.get(i).depth());
        }
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
