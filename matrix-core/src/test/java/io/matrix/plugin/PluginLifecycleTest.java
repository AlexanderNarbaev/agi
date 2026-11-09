package io.matrix.plugin;

import io.matrix.contract.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 — the plugin lifecycle, the sandbox and creator mode.
 *
 * <p>These tests are the proof of the W51 section 8 requirements: that a plugin can be
 * created, validated, activated and revoked; that a revoked plugin leaves nothing behind;
 * that the sandbox is a boundary and not a namespace; and that the creator refuses the gaps
 * it cannot honestly fill.</p>
 */
class PluginLifecycleTest {

    private static final String AT = "2026-11-08T00:00:00Z";

    private static Plugin manifest(Plugin.Stage stage, List<String> declared, List<String> granted) {
        return new Plugin("p-test", 1, "the test plugin", "does a thing", "in", "out", declared,
                granted, List.of(), Plugin.Isolation.DECLARATIVE, new Plugin.Budget(4, 100L, 1024L),
                null, stage, List.of(), null, "the registry revokes it");
    }

    @Test
    @DisplayName("the lifecycle is the chain the mandate names, and no step can be skipped")
    void lifecycleIsTheMandatedChain() {
        assertTrue(PluginLifecycle.canTransition(Plugin.Stage.PROPOSED, Plugin.Stage.BUILDING));
        assertTrue(PluginLifecycle.canTransition(Plugin.Stage.BUILDING, Plugin.Stage.SANDBOXED));
        assertTrue(PluginLifecycle.canTransition(Plugin.Stage.SANDBOXED, Plugin.Stage.VALIDATED));
        assertTrue(PluginLifecycle.canTransition(Plugin.Stage.VALIDATED, Plugin.Stage.SHADOW));
        assertTrue(PluginLifecycle.canTransition(Plugin.Stage.SHADOW, Plugin.Stage.ACTIVE));

        assertFalse(PluginLifecycle.canTransition(Plugin.Stage.PROPOSED, Plugin.Stage.ACTIVE));
        assertFalse(PluginLifecycle.canTransition(Plugin.Stage.PROPOSED, Plugin.Stage.VALIDATED));
        assertFalse(PluginLifecycle.canTransition(Plugin.Stage.BUILDING, Plugin.Stage.ACTIVE));
        assertFalse(PluginLifecycle.canTransition(Plugin.Stage.ACTIVE, Plugin.Stage.SHADOW));
    }

    @Test
    @DisplayName("a revoked plugin cannot come back")
    void revokedIsTerminal() {
        assertTrue(PluginLifecycle.legalMoves(Plugin.Stage.REVOKED).isEmpty());
        assertTrue(PluginLifecycle.legalMoves(Plugin.Stage.RETIRED).isEmpty());
        assertNotNull(PluginLifecycle.refusal(Plugin.Stage.REVOKED, Plugin.Stage.ACTIVE));
        assertEquals(null, PluginLifecycle.refusal(Plugin.Stage.ACTIVE, Plugin.Stage.REVOKED));
    }

    @Test
    @DisplayName("an illegal transition is refused and the refusal is audited")
    void illegalTransitionIsRefusedAndAudited() {
        PluginAudit audit = new PluginAudit();
        PluginRegistry registry = new PluginRegistry(audit);
        registry.register(manifest(Plugin.Stage.PROPOSED, List.of(), List.of()), AT);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> registry.transition("p-test", Plugin.Stage.ACTIVE, "skipping ahead", AT));

        assertTrue(failure.getMessage().contains("not legal"));
        assertEquals(Plugin.Stage.PROPOSED, registry.stageOf("p-test"));
        assertEquals(PluginAuditRecord.OUTCOME_REFUSED, audit.records().get(1).outcome());
    }

    @Test
    @DisplayName("unloading a plugin leaves no service and no handler behind")
    void unloadingLeavesNothingBehind() {
        PluginAudit audit = new PluginAudit();
        PluginRegistry registry = new PluginRegistry(audit);
        registry.register(manifest(Plugin.Stage.PROPOSED, List.of(), List.of()), AT);
        registry.services().registerService("p-test", "the-service", "the-service-value");
        registry.services().registerHandler("p-test", "observation", event -> event);

        assertEquals(1, registry.services().serviceCount());
        assertEquals(1, registry.services().handlerCount());

        PluginServiceRegistry.Removal removal = registry.unload("p-test", "the test ends", AT);

        assertEquals(List.of("the-service"), removal.services());
        assertEquals(List.of("observation"), removal.handlers());
        assertEquals(0, registry.services().serviceCount());
        assertEquals(0, registry.services().handlerCount());
        assertEquals(Plugin.Stage.REVOKED, registry.stageOf("p-test"));
        assertTrue(registry.serving().isEmpty());
    }

    @Test
    @DisplayName("a plugin that is not registered cannot register anything")
    void unknownPluginCannotRegister() {
        PluginRegistry registry = new PluginRegistry(new PluginAudit());
        assertThrows(IllegalArgumentException.class,
                () -> registry.services().registerService("ghost", "the-service", "value"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.services().registerHandler("ghost", "observation", event -> event));
    }

    @Test
    @DisplayName("the audit is append-only and keeps the refusals")
    void auditKeepsEverything() {
        PluginAudit audit = new PluginAudit();
        audit.record("p-1", "REGISTER", PluginAuditRecord.OUTCOME_APPLIED, "registered", AT);
        audit.record("p-1", "TRANSITION", PluginAuditRecord.OUTCOME_REFUSED, "not legal", AT);

        assertEquals(2, audit.size());
        assertEquals(2, audit.recordsOf("p-1").size());
        assertThrows(UnsupportedOperationException.class,
                () -> audit.records().add(new PluginAuditRecord("p-2", "X", "Y", "Z", AT)));
    }

    @Test
    @DisplayName("the sandbox computes what the program says")
    void sandboxComputes() {
        DeclarativeSandbox sandbox = new DeclarativeSandbox();
        String program = """
                {
                  "steps": [
                    {"op":"let","name":"n","value":{"op":"get","path":"count"}},
                    {"op":"if",
                     "cond":{"op":"gte","args":[{"op":"var","name":"n"},{"op":"const","value":10}]},
                     "then":{"op":"let","name":"label","value":{"op":"const","value":"many"}},
                     "else":{"op":"let","name":"label","value":{"op":"const","value":"few"}}}
                  ],
                  "output": {"label":"label","doubled":{"op":"mul",
                              "args":[{"op":"var","name":"n"},{"op":"const","value":2}]}}
                }
                """;
        PluginArtifact artifact = new PluginArtifact("p-test", 1, program, List.of(),
                Plugin.Isolation.DECLARATIVE);

        SandboxResult many = sandbox.run(artifact, Map.of("count", 12),
                new PluginSandbox.Limits(1000, 1000L));
        assertTrue(many.succeeded());
        assertEquals("many", many.output().get("label"));
        assertEquals(24.0, many.output().get("doubled"));

        SandboxResult few = sandbox.run(artifact, Map.of("count", 3),
                new PluginSandbox.Limits(1000, 1000L));
        assertEquals("few", few.output().get("label"));
    }

    @Test
    @DisplayName("the sandbox stops a program that exceeds its budget")
    void sandboxStopsRunawayPrograms() {
        DeclarativeSandbox sandbox = new DeclarativeSandbox(5);
        String program = """
                {
                  "steps": [
                    {"op":"let","name":"a","value":{"op":"add",
                      "args":[{"op":"const","value":1},{"op":"const","value":1}]}},
                    {"op":"let","name":"b","value":{"op":"add",
                      "args":[{"op":"const","value":2},{"op":"const","value":2}]}}
                  ],
                  "output": {"a":"a","b":"b"}
                }
                """;
        PluginArtifact artifact = new PluginArtifact("p-test", 1, program, List.of(),
                Plugin.Isolation.DECLARATIVE);

        SandboxResult result = sandbox.run(artifact, Map.of(),
                new PluginSandbox.Limits(1000, 1000L));

        assertFalse(result.succeeded());
        assertEquals(SandboxResult.Status.FAILED, result.status());
        assertTrue(result.error().contains("budget"));
    }

    @Test
    @DisplayName("a malformed program is refused, not run")
    void malformedProgramIsRefused() {
        DeclarativeSandbox sandbox = new DeclarativeSandbox();
        PluginArtifact artifact = new PluginArtifact("p-test", 1, "{not json", List.of(),
                Plugin.Isolation.DECLARATIVE);

        SandboxResult result = sandbox.run(artifact, Map.of(),
                new PluginSandbox.Limits(1000, 1000L));

        assertEquals(SandboxResult.Status.REFUSED, result.status());
        assertFalse(result.succeeded());
    }

    @Test
    @DisplayName("the sandbox refuses an artefact that declares another isolation")
    void sandboxRefusesAnotherIsolation() {
        DeclarativeSandbox sandbox = new DeclarativeSandbox();
        PluginArtifact artifact = new PluginArtifact("p-test", 1, "{}", List.of(),
                Plugin.Isolation.PROCESS);

        SandboxResult result = sandbox.run(artifact, Map.of(),
                new PluginSandbox.Limits(1000, 1000L));

        assertEquals(SandboxResult.Status.REFUSED, result.status());
        assertTrue(result.error().contains("isolation"));
    }

    @Test
    @DisplayName("the sandbox's own package names no dangerous package")
    void sandboxNamesNoDangerousPackage() throws Exception {
        Path packageDirectory = Path.of("src/main/java/io/matrix/plugin");
        List<String> forbidden = List.of("java.io", "java.net", "java.nio.file", "java.nio.channels",
                "java.lang.reflect", "java.lang.ProcessBuilder", "java.lang.Runtime",
                "java.sql");
        try (Stream<Path> files = Files.list(packageDirectory)) {
            List<Path> sources = files.filter(path -> path.toString().endsWith(".java")).toList();
            assertFalse(sources.isEmpty(), "the package directory must exist");
            for (Path source : sources) {
                String text = Files.readString(source, StandardCharsets.UTF_8);
                for (String banned : forbidden) {
                    assertFalse(text.contains("import " + banned),
                            source.getFileName() + " must not import " + banned);
                    assertFalse(text.contains(banned + "."),
                            source.getFileName() + " must not use " + banned);
                }
            }
        }
    }

    @Test
    @DisplayName("creator mode walks the whole lifecycle and activates a real plugin")
    void creatorModeWalksTheLifecycle() {
        PluginAudit audit = new PluginAudit();
        PluginRegistry registry = new PluginRegistry(audit);
        CreatorMode creator = new CreatorMode(new DeclarativeSandbox());

        CreatorMode.CapabilityGap gap = new CreatorMode.CapabilityGap(
                "gap-1", "reasoning.threshold", "decides whether a score is high enough",
                "a map with the field score", "a map with the field verdict",
                "score", 0.75, true, "verdict", List.of("read.score"),
                List.of(
                        new PluginArtifact.Case("above", Map.of("score", 0.9),
                                Map.of("verdict", true)),
                        new PluginArtifact.Case("below", Map.of("score", 0.1),
                                Map.of("verdict", false))),
                "the selector had no rule for a score of 0.8");

        CreatorMode.CreationResult result = creator.create(gap, registry,
                List.of("read.score"), AT);

        assertEquals(Plugin.Stage.ACTIVE, result.finalStage());
        assertNotNull(result.artifact());
        assertTrue(result.validation().passed());
        assertEquals(2, result.validation().outcomes().size());
        assertEquals(Plugin.Stage.ACTIVE, registry.stageOf("gap-1"));
        assertTrue(registry.plugin("gap-1").holds("read.score"));

        List<String> stages = audit.recordsOf("gap-1").stream()
                .filter(r -> "TRANSITION".equals(r.action()))
                .map(r -> r.reason())
                .toList();
        assertTrue(stages.size() >= 5, "the audit must hold the whole chain, got " + stages);
    }

    @Test
    @DisplayName("creator mode refuses a gap it cannot honestly fill")
    void creatorModeRefusesUnknownGaps() {
        PluginRegistry registry = new PluginRegistry(new PluginAudit());
        CreatorMode creator = new CreatorMode(new DeclarativeSandbox());

        CreatorMode.CapabilityGap gap = new CreatorMode.CapabilityGap(
                "gap-2", "reasoning.something", "does something the creator cannot build",
                "in", "out", null, 0.0, true, "out", List.of(), List.of(), "no evidence");

        CreatorMode.CreationResult result = creator.create(gap, registry, List.of(), AT);

        assertEquals(Plugin.Stage.FAILED, result.finalStage());
        assertTrue(result.reason().contains("family"));
        assertNull(result.artifact());
    }

    @Test
    @DisplayName("a plugin that fails its declared cases is not activated")
    void failingPluginIsNotActivated() {
        PluginRegistry registry = new PluginRegistry(new PluginAudit());
        CreatorMode creator = new CreatorMode(new DeclarativeSandbox());

        CreatorMode.CapabilityGap gap = new CreatorMode.CapabilityGap(
                "gap-3", "reasoning.threshold", "decides whether a score is high enough",
                "a map with the field score", "a map with the field verdict",
                "score", 0.75, true, "verdict", List.of(),
                List.of(new PluginArtifact.Case("wrong", Map.of("score", 0.9),
                        Map.of("verdict", "yes"))),
                "the selector had no rule");

        CreatorMode.CreationResult result = creator.create(gap, registry, List.of(), AT);

        assertEquals(Plugin.Stage.FAILED, result.finalStage());
        assertFalse(result.validation().passed());
        assertEquals(1, result.validation().failures());
        assertEquals(Plugin.Stage.FAILED, registry.stageOf("gap-3"));
    }

    @Test
    @DisplayName("a plugin is not activated when the runtime does not grant what it declared")
    void ungrantedPluginIsNotActivated() {
        PluginRegistry registry = new PluginRegistry(new PluginAudit());
        CreatorMode creator = new CreatorMode(new DeclarativeSandbox());

        CreatorMode.CapabilityGap gap = new CreatorMode.CapabilityGap(
                "gap-4", "reasoning.threshold", "decides whether a score is high enough",
                "a map with the field score", "a map with the field verdict",
                "score", 0.75, true, "verdict", List.of("read.score", "write.verdict"),
                List.of(new PluginArtifact.Case("above", Map.of("score", 0.9),
                        Map.of("verdict", true))),
                "the selector had no rule");

        CreatorMode.CreationResult result = creator.create(gap, registry,
                List.of("read.score"), AT);

        assertEquals(Plugin.Stage.FAILED, result.finalStage());
        assertTrue(result.reason().contains("granted"));
    }

    @Test
    @DisplayName("a validator refuses an artefact whose digest does not match the manifest")
    void validatorRefusesADifferentArtefact() {
        PluginValidator validator = new PluginValidator(new DeclarativeSandbox());
        PluginArtifact artifact = new PluginArtifact("p-test", 1, "{\"steps\":[]}", List.of(
                new PluginArtifact.Case("c", Map.of(), Map.of())), Plugin.Isolation.DECLARATIVE);
        Plugin manifest = new Plugin("p-test", 1, "the test plugin", "does a thing", "in", "out",
                List.of(), List.of(), List.of(), Plugin.Isolation.DECLARATIVE,
                new Plugin.Budget(4, 100L, 1024L), "sha256:0000", Plugin.Stage.BUILDING,
                List.of(), null, "the registry revokes it");

        PluginValidator.ValidationResult result = validator.validate(manifest, artifact);

        assertFalse(result.passed());
        assertTrue(result.reason().contains("digest"));
    }

    private static void assertNull(Object value) {
        org.junit.jupiter.api.Assertions.assertNull(value);
    }
}
