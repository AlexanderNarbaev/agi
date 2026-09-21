package io.matrix.workspace;

import java.util.HashMap;
import java.util.Map;

/**
 * RUN 219 — Workspace (working memory store).
 *
 * <p>Simple key-value store for in-process state. Used by brain
 * components to share state without mediator overhead.
 *
 * <p>Replaces ContextVariable / Globals. Pure Java, deterministic.
 */
public final class Workspace {

    private final Map<String, Object> store = new HashMap<>();

    public synchronized void put(String key, Object value) {
        store.put(key, value);
    }

    public synchronized Object get(String key) {
        return store.get(key);
    }

    @SuppressWarnings("unchecked")
    public synchronized <T> T get(String key, Class<T> type) {
        Object v = store.get(key);
        if (v == null) return null;
        if (type.isInstance(v)) return (T) v;
        return null;
    }

    public synchronized boolean contains(String key) {
        return store.containsKey(key);
    }

    public synchronized int size() { return store.size(); }

    public synchronized void clear() { store.clear(); }
}
