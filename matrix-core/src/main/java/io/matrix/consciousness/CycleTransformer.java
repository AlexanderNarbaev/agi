package io.matrix.consciousness;

import java.util.function.UnaryOperator;

/**
 * RUN 281 — CycleTransformer (input transformation).
 *
 * <p>Applies a transformation function to each input before
 * it enters the cognitive loop.
 */
public final class CycleTransformer {

    private final UnaryOperator<String> transform;

    public CycleTransformer(UnaryOperator<String> transform) {
        this.transform = transform;
    }

    public String apply(String input) {
        if (input == null) return null;
        return transform.apply(input);
    }

    /** Identity transformer (no-op). */
    public static CycleTransformer identity() {
        return new CycleTransformer(s -> s);
    }

    /** Lowercase transformer. */
    public static CycleTransformer lowercase() {
        return new CycleTransformer(String::toLowerCase);
    }

    /** Strip leading/trailing whitespace. */
    public static CycleTransformer strip() {
        return new CycleTransformer(String::strip);
    }

    /** Truncate to max length. */
    public static CycleTransformer maxLength(int max) {
        return new CycleTransformer(s -> s.length() <= max ? s : s.substring(0, max));
    }
}
