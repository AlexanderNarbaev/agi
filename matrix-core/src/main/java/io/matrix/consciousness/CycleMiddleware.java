package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 287 — CycleMiddleware (middleware chain).
 *
 * <p>Chains multiple processing steps before the main cycle.
 * Each middleware can transform the input or block it.
 */
public final class CycleMiddleware {

    public interface Middleware {
        /** Returns transformed input, or null to block. */
        String apply(String input);
    }

    private final List<Middleware> middlewares = new ArrayList<>();

    public CycleMiddleware add(Middleware m) {
        middlewares.add(m);
        return this;
    }

    /** Apply all middlewares in order. Returns null if any blocks. */
    public String process(String input) {
        String current = input;
        for (Middleware m : middlewares) {
            current = m.apply(current);
            if (current == null) return null;
        }
        return current;
    }

    public int size() { return middlewares.size(); }

    /** Pre-built: trim + normalize + validate. */
    public static CycleMiddleware standard() {
        return new CycleMiddleware()
                .add(s -> s == null ? null : s.strip())
                .add(s -> s.isEmpty() ? null : s)
                .add(s -> s.replaceAll("\\s+", " "));
    }
}
