package io.matrix.cognitive.microworld;

import io.matrix.cognitive.MicroWorldCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.4 — the lamp world: what it answers, and what it refuses to say.
 *
 * <p>The rule is known to this test — the test is the specification of the world. It is NOT known
 * to the environment's public surface, and the last test here is the one that says so: a learner
 * that could read the rule out of the environment would not be learning.</p>
 */
class LampEnvironmentTest {

    @Test
    @DisplayName("the world answers the rule for every assignment")
    void theWorldAnswersTheRuleForEveryAssignment() {
        LampEnvironment environment = new LampEnvironment();

        assertThat(environment.probe(WorldInput.of(false, false)).lamp()).isFalse();
        assertThat(environment.probe(WorldInput.of(false, true)).lamp()).isFalse();
        assertThat(environment.probe(WorldInput.of(true, false)).lamp()).isFalse();
        assertThat(environment.probe(WorldInput.of(true, true)).lamp()).isTrue();
    }

    @Test
    @DisplayName("the identifiers of the observations are monotonic and reproducible")
    void theIdentifiersAreMonotonicAndReproducible() {
        LampEnvironment environment = new LampEnvironment();

        Observation first = environment.probe(WorldInput.of(false, true));
        Observation second = environment.probe(WorldInput.of(true, true));

        assertThat(first.id()).isEqualTo("obs-1");
        assertThat(second.id()).isEqualTo("obs-2");

        LampEnvironment other = new LampEnvironment();
        assertThat(other.probe(WorldInput.of(false, true)).id()).isEqualTo("obs-1");
    }

    @Test
    @DisplayName("the adversarial probe contradicts the world and says so")
    void theAdversarialProbeContradictsTheWorld() {
        LampEnvironment environment = new LampEnvironment();

        Observation ordinary = environment.probe(WorldInput.of(true, true));
        Observation adversarial = environment.adversarialProbe(WorldInput.of(true, true));

        assertThat(ordinary.lamp()).isTrue();
        assertThat(ordinary.adversarial()).isFalse();
        assertThat(adversarial.lamp()).isFalse();
        assertThat(adversarial.adversarial()).isTrue();
        assertThat(adversarial.id()).startsWith("obs-adv-");
    }

    @Test
    @DisplayName("the world refuses an assignment it does not have a variable for")
    void theWorldRefusesAnUnknownAssignment() {
        LampEnvironment environment = new LampEnvironment();
        WorldInput unknown = new WorldInput(java.util.Map.of("power", true, "flux", true));

        assertThatThrownBy(() -> environment.probe(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not accept");
    }

    @Test
    @DisplayName("the world reports the instant the caller supplies")
    void theWorldReportsTheCallerSuppliedInstant() {
        LampEnvironment environment = new LampEnvironment(() -> "2026-11-02T00:00:00Z");

        Observation observation = environment.probe(WorldInput.of(true, true));

        assertThat(observation.observedAt()).isEqualTo("2026-11-02T00:00:00Z");
    }

    @Test
    @DisplayName("the world's vocabulary is the lamp world's")
    void theWorldsVocabularyIsDeclared() {
        LampEnvironment environment = new LampEnvironment();

        assertThat(environment.variables()).containsExactlyInAnyOrder("power", "switch");
        assertThat(environment.outcomeVariable()).isEqualTo("lamp");
    }

    @Test
    @DisplayName("THE RULE DOES NOT LEAK: nothing public returns it")
    void theRuleDoesNotLeakThroughThePublicSurface() {
        // No public method may even be NAMED after the rule.
        for (Method method : LampEnvironment.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            String name = method.getName().toLowerCase(Locale.ROOT);
            assertThat(name).doesNotContain("rule");
            assertThat(name).doesNotContain("truth");
        }
        for (Method method : MicroWorldEnvironment.class.getMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            assertThat(name).doesNotContain("rule");
            assertThat(name).doesNotContain("truth");
        }
        for (Field field : LampEnvironment.class.getFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            assertThat(name).doesNotContain("rule");
            assertThat(name).doesNotContain("truth");
        }

        // And the public surface returns nothing that could carry a rule: an observation, the
        // names of the variables, or the name of the outcome variable. Nothing else.
        Set<Class<?>> allowed = Set.of(Observation.class, Set.class, String.class);
        for (Method method : LampEnvironment.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            assertThat(allowed).contains(method.getReturnType());
        }
    }

    @Test
    @DisplayName("the rule is applied by a private method, so even this package cannot read it")
    void theRuleIsAppliedPrivately() {
        boolean found = false;
        for (Method method : LampEnvironment.class.getDeclaredMethods()) {
            if (method.getParameterCount() == 1
                    && method.getParameterTypes()[0].equals(WorldInput.class)
                    && method.getReturnType().equals(boolean.class)) {
                found = true;
                assertThat(Modifier.isPrivate(method.getModifiers()))
                        .as("the rule must not be reachable")
                        .isTrue();
            }
        }
        assertThat(found).as("the world must have a rule to apply").isTrue();
    }

    @Test
    @DisplayName("the assignment key is the format the kernel computes")
    void theAssignmentKeyIsTheFormatTheKernelComputes() {
        WorldInput input = WorldInput.of(false, true);

        assertThat(input.key()).isEqualTo("power=0,switch=1");
        assertThat(input.key()).isEqualTo(MicroWorldCodec.describe(input));
    }

    @Test
    @DisplayName("the assignment is immutable and rejects a nameless one")
    void theAssignmentIsImmutableAndChecked() {
        java.util.Map<String, Boolean> source = new java.util.LinkedHashMap<>();
        source.put("power", true);
        source.put("switch", false);
        WorldInput input = new WorldInput(source);

        source.put("power", false);
        assertThat(input.get("power")).isTrue();

        assertThatThrownBy(() -> input.assignment().put("power", false))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new WorldInput(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorldInput(java.util.Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        java.util.Map<String, Boolean> withNull = new java.util.LinkedHashMap<>();
        withNull.put("power", null);
        assertThatThrownBy(() -> new WorldInput(withNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("power");
        assertThatThrownBy(() -> input.get("flux"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("flux");
    }

    @Test
    @DisplayName("the observation refuses an empty identity and a missing assignment")
    void theObservationIsChecked() {
        WorldInput input = WorldInput.of(true, true);

        assertThatThrownBy(() -> new Observation(" ", input, true, false, "now"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Observation("obs-1", null, true, false, "now"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Observation("obs-1", input, true, false, " "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(new Observation("obs-1", input, true, false, "now").assignmentKey())
                .isEqualTo("power=1,switch=1");
    }

    @Test
    @DisplayName("the known variables are the documented ones")
    void theKnownVariablesAreDocumented() {
        assertThat(WorldInput.KNOWN_VARIABLES).containsExactlyInAnyOrder("power", "switch");
        assertThat(List.copyOf(WorldInput.KNOWN_VARIABLES)).hasSize(2);
    }
}
