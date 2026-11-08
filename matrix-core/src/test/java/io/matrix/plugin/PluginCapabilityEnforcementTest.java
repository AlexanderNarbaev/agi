package io.matrix.plugin;

import io.matrix.contract.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 §11 — the two boundaries that a safety audit found to be promised and not kept.
 *
 * <p><b>The wall-clock budget.</b> {@code DeclarativeSandbox} accepted a
 * {@code maxWallClockMillis} and never read it: a stopped plugin was always stopped by the
 * step counter, and the time budget was decoration. These tests hold the runtime to the
 * budget it declares.</p>
 *
 * <p><b>The capability at the call.</b> The grant was checked when a plugin was ACTIVATED
 * and nowhere else, so a narrowing of the grant afterwards — or a plugin that was never
 * activated but was serving — was not enforced at the place where it matters. These tests
 * hold the registry to the grant on EVERY call.</p>
 */
class PluginCapabilityEnforcementTest {

    private static final String AT = "2026-11-08T00:00:00Z";

    /** The capability the test plugin needs to use its service. */
    private static final String CAPABILITY = "knowledge.read";

    private static Plugin manifest(List<String> granted) {
        return new Plugin("p-enforce", 1, "the enforcing plugin", "does a thing", "in", "out",
                List.of(CAPABILITY), granted, List.of(), Plugin.Isolation.DECLARATIVE,
                new Plugin.Budget(4, 100L, 1024L), null, Plugin.Stage.PROPOSED, List.of(), null,
                "the registry revokes it");
    }

    /**
     * Walks a plugin to ACTIVE.
     *
     * <p>The registry forces every registered plugin into PROPOSED — a plugin that was built
     * somewhere else does not arrive ACTIVE — so a test that wants a serving plugin has to
     * walk the same lifecycle the runtime walks.</p>
     *
     * @param registry the registry
     * @param granted  the capabilities the owner grants
     */
    private static void activate(PluginRegistry registry, List<String> granted) {
        registry.register(manifest(granted), AT);
        registry.transition("p-enforce", Plugin.Stage.BUILDING, "the artefact is built", AT);
        registry.transition("p-enforce", Plugin.Stage.SANDBOXED, "the artefact is sandboxed", AT);
        registry.transition("p-enforce", Plugin.Stage.VALIDATED, "the cases passed", AT);
        registry.transition("p-enforce", Plugin.Stage.SHADOW, "it runs beside the real path", AT);
        registry.transition("p-enforce", Plugin.Stage.ACTIVE, "its results are used", AT);
    }

    private static PluginArtifact artefact(String program) {
        return new PluginArtifact("p-enforce", 1, program, List.of(),
                Plugin.Isolation.DECLARATIVE);
    }

    @Test
    @DisplayName("the sandbox stops a programme that exceeds the wall-clock budget it was given")
    void theWallClockBudgetIsKept() {
        DeclarativeSandbox sandbox = new DeclarativeSandbox();

        // A programme of many steps, and a budget of one millisecond. The sandbox must stop
        // it and must say that it stopped it for the TIME, not for the step count.
        StringBuilder steps = new StringBuilder("[");
        for (int i = 0; i < 50_000; i++) {
            if (i > 0) {
                steps.append(',');
            }
            steps.append("{\"op\":\"let\",\"name\":\"x").append(i)
                    .append("\",\"value\":{\"op\":\"const\",\"value\":1}}");
        }
        steps.append(']');
        PluginArtifact artifact = artefact("{\"steps\":" + steps + ",\"output\":{}}");

        SandboxResult result = sandbox.run(artifact, Map.of(),
                new PluginSandbox.Limits(1_000_000, 1L));

        assertEquals(SandboxResult.Status.FAILED, result.status(),
                "the sandbox must stop a programme that overruns its time budget");
        assertTrue(result.error().contains("time budget"),
                "the refusal must name the boundary that was crossed: " + result.error());
    }

    @Test
    @DisplayName("a programme inside its budget still runs")
    void aProgrammeInsideItsBudgetRuns() {
        DeclarativeSandbox sandbox = new DeclarativeSandbox();
        PluginArtifact artifact = artefact(
                "{\"steps\":[{\"op\":\"let\",\"name\":\"x\","
                        + "\"value\":{\"op\":\"const\",\"value\":41}},"
                        + "{\"op\":\"let\",\"name\":\"y\","
                        + "\"value\":{\"op\":\"const\",\"value\":1}}],"
                        + "\"output\":{\"sum\":{\"op\":\"add\",\"args\":["
                        + "{\"op\":\"var\",\"name\":\"x\"},"
                        + "{\"op\":\"var\",\"name\":\"y\"}]}}}");

        SandboxResult result = sandbox.run(artifact, Map.of(),
                new PluginSandbox.Limits(100, 10_000L));

        assertEquals(SandboxResult.Status.OK, result.status(), result.error());
        assertEquals(42.0, ((Number) result.output().get("sum")).doubleValue(), 0.0001);
    }

    @Test
    @DisplayName("a call to a service whose plugin does not hold the capability is refused")
    void aCallWithoutTheCapabilityIsRefused() {
        PluginAudit audit = new PluginAudit();
        PluginRegistry registry = new PluginRegistry(audit);
        // The plugin is ACTIVE and serving, but its grant does NOT cover the capability.
        activate(registry, List.of());
        PluginServiceRegistry services = registry.services();
        services.registerService("p-enforce", "the-service",
                (java.util.function.Function<Object, Object>) service -> "the answer");

        Optional<String> answer = services.invoke("the-service", CAPABILITY,
                service -> (String) ((java.util.function.Function<Object, Object>) service).apply("in"), AT);

        assertTrue(answer.isEmpty(), "the call must be refused when the grant does not cover it");
        boolean refused = audit.recordsOf("p-enforce").stream()
                .anyMatch(record -> PluginAuditRecord.OUTCOME_REFUSED.equals(record.outcome())
                        && record.reason().contains(CAPABILITY));
        assertTrue(refused, "the refusal must be audited and must name the capability");
    }

    @Test
    @DisplayName("a call to a service whose plugin holds the capability goes through")
    void aCallWithTheCapabilityGoesThrough() {
        PluginAudit audit = new PluginAudit();
        PluginRegistry registry = new PluginRegistry(audit);
        activate(registry, List.of(CAPABILITY));
        PluginServiceRegistry services = registry.services();
        services.registerService("p-enforce", "the-service",
                (java.util.function.Function<Object, Object>) service -> "the answer");

        Optional<String> answer = services.invoke("the-service", CAPABILITY,
                service -> (String) ((java.util.function.Function<Object, Object>) service).apply("in"), AT);

        assertTrue(answer.isPresent(), "the call must go through when the grant covers it");
        assertEquals("the answer", answer.get());
    }

    @Test
    @DisplayName("a plugin that does not serve may not be called even with the capability")
    void aPluginThatDoesNotServeMayNotBeCalled() {
        PluginAudit audit = new PluginAudit();
        PluginRegistry registry = new PluginRegistry(audit);
        registry.register(manifest(List.of(CAPABILITY)), AT);
        registry.transition("p-enforce", Plugin.Stage.BUILDING, "the artefact is built", AT);
        registry.transition("p-enforce", Plugin.Stage.SANDBOXED, "the artefact is sandboxed", AT);
        registry.transition("p-enforce", Plugin.Stage.VALIDATED, "the cases passed", AT);
        PluginServiceRegistry services = registry.services();
        services.registerService("p-enforce", "the-service",
                (java.util.function.Function<Object, Object>) service -> "the answer");

        // VALIDATED is not serving: the plugin has not been put beside the real path yet,
        // so a call into it would be a call into something that is not supposed to be
        // reached. SHADOW and ACTIVE both serve; VALIDATED does not.
        Optional<String> answer = services.invoke("the-service", CAPABILITY,
                service -> (String) ((java.util.function.Function<Object, Object>) service).apply("in"), AT);

        assertTrue(answer.isEmpty(), "a plugin that does not serve may not be called");
        boolean refused = audit.recordsOf("p-enforce").stream()
                .anyMatch(record -> PluginAuditRecord.OUTCOME_REFUSED.equals(record.outcome()));
        assertTrue(refused, "the refusal must be audited");
        assertFalse(services.invoke("the-service", CAPABILITY,
                service -> null, AT).isPresent());
    }
}
