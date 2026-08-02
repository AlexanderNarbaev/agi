package matrix.io;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Реестр модулей сигналов (DESIGN-06 §3). Явная регистрация из кода —
 * никакого ServiceLoader/classpath-сканирования: native-image GraalVM не
 * гарантирует детерминированный обход, а JMM требует безопасной публикации.
 *
 * Инварианты:
 *  - R1: пара (id, version) уникальна; повторная регистрация — ошибка сборки.
 *  - R2: resolve всегда детерминирован: точная версия, иначе max semver.
 *  - R3: после freeze() реестр иммутабелен (публикация через final-обёртку).
 */
public final class ModuleRegistry {
    private final Map<String, SignalModule> modules = new LinkedHashMap<>();
    private volatile boolean frozen;

    public synchronized void register(SignalModule m) {
        if (frozen) throw new IllegalStateException("реестр заморожен");
        String key = m.id() + "@" + m.version();
        if (modules.containsKey(key))
            throw new IllegalStateException("дубликат модуля: " + key);
        modules.put(key, m);
    }

    public Optional<SignalModule> resolve(String id, String version) {
        if (version != null) return Optional.ofNullable(modules.get(id + "@" + version));
        return modules.values().stream()
                .filter(m -> m.id().equals(id))
                .max(Comparator.comparing(SignalModule::version));
    }

    public List<SignalModule> list(SignalModule.Direction d) {
        List<SignalModule> out = new ArrayList<>();
        for (SignalModule m : modules.values()) if (m.direction() == d) out.add(m);
        return List.copyOf(out);
    }

    public void freeze() { frozen = true; }
    public boolean isFrozen() { return frozen; }
}
