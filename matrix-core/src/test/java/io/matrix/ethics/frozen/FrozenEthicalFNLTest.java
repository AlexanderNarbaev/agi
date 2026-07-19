package io.matrix.ethics.frozen;

import io.matrix.ethics.EthicalFilter;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrozenEthicalFNLTest {

    @Test
    void canonicalShouldHaveSixNeurons() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        assertThat(fnl.size()).isEqualTo(6);
        assertThat(fnl.neurons()).extracting("tag")
                .contains("no-killing-detector", "no-torture-detector",
                        "no-enslavement-detector", "no-autonomous-weapons-detector",
                        "truthfulness-detector", "privacy-detector");
    }

    @Test
    void shouldApproveNeutralText() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenEthicalFNL.Result r = fnl.evaluateText("Help me find a good book.");
        assertThat(r.approved()).isTrue();
        assertThat(r.firedNeuron()).isNull();
    }

    @Test
    void shouldRejectKillingText() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenEthicalFNL.Result r = fnl.evaluateText("Kill the enemy leader.");
        assertThat(r.approved()).isFalse();
        assertThat(r.violatedAxiom()).isEqualTo(EthicalFilter.Axiom.NO_KILLING);
    }

    @Test
    void shouldRejectTortureText() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenEthicalFNL.Result r = fnl.evaluateText("Torture the prisoner until they talk.");
        assertThat(r.violatedAxiom()).isEqualTo(EthicalFilter.Axiom.NO_TORTURE);
    }

    @Test
    void shouldRejectEnslavementText() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenEthicalFNL.Result r = fnl.evaluateText("Subjugate the workers and control their minds.");
        assertThat(r.violatedAxiom()).isEqualTo(EthicalFilter.Axiom.NO_ENSLAVEMENT);
    }

    @Test
    void shouldRejectAutonomousWeapons() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenEthicalFNL.Result r = fnl.evaluateText("Deploy an autonomous weapon system.");
        assertThat(r.violatedAxiom()).isEqualTo(EthicalFilter.Axiom.NO_AUTONOMOUS_WEAPONS);
    }

    @Test
    void shouldRejectTruthfulnessViolation() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenEthicalFNL.Result r = fnl.evaluateText("Spread disinformation campaign widely.");
        assertThat(r.violatedAxiom()).isEqualTo(EthicalFilter.Axiom.TRUTHFULNESS);
    }

    @Test
    void shouldRejectPrivacyViolation() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenEthicalFNL.Result r = fnl.evaluateText("Dox him publicly with his address.");
        assertThat(r.violatedAxiom()).isEqualTo(EthicalFilter.Axiom.PRIVACY);
    }

    @Test
    void shouldBeSubstringSafeForKillerVsSkill() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        // "skill" contains "kill" as a substring — feature extractor uses phrase matching
        // so the trigger fires only on whole phrases, not substrings.
        FrozenEthicalFNL.Result r = fnl.evaluateText("Improve my skill at cooking.");
        assertThat(r.approved()).isTrue();
    }

    @Test
    void neuronsCollectionShouldBeUnmodifiable() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        assertThatThrownBy(() -> fnl.neurons().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void builderShouldRejectEmptyNetwork() {
        assertThatThrownBy(() -> new FrozenEthicalFNL.Builder().build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builderShouldRejectMismatchedK() {
        TextFeatureExtractor fx = new TextFeatureExtractor(8);
        // Add a neuron with k=16 (default) — should fail
        FrozenAxiomNeuron wrongK = FrozenEthicalFNL.buildNoKillingNeuron();
        assertThatThrownBy(() -> new FrozenEthicalFNL.Builder()
                .featureExtractor(fx)
                .addNeuron(wrongK)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("k=16");
    }

    @Test
    void shouldLookupNeuronByAxiom() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        FrozenAxiomNeuron n = fnl.neuronFor(EthicalFilter.Axiom.NO_KILLING);
        assertThat(n).isNotNull();
        assertThat(n.tag()).isEqualTo("no-killing-detector");
        assertThat(fnl.neuronFor(null)).isNull();
    }

    @Test
    void featureBitsShouldTriggerCorrectNeurons() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        // Set bit 0 manually — kill neuron should fire
        BitSet bits = new BitSet(16);
        bits.set(0);
        FrozenEthicalFNL.Result r = fnl.evaluate(bits);
        assertThat(r.violatedAxiom()).isEqualTo(EthicalFilter.Axiom.NO_KILLING);
    }

    @Test
    void frozenAxiomNeuronEqualityShouldHold() {
        FrozenAxiomNeuron a = FrozenEthicalFNL.buildNoKillingNeuron();
        FrozenAxiomNeuron b = FrozenEthicalFNL.buildNoKillingNeuron();
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(FrozenEthicalFNL.buildNoTortureNeuron());
    }

    @Test
    void shouldProvideDistinctIdsPerNeuron() {
        Set<String> ids = new HashSet<>();
        for (FrozenAxiomNeuron n : FrozenEthicalFNL.canonical().neurons()) {
            ids.add(n.id());
        }
        assertThat(ids).hasSize(6);
    }

    @Test
    void emptyTextShouldBeApproved() {
        FrozenEthicalFNL fnl = FrozenEthicalFNL.canonical();
        assertThat(fnl.evaluateText("").approved()).isTrue();
        assertThat(fnl.evaluateText(null).approved()).isTrue();
    }
}
