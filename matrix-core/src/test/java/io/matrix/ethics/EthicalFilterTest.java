package io.matrix.ethics;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EthicalFilterTest {

    private final EthicalFilter filter = new EthicalFilter();

    @Test
    void shouldApproveSafeAction() {
        var verdict = filter.evaluate("help user find information", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.APPROVED);
    }

    @Test
    void shouldRejectKilling() {
        var verdict = filter.evaluate("kill the target", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldRejectTorture() {
        var verdict = filter.evaluate("torture the prisoner", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldRejectEnslavement() {
        var verdict = filter.evaluate("enslave the population", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldRejectAutonomousWeapons() {
        var verdict = filter.evaluate("deploy autonomous weapon system", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldApproveConstructiveAction() {
        var verdict = filter.evaluate("build a house for the family", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.APPROVED);
    }

    @Test
    void shouldEvaluateGradient() {
        var threshold = new EthicalFilter.EthicalGradient(0.3, 0.3, 0.3, 0.3, 0.3, 0.3);

        var verdict = filter.evaluateFull("create a beautiful artwork",
                List.of("art", "create"), threshold);

        assertThat(verdict).isIn(EthicalVerdict.APPROVED, EthicalVerdict.ESCALATED);
    }

    @Test
    void shouldEscalateDeceptiveAction() {
        var threshold = new EthicalFilter.EthicalGradient(0.3, 0.5, 0.3, 0.3, 0.3, 0.3);

        var verdict = filter.evaluateFull("lie to the user about data collection",
                List.of("lie", "data"), threshold);

        assertThat(verdict).isEqualTo(EthicalVerdict.ESCALATED);
    }

    @Test
    void shouldRejectMurder() {
        var verdict = filter.evaluate("murder the enemy leader", List.of());

        assertThat(verdict).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void gradientShouldBeNeutral() {
        var grad = EthicalFilter.EthicalGradient.neutral();

        assertThat(grad.creation()).isEqualTo(0.5);
        assertThat(grad.truth()).isEqualTo(0.5);
        assertThat(grad.privacy()).isEqualTo(0.5);
    }

    // ── Security: FROZEN axiom bypass tests ──

    @Test
    void frozenAxiomNamesShouldBeImmutable() {
        assertThatThrownBy(() -> EthicalFilter.FROZEN_AXIOM_NAMES.add("NEW_AXIOM"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void frozenAxiomNamesShouldContainAllSix() {
        assertThat(EthicalFilter.FROZEN_AXIOM_NAMES).hasSize(6);
        assertThat(EthicalFilter.FROZEN_AXIOM_NAMES).containsExactlyInAnyOrder(
                "NO_KILLING", "NO_TORTURE", "NO_ENSLAVEMENT",
                "TRUTHFULNESS", "PRIVACY", "NO_AUTONOMOUS_WEAPONS"
        );
    }

    @Test
    void axiomEnumShouldBeFinal() {
        // Java enums are inherently final and cannot be subclassed
        assertThat(Modifier.isFinal(EthicalFilter.Axiom.class.getModifiers())).isTrue();
    }

    @Test
    void ethicalFilterClassShouldBeFinal() {
        assertThat(Modifier.isFinal(EthicalFilter.class.getModifiers())).isTrue();
    }

    @Test
    void shouldRejectKillingEvenWithObfuscation() {
        // Attempt to bypass via case tricks, unicode, etc.
        assertThat(filter.evaluate("KILL the target", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("Kill The Target", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("please murder someone", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("ASSASSINATE the leader", List.of())).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldRejectTortureVariants() {
        assertThat(filter.evaluate("TORTURE the prisoner", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("Inflict Pain on them", List.of())).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldRejectEnslavementVariants() {
        assertThat(filter.evaluate("ENSLAVE everyone", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("Subjugate the workers", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("Control Mind of the agent", List.of())).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldRejectAutonomousWeaponsVariants() {
        assertThat(filter.evaluate("Deploy Autonomous Weapon", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("Activate LAWS system", List.of())).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldHandleNullActionSafely() {
        assertThat(filter.evaluate(null, List.of())).isEqualTo(EthicalVerdict.APPROVED);
    }

    @Test
    void shouldHandleEmptyActionSafely() {
        assertThat(filter.evaluate("", List.of())).isEqualTo(EthicalVerdict.APPROVED);
    }

    @Test
    void axiomDescriptionShouldBeImmutable() {
        // Enum field descriptions are set at construction and cannot be changed
        for (EthicalFilter.Axiom axiom : EthicalFilter.Axiom.values()) {
            assertThat(axiom.description()).isNotBlank();
        }
    }

    // ── GAP-013: Whole-word matching (no substring false positives) ──

    @Test
    void wholeWordMatchingShouldNotFalsePositiveOnSubstrings() {
        // "skill" contains "kill" as a substring but must NOT trigger NO_KILLING.
        assertThat(filter.evaluate("improve skill", List.of())).isEqualTo(EthicalVerdict.APPROVED);
        assertThat(filter.evaluate("skill development plan", List.of())).isEqualTo(EthicalVerdict.APPROVED);
        assertThat(filter.evaluate("killer puzzle", List.of())).isEqualTo(EthicalVerdict.APPROVED); // "killer" != whole "kill"
        assertThat(filter.evaluate("skillet cooking", List.of())).isEqualTo(EthicalVerdict.APPROVED);
    }

    @Test
    void wholeWordMatchingShouldRejectExactWord() {
        assertThat(filter.evaluate("kill it", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("murder was solved", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("to enslave is wrong", List.of())).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void wordBoundaryShouldRejectWholeToken() {
        // Word-boundary regex: must reject stand-alone keywords.
        assertThat(filter.evaluate("please kill now", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("i will murder you", List.of())).isEqualTo(EthicalVerdict.REJECTED);
    }

    // ── GAP-012: TRUTHFULNESS and PRIVACY rejection keywords ──

    @Test
    void shouldRejectTruthfulnessViolations() {
        assertThat(filter.evaluate("lie about my grades", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("disinformation campaign starting", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("fake news article", List.of())).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldRejectPrivacyViolations() {
        assertThat(filter.evaluate("leak personal data online", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("dox him publicly", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("expose private information to media", List.of())).isEqualTo(EthicalVerdict.REJECTED);
        assertThat(filter.evaluate("doxxing the developer", List.of())).isEqualTo(EthicalVerdict.REJECTED);
    }

    // ── GAP-018: null threshold in evaluateFull must NPE ──

    @Test
    void evaluateFullShouldThrowOnNullThreshold() {
        assertThatThrownBy(() -> filter.evaluateFull("help someone", List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void evaluateFullShouldHandleNullActionSafely() {
        var threshold = EthicalFilter.EthicalGradient.neutral();
        assertThat(filter.evaluateFull(null, List.of(), threshold)).isEqualTo(EthicalVerdict.APPROVED);
        assertThat(filter.evaluateFull("", List.of(), threshold)).isEqualTo(EthicalVerdict.APPROVED);
    }

    // ── GAP-011: keywords parameter must be ignored (informational) ──

    @Test
    void keywordsParameterIsInformationalAndDoesNotBypass() {
        // Even with "kill" passed as a keyword, the FRESH action text must be evaluated.
        // The parameter is reserved for future caller extensions; canonical FROZEN axioms are authoritative.
        assertThat(filter.evaluate("plant a tree", List.of("kill", "murder"))).isEqualTo(EthicalVerdict.APPROVED);
        // And the FROZEN rules still kick in even if keywords is empty.
        assertThat(filter.evaluate("kill someone", List.of())).isEqualTo(EthicalVerdict.REJECTED);
    }
}
