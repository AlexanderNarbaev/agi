package io.matrix.signals;

import io.matrix.perception.TextEncoder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 151 — SignalRegistry unit tests. */
class SignalRegistryTest {

    static class StubModule implements SignalRegistry.SignalModule {
        private final String name;
        StubModule(String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public boolean[] emit(String raw) {
            // simple deterministic stub
            boolean[] out = new boolean[8];
            for (int i = 0; i < raw.length() && i < 8; i++) {
                out[i] = raw.charAt(i) % 2 == 0;
            }
            return out;
        }
    }

    @Test
    void registerAndLookup() {
        SignalRegistry reg = new SignalRegistry();
        reg.register(new StubModule("text"));
        reg.register(new StubModule("audio"));
        assertThat(reg.size()).isEqualTo(2);
        assertThat(reg.has("text")).isTrue();
        assertThat(reg.has("audio")).isTrue();
        assertThat(reg.has("video")).isFalse();
    }

    @Test
    void getReturnsModule() {
        SignalRegistry reg = new SignalRegistry();
        reg.register(new StubModule("text"));
        var m = reg.get("text");
        assertThat(m).isPresent();
        assertThat(m.get().name()).isEqualTo("text");
    }

    @Test
    void unknownSignalReturnsEmpty() {
        SignalRegistry reg = new SignalRegistry();
        assertThat(reg.get("missing")).isEmpty();
    }

    @Test
    void namesListIncludesRegistered() {
        SignalRegistry reg = new SignalRegistry();
        reg.register(new StubModule("a"));
        reg.register(new StubModule("b"));
        reg.register(new StubModule("c"));
        List<String> names = new ArrayList<>();
        reg.names().forEach(names::add);
        assertThat(names).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void textModuleEmits256Bits() {
        SignalRegistry reg = new SignalRegistry();
        reg.register(new StubModule("text") {
            @Override public boolean[] emit(String raw) {
                return new TextEncoder().encode(raw);
            }
        });
        boolean[] bits = reg.get("text").get().emit("hello world");
        assertThat(bits).hasSize(256);
    }

    @Test
    void replaceModule() {
        SignalRegistry reg = new SignalRegistry();
        reg.register(new StubModule("text"));
        reg.register(new StubModule("text")); // replace
        assertThat(reg.size()).isEqualTo(1);
        assertThat(reg.has("text")).isTrue();
    }
}
