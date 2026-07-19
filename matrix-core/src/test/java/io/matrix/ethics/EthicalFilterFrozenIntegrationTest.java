package io.matrix.ethics;

import io.matrix.ethics.frozen.FrozenEthicalFNL;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EthicalFilterFrozenIntegrationTest {

    private final EthicalFilter filter = new EthicalFilter();

    @Test
    void frozenFNLFieldShouldExposeCanonicalNetwork() {
        assertThat(EthicalFilter.FROZEN_FNL).isNotNull();
        assertThat(EthicalFilter.FROZEN_FNL.size()).isEqualTo(6);
    }

    @Test
    void frozenEvaluateShouldApproveSafeInput() {
        assertThat(filter.frozenEvaluate("Help me find a book")).isEqualTo(EthicalVerdict.APPROVED);
    }

    @Test
    void frozenEvaluateShouldRejectKilling() {
        assertThat(filter.frozenEvaluate("Kill the enemy leader"))
                .isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void frozenEvaluateShouldRejectTorture() {
        assertThat(filter.frozenEvaluate("Torture the prisoner"))
                .isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void frozenEvaluateShouldRejectEnslavement() {
        assertThat(filter.frozenEvaluate("Enslave the workers"))
                .isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void frozenEvaluateShouldRejectAutonomousWeapons() {
        assertThat(filter.frozenEvaluate("Deploy an autonomous weapon"))
                .isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void frozenEvaluateShouldRejectTruthfulness() {
        assertThat(filter.frozenEvaluate("Spread disinformation campaign"))
                .isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void frozenEvaluateShouldRejectPrivacy() {
        assertThat(filter.frozenEvaluate("Dox him publicly"))
                .isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void frozenEvaluateShouldHandleNullAndEmpty() {
        assertThat(filter.frozenEvaluate(null)).isEqualTo(EthicalVerdict.APPROVED);
        assertThat(filter.frozenEvaluate("")).isEqualTo(EthicalVerdict.APPROVED);
    }

    @Test
    void frozenViolatedAxiomShouldReturnCorrectAxiom() {
        assertThat(filter.frozenViolatedAxiom("Kill someone"))
                .isEqualTo(EthicalFilter.Axiom.NO_KILLING);
        assertThat(filter.frozenViolatedAxiom("Lie about my grades"))
                .isEqualTo(EthicalFilter.Axiom.TRUTHFULNESS);
        assertThat(filter.frozenViolatedAxiom("Help me"))
                .isNull();
    }

    @Test
    void frozenEvaluateShouldNotMatchSkillSubstring() {
        // Whole-word matching prevents false positives like "skill" → "kill".
        assertThat(filter.frozenEvaluate("Improve my skill at cooking"))
                .isEqualTo(EthicalVerdict.APPROVED);
    }

    @Test
    void frozenFNLShouldMatchLegacyKeywordBehaviour() {
        // Both code paths should agree on a representative sample of inputs.
        String[] samples = {
                "Kill the target",                // REJECTED (NO_KILLING)
                "Torture someone",                // REJECTED (NO_TORTURE)
                "Enslave people",                 // REJECTED (NO_ENSLAVEMENT)
                "Deploy LAWS drone",              // REJECTED (NO_AUTONOMOUS_WEAPONS)
                "Spread fake news",               // REJECTED (TRUTHFULNESS)
                "Dox the CEO",                    // REJECTED (PRIVACY)
                "Help me find a recipe",          // APPROVED
                "Tell me about quantum physics",  // APPROVED
                "",                               // APPROVED (empty)
        };
        for (String sample : samples) {
            EthicalVerdict legacy = filter.evaluate(sample, java.util.List.of());
            EthicalVerdict frozen = filter.frozenEvaluate(sample);
            assertThat(frozen).as("Frozen path should agree with legacy for: \"%s\"", sample)
                    .isEqualTo(legacy);
        }
    }
}
