package io.matrix.signals;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * RUN 151 — SignalModule registry (perception inputs).
 *
 * <p>Maps signal-type names to producers. The cognitive loop
 * (Phase β) pulls signals from this registry each cycle:
 * <pre>
 *   text → TextSignalModule  → boolean vector
 *   audio-stub → AudioSignalModule → boolean vector
 *   video-stub → VideoSignalModule → boolean vector
 * </pre>
 *
 * <p>The output of every signal is a deterministic boolean vector.
 * Production code adds new signal types as needed (Phase γ
 * federation digests will plug in here).
 */
public final class SignalRegistry {

    public interface SignalModule {
        /** Unique name of this signal type, e.g. "text", "audio", "video". */
        String name();

        /** Produce a boolean vector for the given raw input. */
        boolean[] emit(String raw);
    }

    private final Map<String, SignalModule> modules = new HashMap<>();

    public SignalRegistry register(SignalModule module) {
        modules.put(module.name(), module);
        return this;
    }

    public Optional<SignalModule> get(String name) {
        return Optional.ofNullable(modules.get(name));
    }

    public boolean has(String name) {
        return modules.containsKey(name);
    }

    public int size() {
        return modules.size();
    }

    public Iterable<String> names() {
        return modules.keySet();
    }
}
