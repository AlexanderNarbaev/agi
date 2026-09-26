package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W2 #7 — TrueDistillationFactory promoted (endpoint wiring).
 *
 * <p>Full ONNX pipeline (Distiller.synthesize -> BirRegistry merge)
 * lands in W5. This commit wires the endpoint + factory.</p>
 */
class TrueDistillationFactoryWiringTest {

    @Test
    void gateway_has_distillationFactory_field() throws Exception {
        var f = MinimalHttpServer.class.getDeclaredField("distillationFactory");
        f.setAccessible(true);
        assertThat(f.getType().getName())
            .isEqualTo("io.matrix.brain.runtime.TrueDistillationFactory");
    }

    @Test
    void true_distillation_factory_class_present() throws Exception {
        Class<?> c = Class.forName("io.matrix.brain.runtime.TrueDistillationFactory");
        assertThat(c).isNotNull();
        assertThat(c.getDeclaredConstructors().length).isGreaterThan(0);
    }

    @Test
    void distill_custom_method_signature() throws Exception {
        Class<?> c = Class.forName("io.matrix.brain.runtime.TrueDistillationFactory");
        var method = c.getDeclaredMethod("distillCustom",
            String.class, java.util.List.class,
            java.util.function.Function.class,
            Class.forName("io.matrix.brain.runtime.DistillationLedger"),
            Class.forName("io.matrix.brain.runtime.DiskBudget"));
        assertThat(method).isNotNull();
        assertThat(method.getReturnType().getName())
            .isEqualTo("io.matrix.brain.runtime.TrueDistillationFactory$Result");
    }

    @Test
    void synthetic_activation_helper_exists() throws Exception {
        Class<?> c = Class.forName("io.matrix.brain.runtime.TrueDistillationFactory");
        var method = c.getDeclaredMethod("syntheticActivation", long[].class);
        assertThat(method).isNotNull();
        assertThat(method.getReturnType()).isEqualTo(float[].class);
    }

    @Test
    void distill_endpoint_registered_in_router() {
        // Verify /v1/distill is registered as a context in the gateway's HTTP server.
        // (Verified at runtime when start() is called; here we just check the handler method exists.)
        try {
            var m = MinimalHttpServer.class.getDeclaredMethod("handleDistill",
                com.sun.net.httpserver.HttpExchange.class);
            assertThat(m).isNotNull();
        } catch (NoSuchMethodException e) {
            org.junit.jupiter.api.Assertions.fail("handleDistill method missing");
        }
    }
}
