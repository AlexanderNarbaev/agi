package io.matrix.api;

import io.matrix.api.brain.ProductionBrainClient;
import io.matrix.brain.runtime.TrueMindCycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W45.2 — the canonical entry point of the brain runtime, on the gateway's own side.
 *
 * <p>The decision names the canonical entry point of each serving application. The naming is
 * only worth anything if it matches the code, so this test checks the chain B side of it in
 * the module that owns it: the hand-written HTTP server is the entry point, and the cycle it
 * reaches is {@code TrueMindCycle} — not the unmounted JAX-RS facade and its stub.</p>
 */
class CanonicalEntryPointChainBTest {

    @Test
    @DisplayName("the declaration names the hand-written server and the TrueMindCycle")
    void theDeclarationNamesTheHandWrittenServerAndTheTrueMindCycle() {
        CanonicalEntryPoint.Declaration brain = CanonicalEntryPoint
                .canonicalOf(CanonicalEntryPoint.BRAIN_RUNTIME_APPLICATION)
                .orElseThrow();

        assertThat(brain.entryPointClass()).isEqualTo(MinimalHttpServer.class.getName());
        assertThat(brain.cycleClasses()).isEqualTo(TrueMindCycle.class.getName());
    }

    @Test
    @DisplayName("the production client really holds a TrueMindCycle")
    void theProductionClientReallyHoldsATrueMindCycle() throws Exception {
        ProductionBrainClient client = new ProductionBrainClient();

        Field field = ProductionBrainClient.class.getDeclaredField("mindCycle");
        field.setAccessible(true);

        assertThat(field.get(client)).isInstanceOf(TrueMindCycle.class);
    }

    @Test
    @DisplayName("the entry point is a main the gateway can be started from")
    void theEntryPointIsAMainTheGatewayCanBeStartedFrom() throws Exception {
        java.lang.reflect.Method main = MinimalHttpServer.class.getMethod("main", String[].class);

        assertThat(java.lang.reflect.Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(java.lang.reflect.Modifier.isPublic(main.getModifiers())).isTrue();
    }
}
