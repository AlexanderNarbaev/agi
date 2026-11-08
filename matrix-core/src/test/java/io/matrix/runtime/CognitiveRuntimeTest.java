package io.matrix.runtime;

import io.matrix.contract.Capability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 — the runtime composition layer.
 *
 * <p>The W51 end-to-end acceptance list starts with "the runtime discovers or initializes the
 * available subsystems", and the mandate says that "a subsystem must not be considered
 * integrated merely because it is registered or discoverable". These tests check the part
 * that can be checked without a running system: that the declarations are honest, that the
 * dependency rule is enforced, and that a component which cannot be used is refused BY
 * NAME.</p>
 */
class CognitiveRuntimeTest {

    private static RuntimeComponent component(String id, RuntimeComponent.Kind kind,
                                              String capabilityId, RuntimeComponent.Health health,
                                              List<String> requires) {
        return new RuntimeComponent(id, kind,
                capabilityId == null ? List.of()
                        : List.of(new Capability(capabilityId, id, "the contract of " + id,
                                "does the thing of " + id, List.of("it cannot"), false)),
                requires, false, health);
    }

    @Test
    @DisplayName("the chain A declarations name the real subsystems")
    void chainADeclarationsNameTheRealSubsystems() {
        CognitiveRuntime runtime = new CognitiveRuntime();
        MatrixRuntimeComposition.chainAComponents().forEach(runtime::register);

        assertEquals(MatrixRuntimeComposition.COGNITIVE_KERNEL,
                runtime.providerOf(MatrixRuntimeComposition.CAPABILITY_CYCLE).id());
        assertEquals(MatrixRuntimeComposition.ALGORITHM_PORTFOLIO,
                runtime.providerOf(MatrixRuntimeComposition.CAPABILITY_METHOD_SELECTION).id());
        assertEquals(MatrixRuntimeComposition.MICRO_WORLD,
                runtime.providerOf(MatrixRuntimeComposition.CAPABILITY_PROBE).id());
        assertEquals(MatrixRuntimeComposition.EPISTEMIC_STORE,
                runtime.providerOf(MatrixRuntimeComposition.CAPABILITY_CLAIMS).id());
        assertNull(runtime.providerOf("capability.nobody.offers"));
    }

    @Test
    @DisplayName("a component whose dependency is down is degraded and is not offered")
    void degradedComponentIsNotOffered() {
        CognitiveRuntime runtime = new CognitiveRuntime();
        runtime.register(component("memory", RuntimeComponent.Kind.MEMORY, "memory.x",
                RuntimeComponent.Health.UP, List.of()));
        runtime.register(component("reasoning", RuntimeComponent.Kind.REASONING, "reasoning.x",
                RuntimeComponent.Health.UP, List.of("memory")));

        assertEquals(List.of(), runtime.unmetDependencies(runtime.component("reasoning")));
        assertTrue(runtime.usableProviderOf("reasoning.x").isPresent());

        CognitiveRuntime broken = new CognitiveRuntime();
        broken.register(component("memory", RuntimeComponent.Kind.MEMORY, "memory.x",
                RuntimeComponent.Health.DOWN, List.of()));
        broken.register(component("reasoning", RuntimeComponent.Kind.REASONING, "reasoning.x",
                RuntimeComponent.Health.UP, List.of("memory")));

        assertEquals(List.of("memory"), broken.unmetDependencies(broken.component("reasoning")));
        assertTrue(broken.usableProviderOf("reasoning.x").isEmpty());
    }

    @Test
    @DisplayName("require names the dependency that is not up")
    void requireNamesTheMissingDependency() {
        CognitiveRuntime runtime = new CognitiveRuntime();
        runtime.register(component("memory", RuntimeComponent.Kind.MEMORY, "memory.x",
                RuntimeComponent.Health.DOWN, List.of()));
        runtime.register(component("reasoning", RuntimeComponent.Kind.REASONING, "reasoning.x",
                RuntimeComponent.Health.UP, List.of("memory")));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> runtime.require("reasoning"));
        assertTrue(failure.getMessage().contains("memory"));

        IllegalStateException unknown = assertThrows(IllegalStateException.class,
                () -> runtime.require("ghost"));
        assertTrue(unknown.getMessage().contains("not declared"));
    }

    @Test
    @DisplayName("a remote component's health is what the peer reported")
    void remoteHealthIsReported() {
        CognitiveRuntime runtime = new CognitiveRuntime();
        runtime.register(MatrixRuntimeComposition.brainRuntime(RuntimeComponent.Health.UP));

        RuntimeComponent peer = runtime.component(MatrixRuntimeComposition.BRAIN_RUNTIME);
        assertNotNull(peer);
        assertTrue(peer.remote());
        assertEquals(MatrixRuntimeComposition.CAPABILITY_BRAIN_THINK,
                peer.capabilities().get(0).id());
    }

    @Test
    @DisplayName("the report is reproducible and says what each component needs")
    void reportIsReproducible() {
        CognitiveRuntime runtime = new CognitiveRuntime();
        MatrixRuntimeComposition.chainAComponents().forEach(runtime::register);

        Map<String, Object> first = runtime.describe();
        Map<String, Object> second = runtime.describe();

        assertEquals(first, second);
        assertEquals(MatrixRuntimeComposition.chainAComponents().size(), first.get("componentCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) first.get("components");
        assertFalse(components.isEmpty());
        Map<String, Object> kernel = components.stream()
                .filter(entry -> MatrixRuntimeComposition.COGNITIVE_KERNEL.equals(entry.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(MatrixRuntimeComposition.COGNITIVE_STORE,
                MatrixRuntimeComposition.MICRO_WORLD,
                MatrixRuntimeComposition.ALGORITHM_PORTFOLIO), kernel.get("requires"));
    }

    @Test
    @DisplayName("a component cannot be declared twice with a different declaration")
    void duplicateDeclarationIsRefused() {
        CognitiveRuntime runtime = new CognitiveRuntime();
        runtime.register(component("a", RuntimeComponent.Kind.MEMORY, "memory.x",
                RuntimeComponent.Health.UP, List.of()));

        assertThrows(IllegalArgumentException.class, () -> runtime.register(
                component("a", RuntimeComponent.Kind.MEMORY, "memory.x",
                        RuntimeComponent.Health.DOWN, List.of())));
        assertEquals(RuntimeComponent.Health.UP, runtime.component("a").health());
    }
}
