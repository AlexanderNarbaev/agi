package io.matrix.reflex;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 217 — ReflexEngine (fast-path response).
 *
 * <p>Reflexes are pre-defined stimulus→response mappings. They
 * bypass the cognitive loop for known inputs (e.g., greetings,
 * shutdown commands). Per SPEC, reflexes are an optimization.
 *
 * <p>Reflexes are intentional and not adversarial: the matrix
 * can't run in adversarial conditions, only respond quickly
 * to known patterns.
 */
public final class ReflexEngine {

    public record Reflex(String pattern, String response) {}

    private final List<Reflex> reflexes = new ArrayList<>();

    public Reflex register(String pattern, String response) {
        Reflex r = new Reflex(pattern, response);
        reflexes.add(r);
        return r;
    }

    /** Try to match input against any reflex (returns null if no match). */
    public String tryReflex(String input) {
        if (input == null) return null;
        for (Reflex r : reflexes) {
            if (input.contains(r.pattern())) {
                return r.response();
            }
        }
        return null;
    }

    public int reflexCount() { return reflexes.size(); }
}
