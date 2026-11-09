package io.matrix.safety;

import io.matrix.contract.Plugin;
import io.matrix.plugin.CreatorMode;
import io.matrix.plugin.DeclarativeSandbox;
import io.matrix.plugin.PluginArtifact;
import io.matrix.plugin.PluginAudit;
import io.matrix.plugin.PluginAuditRecord;
import io.matrix.plugin.PluginRegistry;
import io.matrix.plugin.PluginSandbox;
import io.matrix.plugin.PluginValidator;
import io.matrix.plugin.SandboxResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W51 §11 — the plugin machinery under failure: an artefact that does not work, a programme
 * that never finishes, a damaged artefact and a capability that was never granted.
 *
 * <p><b>What this file is for.</b> §11 asks what happens when a plugin misbehaves. The
 * answers it looks for are all the same shape: the plugin does NOT reach ACTIVE, the reason
 * is written down, and nothing that the plugin could reach is left behind. A plugin system
 * that can only report success is not a plugin system; it is a way of running code.</p>
 *
 * <p><b>Why the clock is a parameter everywhere.</b> Every stage change, every audit record
 * and every creation attempt takes its instant from the caller. A test that let the plugin
 * machinery read the clock would be testing the clock.</p>
 */
class PluginSafetyTest {

    /** The instant every lifecycle step and every audit record in this file carries. */
    private static final String AT = "2026-11-08T00:00:00Z";

    /** The capability the plugin under test declares. */
    private static final String CAPABILITY = "threshold.compare";

    /** The number of evaluation steps the sandbox allows in the tests that need a bound. */
    private static final int MAX_STEPS = 50;

    /** The time the sandbox is given in the tests that need a bound. */
    private static final long MAX_WALL_CLOCK_MILLIS = 1_000L;

    /** The depth of the nested programme that never finishes. */
    private static final int NEVER_ENDING_DEPTH = 200;

    /** The number of declared cases of the plugin under test. */
    private static final int DECLARED_CASE_COUNT = 1;

    /** The number of cases that fail in the validation that is supposed to fail. */
    private static final long FAILING_CASE_COUNT = 1L;

    /** A digest that belongs to no artefact. */
    private static final String FOREIGN_DIGEST = "sha256:0000000000000000000000000000000000000000000000000000000000000000";

    // ── §11.2 — the plugin fails at initialisation ────────────────────────────

    @Test
    @DisplayName("§11.2 — a plugin whose programme does not satisfy its declared cases ends FAILED and never ACTIVE")
    void aPluginThatDoesNotSatisfyItsCasesEndsFailedAndNeverActive() {
        PluginAudit audit = new PluginAudit();
        PluginRegistry registry = new PluginRegistry(audit);
        CreatorMode creator = new CreatorMode(new DeclarativeSandbox());

        // The gap expects FALSE for a score that is above the threshold: the generated
        // programme is right and the evidence is wrong, so the validation must fail.
        CreatorMode.CapabilityGap gap = gap("plugin-failing", Map.of("score", 0.9),
                Map.of("above", false));

        CreatorMode.CreationResult result = creator.create(gap, registry, List.of(CAPABILITY), AT);

        assertThat(result.finalStage()).isEqualTo(Plugin.Stage.FAILED);
        assertThat(result.validation().passed()).isFalse();
        assertThat(result.validation().failures()).isEqualTo(FAILING_CASE_COUNT);
        assertThat(registry.stageOf(gap.id())).isEqualTo(Plugin.Stage.FAILED);
        assertThat(registry.serving())
                .as("a plugin that failed its validation must never serve")
                .isEmpty();

        // The refusal is in the audit, with the reason it happened.
        List<PluginAuditRecord> records = audit.recordsOf(gap.id());
        assertThat(records).isNotEmpty();
        assertThat(records).anyMatch(record -> record.reason() != null
                && record.reason().contains("did not pass"));
    }

    @Test
    @DisplayName("§11.2 — a gap the generator cannot build ends FAILED with the reason named")
    void aGapTheGeneratorCannotBuildEndsFailedWithTheReasonNamed() {
        PluginAudit audit = new PluginAudit();
        PluginRegistry registry = new PluginRegistry(audit);
        CreatorMode creator = new CreatorMode(new DeclarativeSandbox());

        // No feature path: the gap does not name the field the decision is about, so the
        // creator has nothing to build.
        CreatorMode.CapabilityGap gap = new CreatorMode.CapabilityGap("plugin-unbuildable",
                CAPABILITY, "decides a threshold", "a score", "a decision", "", 0.5, true,
                "above", List.of(CAPABILITY), List.of(), "the gap was noticed");

        CreatorMode.CreationResult result = creator.create(gap, registry, List.of(CAPABILITY), AT);

        assertThat(result.finalStage()).isEqualTo(Plugin.Stage.FAILED);
        assertThat(result.reason()).contains("outside the generator's family");
        assertThat(registry.stageOf(gap.id())).isEqualTo(Plugin.Stage.FAILED);
        assertThat(registry.serving()).isEmpty();
    }

    // ── §11.3 — the plugin stops answering ───────────────────────────────────

    @Test
    @DisplayName("§11.3 — a programme that never finishes is stopped by the step budget and reported as FAILED")
    void aProgrammeThatNeverFinishesIsStoppedByTheStepBudget() {
        DeclarativeSandbox sandbox = new DeclarativeSandbox(MAX_STEPS);
        PluginArtifact artifact = new PluginArtifact("plugin-hanging", 1,
                neverEndingProgram(NEVER_ENDING_DEPTH), List.of(), Plugin.Isolation.DECLARATIVE);

        SandboxResult result = sandbox.run(artifact, Map.of(),
                new PluginSandbox.Limits(MAX_STEPS, MAX_WALL_CLOCK_MILLIS));

        assertThat(result.status())
                .as("a programme that never finishes must not report success")
                .isEqualTo(SandboxResult.Status.FAILED);
        assertThat(result.error()).contains("budget");
        assertThat(result.steps()).isGreaterThan(MAX_STEPS);
    }

    // ── §11.9 — a damaged artefact ────────────────────────────────────────────

    @Test
    @DisplayName("§11.9 — a programme that is not valid JSON is refused and nothing is run")
    void aProgrammeThatIsNotValidJsonIsRefusedAndNothingIsRun() {
        DeclarativeSandbox sandbox = new DeclarativeSandbox(MAX_STEPS);
        PluginArtifact artifact = new PluginArtifact("plugin-broken", 1,
                "{ this is not json", List.of(), Plugin.Isolation.DECLARATIVE);

        SandboxResult result = sandbox.run(artifact, Map.of(),
                new PluginSandbox.Limits(MAX_STEPS, MAX_WALL_CLOCK_MILLIS));

        assertThat(result.status()).isEqualTo(SandboxResult.Status.REFUSED);
        assertThat(result.error()).contains("not valid JSON");
        assertThat(result.steps())
                .as("a refused programme must not have been evaluated at all")
                .isZero();
    }

    @Test
    @DisplayName("§11.9 — an artefact with a foreign digest is refused and is never run")
    void anArtefactWithAForeignDigestIsRefusedAndIsNeverRun() {
        CountingSandbox sandbox = new CountingSandbox(new DeclarativeSandbox(MAX_STEPS));
        PluginValidator validator = new PluginValidator(sandbox);

        PluginArtifact artefact = new PluginArtifact("plugin-foreign", 1,
                "{\"steps\":[],\"output\":{}}", List.of(declaredCase()), Plugin.Isolation.DECLARATIVE);
        Plugin manifest = manifest("plugin-foreign", FOREIGN_DIGEST, artefact.isolation());

        PluginValidator.ValidationResult result = validator.validate(manifest, artefact,
                new PluginSandbox.Limits(MAX_STEPS, MAX_WALL_CLOCK_MILLIS));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("digest");
        assertThat(sandbox.runs())
                .as("the artefact must be refused before it is run")
                .isZero();
    }

    @Test
    @DisplayName("§11.9 — an artefact that declares another isolation is refused and is never run")
    void anArtefactThatDeclaresAnotherIsolationIsRefusedAndIsNeverRun() {
        CountingSandbox sandbox = new CountingSandbox(new DeclarativeSandbox(MAX_STEPS));
        PluginValidator validator = new PluginValidator(sandbox);

        PluginArtifact artefact = new PluginArtifact("plugin-isolation", 1,
                "{\"steps\":[],\"output\":{}}", List.of(declaredCase()), Plugin.Isolation.PROCESS);
        Plugin manifest = manifest("plugin-isolation", null, Plugin.Isolation.DECLARATIVE);

        PluginValidator.ValidationResult result = validator.validate(manifest, artefact,
                new PluginSandbox.Limits(MAX_STEPS, MAX_WALL_CLOCK_MILLIS));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("isolation");
        assertThat(sandbox.runs())
                .as("the artefact must be refused before it is run")
                .isZero();
    }

    // ── §11.10 — an operation outside the granted capabilities ─────────────────

    @Test
    @DisplayName("§11.10 — a plugin that asks for a capability it was not granted loads anyway and says so")
    void aPluginThatAsksForAnUngrantedCapabilityLoadsAnywayAndSaysSo() {
        PluginAudit audit = new PluginAudit();
        PluginRegistry registry = new PluginRegistry(audit);

        Plugin declared = manifest("plugin-ungranted", null, Plugin.Isolation.DECLARATIVE)
                .granting(List.of());

        assertThat(declared.requestsUngranted())
                .as("the plugin must be able to say that it asks for something it does not hold")
                .isTrue();
        assertThat(declared.holds(CAPABILITY)).isFalse();

        // The LOAD is not the refusal point: a plugin that declares more than it holds is
        // registered, and the refusal belongs to the call that would use the capability.
        Plugin registered = registry.register(declared, AT);

        assertThat(registered.id()).isEqualTo("plugin-ungranted");
        assertThat(registry.stageOf("plugin-ungranted")).isEqualTo(Plugin.Stage.PROPOSED);
        assertThat(registry.plugin("plugin-ungranted")).isNotNull();
    }

    @Test
    @DisplayName("§11.10 — the runtime refuses to activate a plugin whose grant does not cover its declaration")
    void theRuntimeRefusesToActivateAPluginWhoseGrantDoesNotCoverItsDeclaration() {
        PluginAudit audit = new PluginAudit();
        PluginRegistry registry = new PluginRegistry(audit);
        CreatorMode creator = new CreatorMode(new DeclarativeSandbox());

        CreatorMode.CapabilityGap gap = gap("plugin-short-grant", Map.of("score", 0.9),
                Map.of("above", true));

        // The runtime grants nothing; the plugin declares one capability.
        CreatorMode.CreationResult result = creator.create(gap, registry, List.of(), AT);

        assertThat(result.finalStage()).isEqualTo(Plugin.Stage.FAILED);
        assertThat(result.reason()).contains(CAPABILITY);
        assertThat(registry.serving()).isEmpty();
        assertThat(registry.plugin("plugin-short-grant").requestsUngranted()).isTrue();
    }

    // ── the fixtures ──────────────────────────────────────────────────────────

    private static CreatorMode.CapabilityGap gap(String id, Map<String, Object> input,
                                                 Map<String, Object> expected) {
        return new CreatorMode.CapabilityGap(id, CAPABILITY, "decides a threshold",
                "a score", "a decision", "score", 0.5, true, "above", List.of(CAPABILITY),
                List.of(new PluginArtifact.Case("the declared case", input, expected)),
                "the gap was noticed");
    }

    private static PluginArtifact.Case declaredCase() {
        return new PluginArtifact.Case("the declared case", Map.of("score", 0.9),
                Map.of("above", true));
    }

    private static Plugin manifest(String id, String artifactDigest, Plugin.Isolation isolation) {
        return new Plugin(id, 1, id, "decides a threshold", "a score", "a decision",
                List.of(CAPABILITY), List.of(), List.of(), isolation,
                new Plugin.Budget(0, 0L, 0L), artifactDigest, Plugin.Stage.PROPOSED, List.of(),
                null, "the registry revokes it and removes its registrations");
    }

    /**
     * A programme that never finishes.
     *
     * <p>The declarative language has no loop, so the only way to write a programme that does
     * not terminate is to nest the conditional deeper than the budget allows. Every level
     * costs one evaluation step, so the budget is what stops it — which is exactly the
     * property the test is about.</p>
     */
    private static String neverEndingProgram(int depth) {
        StringBuilder program = new StringBuilder();
        program.append("{\"steps\":[");
        for (int level = 0; level < depth; level++) {
            program.append("{\"op\":\"if\",\"cond\":{\"op\":\"const\",\"value\":true},\"then\":");
        }
        program.append("{\"op\":\"const\",\"value\":true}");
        for (int level = 0; level < depth; level++) {
            program.append('}');
        }
        program.append("],\"output\":{}}");
        return program.toString();
    }

    /** A sandbox that counts how many times it was asked to run something. */
    private static final class CountingSandbox implements PluginSandbox {

        private final PluginSandbox delegate;
        private final AtomicInteger runs = new AtomicInteger();

        CountingSandbox(PluginSandbox delegate) {
            this.delegate = delegate;
        }

        int runs() {
            return runs.get();
        }

        @Override
        public SandboxResult run(PluginArtifact artifact, Map<String, Object> input, Limits limits) {
            runs.incrementAndGet();
            return delegate.run(artifact, input, limits);
        }

        @Override
        public Plugin.Isolation isolation() {
            return delegate.isolation();
        }
    }
}
