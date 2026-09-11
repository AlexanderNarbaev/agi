package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 82 — OnnxModelRegistry unit tests. */
class OnnxModelRegistryTest {

    @Test
    void emptyRegistry() {
        OnnxModelRegistry reg = new OnnxModelRegistry(true);
        assertThat(reg.size()).isZero();
        assertThat(reg.loadedCount()).isZero();
        assertThat(reg.registeredKeys()).isEmpty();
    }

    @Test
    void registerAddsEntry() {
        OnnxModelRegistry reg = new OnnxModelRegistry(true);
        reg.register(OnnxModelRegistry.ModelId.of("qwen", "0.5b", "bf16"),
                Path.of("/tmp/none"));
        assertThat(reg.size()).isEqualTo(1);
        assertThat(reg.registeredKeys()).contains("qwen:0.5b:bf16");
    }

    @Test
    void containsBeforeLoad() {
        OnnxModelRegistry reg = new OnnxModelRegistry(true);
        var id = OnnxModelRegistry.ModelId.of("qwen", "0.5b", "bf16");
        reg.register(id, Path.of("/tmp/none"));
        assertThat(reg.contains(id)).isTrue();
        assertThat(reg.isLoaded(id)).isFalse();
    }

    @Test
    void getUnknownReturnsNull() {
        OnnxModelRegistry reg = new OnnxModelRegistry(true);
        var id = OnnxModelRegistry.ModelId.of("unknown", "?", "?");
        assertThat(reg.get(id)).isNull();
    }

    @Test
    void modelIdKeyFormat() {
        var id1 = OnnxModelRegistry.ModelId.of("qwen", "0.5b", "bf16");
        var id2 = OnnxModelRegistry.ModelId.of("qwen", "0.5b", "bf16");
        var id3 = OnnxModelRegistry.ModelId.of("qwen", "1.5b", "bf16");
        assertThat(id1.key()).isEqualTo(id2.key());
        assertThat(id1.key()).isNotEqualTo(id3.key());
    }

    @Test
    void modelIdComponents() {
        var id = OnnxModelRegistry.ModelId.of("qwen", "0.5b", "bf16");
        assertThat(id.name()).isEqualTo("qwen");
        assertThat(id.size()).isEqualTo("0.5b");
        assertThat(id.precision()).isEqualTo("bf16");
    }

    @Test
    void customLoaderIsInvokedLazily() {
        OnnxModelRegistry reg = new OnnxModelRegistry(true);
        AtomicInteger loadCount = new AtomicInteger();
        var id = OnnxModelRegistry.ModelId.of("qwen", "0.5b", "bf16");
        reg.register(id, useGpu -> {
            loadCount.incrementAndGet();
            return null;  // simulate load failure
        });
        // Not loaded yet
        assertThat(loadCount.get()).isZero();
        // First get triggers load
        reg.get(id);
        assertThat(loadCount.get()).isEqualTo(1);
        // Second get doesn't trigger load
        reg.get(id);
        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    void closeAllResetsBridgeRefs() {
        OnnxModelRegistry reg = new OnnxModelRegistry(true);
        reg.register(OnnxModelRegistry.ModelId.of("qwen", "0.5b", "bf16"),
                Path.of("/tmp/none"));
        reg.closeAll();
        assertThat(reg.loadedCount()).isZero();
        assertThat(reg.size()).isEqualTo(1);  // registrations still present
    }

    @Test
    void multipleRegistrations() {
        OnnxModelRegistry reg = new OnnxModelRegistry(true);
        reg.register(OnnxModelRegistry.ModelId.of("qwen", "0.5b", "bf16"),
                Path.of("/tmp/none"));
        reg.register(OnnxModelRegistry.ModelId.of("qwen", "1.5b", "bf16"),
                Path.of("/tmp/none"));
        reg.register(OnnxModelRegistry.ModelId.of("llama", "7b", "fp16"),
                Path.of("/tmp/none"));
        assertThat(reg.size()).isEqualTo(3);
        assertThat(reg.registeredKeys()).hasSize(3);
    }
}
