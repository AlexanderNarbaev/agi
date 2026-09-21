package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveConstitutionalAITest {

    @Test
    void nullProfileReturnsEmpty() {
        CognitiveConstitutionalAI.ConstitutionalResult r =
            CognitiveConstitutionalAI.evaluate(null,
                CognitiveConstitutionalAI.DEFAULT_CONSTITUTION, 0.5);
        assertThat(r.critiques()).isEmpty();
    }

    @Test
    void emptyConstitutionReturnsEmpty() {
        CognitiveConstitutionalAI.ConstitutionalResult r =
            CognitiveConstitutionalAI.evaluate(makeProfile(0.5),
                new ArrayList<>(), 0.5);
        assertThat(r.critiques()).isEmpty();
    }

    @Test
    void defaultConstitutionEvaluates() {
        CognitiveConstitutionalAI.ConstitutionalResult r =
            CognitiveConstitutionalAI.evaluate(makeProfile(0.5),
                CognitiveConstitutionalAI.DEFAULT_CONSTITUTION, 0.5);
        assertThat(r.critiques().size()).isEqualTo(4);
    }

    @Test
    void averageScoreBounded() {
        CognitiveConstitutionalAI.ConstitutionalResult r =
            CognitiveConstitutionalAI.evaluate(makeProfile(0.5),
                CognitiveConstitutionalAI.DEFAULT_CONSTITUTION, 0.5);
        if (!Double.isNaN(r.averageScore())) {
            assertThat(r.averageScore()).isBetween(0.0, 1.0 + 1e-9);
        }
    }

    @Test
    void passedIfAllPrinciplesAboveThreshold() {
        // Profile with high scores for all principles
        CognitiveConstitutionalAI.ConstitutionalResult r =
            CognitiveConstitutionalAI.evaluate(makeProfile(1.0),
                CognitiveConstitutionalAI.DEFAULT_CONSTITUTION, 0.3);
        assertThat(r.passed()).isTrue();
        assertThat(r.violatedPrinciples()).isEmpty();
    }

    @Test
    void failedIfSomePrinciplesBelowThreshold() {
        CognitiveConstitutionalAI.ConstitutionalResult r =
            CognitiveConstitutionalAI.evaluate(makeProfile(0.1),
                CognitiveConstitutionalAI.DEFAULT_CONSTITUTION, 0.5);
        assertThat(r.passed()).isFalse();
        assertThat(r.violatedPrinciples().size()).isGreaterThan(0);
    }

    @Test
    void builtInPrinciplesExist() {
        assertThat(CognitiveConstitutionalAI.HIGH_PHI.name()).isEqualTo("HIGH_PHI");
        assertThat(CognitiveConstitutionalAI.STABILITY.name()).isEqualTo("STABILITY");
        assertThat(CognitiveConstitutionalAI.ANALOGY.name()).isEqualTo("ANALOGY");
        assertThat(CognitiveConstitutionalAI.NO_EXCLUSION.name()).isEqualTo("NO_EXCLUSION");
    }

    @Test
    void reviseReturnsValidProfile() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveConstitutionalAI.Critique c =
            new CognitiveConstitutionalAI.Critique("test", 0.3, "feedback");
        CognitiveGenesisProfile revised = CognitiveConstitutionalAI.revise(p, c, 42L);
        assertThat(revised).isNotNull();
    }

    @Test
    void reviseNullProfileReturnsNull() {
        CognitiveConstitutionalAI.Critique c =
            new CognitiveConstitutionalAI.Critique("test", 0.3, "feedback");
        assertThat(CognitiveConstitutionalAI.revise(null, c, 1L)).isNull();
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
